# Architecture

## 1. Objetivo arquitetural

Construir um sistema de venda de ingressos capaz de evoluir de um monólito transacional para uma arquitetura distribuída orientada a eventos, sem introduzir complexidade antes de haver necessidade comprovada.

A arquitetura deve permitir estudar:

- concorrência;
- consistência;
- idempotência;
- filas;
- backpressure;
- retry;
- DLQ;
- outbox;
- lease;
- fencing token;
- reconciliação;
- observabilidade;
- load shedding;
- multi-region em fases posteriores.

## 2. Arquitetura inicial

A primeira versão será um monólito modular.

```text
Client
  |
  v
Spring Boot API
  |
  +--> Event
  +--> Identity
  +--> Reservation
  +--> Payment
  +--> Outbox
  |
  v
PostgreSQL
```

Motivos:

- reduz custo operacional;
- facilita transações locais;
- permite provar invariantes antes de distribuir;
- simplifica testes concorrentes;
- evita microservices prematuros.

## 3. Organização por feature

```text
identity/
  domain/
  application/
  port/
  infrastructure/persistence/
  infrastructure/security/
  presentation/
  presentation/cli/

event/
  domain/
  application/
  infrastructure/
  presentation/

reservation/
  domain/
  application/
  port/
  infrastructure/
  presentation/

payment/
  domain/
  application/
  port/
  infrastructure/
  presentation/

outbox/
shared/
```

Cada feature contém suas responsabilidades.

`identity` possui domínio independente de Spring/JPA e uma porta de repositório
implementada por um adaptador JPA. O adaptador delimita as transações de gravação
e leitura, incluindo a conversão das roles para um snapshot imutável. Casos de uso
transacionais coordenam cadastro, credenciais, login, refresh e concessão de ORGANIZER.
Adaptadores JDBC armazenam credenciais/sessões e fazem concessões aditivas; participam
da mesma transação de banco usada pelo JPA. O domínio não depende desses adaptadores.

JWT RS256 é emitido por uma porta de aplicação e validado pelo Resource Server do
Spring Security. A matriz de rotas distingue autenticação e roles independentes.
Refresh opaco usa hash SHA-256; renovação/logout bloqueiam a sessão com FOR UPDATE.
O token é relido após o bloqueio para detectar consumo concorrente. Reutilização
retorna resultado rejeitado, confirmando revogação antes da resposta HTTP 401.

A entrada CLI `identity create-admin` inicia contexto sem HTTP e sem beans JWT,
utiliza o mesmo caso de criação de contas e encerra após executar. Propriedade
de eventos não é avaliada nesta entrega.

Não teremos diretórios globais gigantes de `controllers`, `services` e `repositories`.

## 4. Fluxo de reserva

```text
POST /events/{eventId}/reservations
           |
           v
CreateReservationUseCase
           |
           v
atomic UPDATE seats
           |
           +--> updated != requested -> rollback
           |
           v
insert reservation
           |
           v
insert reservation_seats
           |
           v
insert outbox event
           |
           v
COMMIT
```

A operação inteira deve ser transacional.

## 5. Fluxo de expiração

```text
Reservation created
      |
      v
OutboxEvent
      |
      v
Outbox Relay
      |
      v
Delayed Queue
      |
      v
Expiration Consumer
      |
      v
atomic PENDING -> EXPIRED
      |
      v
release seats
```

A fila não é fonte da verdade.

O consumer precisa validar o estado atual no banco.

## 6. Fluxo de pagamento

```text
Client
  |
  | Idempotency-Key
  v
Payment API
  |
  v
Payment CREATED
  |
  v
Outbox PaymentRequested
  |
  v
Payment Worker
  |
  +--> acquire lease
  +--> obtain fencing token
  +--> PENDING -> PAYMENT_PROCESSING
  |
  v
Payment Provider
  |
  +--> SUCCESS
  +--> DECLINED
  +--> UNKNOWN
```

`UNKNOWN` não encerra o fluxo.

```text
UNKNOWN
   |
   v
Reconciliation Worker
   |
   v
provider.getStatus(...)
```

## 7. Limite de responsabilidade

### Controlamos internamente

- idempotência de request;
- consistência entre banco e evento via outbox;
- concorrência entre workers;
- fencing token interno;
- transições de estado;
- deduplicação de mensagens;
- expiração de reservas.

### Não controlamos completamente

- retenção da idempotency key do fornecedor;
- timeout sem resposta;
- duplicate processing externo;
- atraso de webhook;
- inconsistência temporária no provider;
- outage do provider.

Essas diferenças devem aparecer explicitamente no design.

## 8. Evoluções futuras

Possíveis componentes:

```text
API Gateway
Rate Limiter
Waiting Room
Redis
Kafka
Dedicated delayed queue
Payment workers
Reservation workers
Prometheus
Grafana
OpenTelemetry
Kubernetes
Multi-region
```

Nenhum deles é requisito da V1.
