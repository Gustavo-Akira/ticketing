# Domain Model

## User e Role

Representam uma identidade que pode comprar ingressos, organizar eventos ou
administrar o sistema, conforme as roles explicitamente atribuídas.

```text
User
- id
- name
- email
- roles
- createdAt
- updatedAt
```

`Role` admite `CUSTOMER`, `ORGANIZER` e `ADMIN`. Um usuário possui pelo menos uma
role e pode acumular várias; não há hierarquia implícita. O conjunto exposto pelo
domínio é imutável.

Nome é obrigatório, com até 255 caracteres. E-mail é obrigatório, com até 254
caracteres após normalização: remover espaços externos e converter para minúsculas
com `Locale.ROOT`. O formato básico exige uma parte local e um domínio separados
por um único `@`, sem espaços; não há validação DNS. A unicidade é garantida no banco.

IDs seguem o UUID temporal usado por Event. Datas são geradas pelo banco na
inserção, e a reconstituição preserva ID e auditoria. O domínio exige ao menos uma
role; a tabela de associações garante valores válidos, ausência de duplicação e
referência a um usuário existente. Ela não impõe cardinalidade mínima por trigger.

Esta base não define credenciais, cadastro público nem operações de alteração de
perfil/roles. Autenticação e autorização serão implementadas em outro PR.

---

## Event

Representa um evento comercializável.

```text
Event
- id
- name
- startsAt
- status
- createdAt
- updatedAt
```

Estados sugeridos:

```text
DRAFT
AVAILABLE
SALES_CLOSED
FINISHED
CANCELLED
```

---

## Seat

Representa um assento vendável dentro de um evento.

```text
Seat
- id
- eventId
- section
- row
- number
- price
- currency
- status
- version
```

Estados:

```text
AVAILABLE
RESERVED
SOLD
```

Fluxo:

```text
AVAILABLE
   |
   | reservation
   v
RESERVED
   |
   | payment confirmed
   v
SOLD
```

Expiração:

```text
RESERVED
   |
   | reservation expired
   v
AVAILABLE
```

---

## Reservation

Representa uma intenção temporária de aquisição.

```text
Reservation
- id
- eventId
- userId
- status
- createdAt
- expiresAt
- paymentProcessingUntil
```

Estados:

```text
PENDING
PAYMENT_PROCESSING
CONFIRMED
EXPIRED
CANCELLED
```

Transições:

```text
PENDING
  ├── PAYMENT_PROCESSING
  ├── EXPIRED
  └── CANCELLED

PAYMENT_PROCESSING
  ├── CONFIRMED
  ├── PENDING
  └── CANCELLED
```

---

## ReservationSeat

Relaciona a reserva aos assentos.

```text
ReservationSeat
- reservationId
- seatId
```

Uma reserva só pode conter assentos de um único evento.

---

## Payment

Representa o pagamento lógico da reserva.

```text
Payment
- id
- reservationId
- amount
- status
- idempotencyKey
- createdAt
```

Estados:

```text
CREATED
PROCESSING
CAPTURED
DECLINED
UNKNOWN
FAILED
```

`UNKNOWN` é usado quando não existe evidência suficiente para afirmar sucesso ou falha.

Exemplo:

```text
request enviada
    |
provider processa
    |
resposta se perde
    |
timeout
    |
UNKNOWN
```

---

## PaymentAttempt

Representa uma tentativa específica de comunicação com um fornecedor.

```text
PaymentAttempt
- id
- paymentId
- attemptNumber
- provider
- providerReference
- status
- fencingToken
- createdAt
```

Separar `Payment` de `PaymentAttempt` permite:

- múltiplas tentativas;
- reconciliação;
- troca futura de fornecedor;
- auditoria;
- análise de falhas.

---

## OutboxEvent

Evento persistido na mesma transação da alteração de domínio.

```text
OutboxEvent
- id
- aggregateType
- aggregateId
- eventType
- payload
- createdAt
- publishedAt
- attempts
```

Exemplos:

```text
ReservationExpirationScheduled
PaymentRequested
ReservationConfirmed
ReservationExpired
```
