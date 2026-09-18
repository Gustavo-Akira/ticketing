# Testing Strategy

## Objetivo

Testes não servem apenas para validar código.

Neste projeto, testes são parte do processo de System Design:

```text
design
  ↓
prediction
  ↓
experiment
  ↓
measurement
  ↓
redesign
```

## 1. Unit tests

Cobrir:

- transições válidas;
- transições inválidas;
- expiração;
- pagamento;
- idempotência de regras de domínio.

Exemplo:

```text
PENDING -> PAYMENT_PROCESSING
```

válido.

```text
EXPIRED -> PAYMENT_PROCESSING
```

inválido.

## 2. Integration tests

Usar PostgreSQL real via Testcontainers.

Cobrir:

- migrations;
- atomic conditional update;
- rollback all-or-nothing;
- outbox;
- payment idempotency.

## 3. Concurrency tests

### Hot seat

```text
100 threads
seat = A15
```

Esperado:

```text
success = 1
conflict = 99
overselling = 0
```

### Multi-seat reservation

```text
100 threads
seats = [A15, A16, A17]
```

Esperado:

```text
complete reservations = 1
partial reservations = 0
```

## 4. Load tests

Ferramenta inicial:

```text
k6
```

Cenários:

```text
/load-tests
  normal-traffic.js
  flash-sale.js
  hot-seat.js
  bot-traffic.js
  retry-storm.js
```

## 5. Failure injection

Falhas planejadas:

- aplicação morre depois do COMMIT e antes de publicar;
- provider demora 10s;
- provider retorna 30% timeout;
- mensagem duplicada;
- worker perde lease;
- worker antigo tenta escrever;
- banco reinicia;
- fila acumula mensagens.

## 6. SLOs iniciais

Metas de laboratório:

```text
GET /events
p99 < 150ms

GET /events/{id}/seats
p99 < 250ms

POST /reservations
p99 < 500ms

overselling = 0
```

Esses valores são metas de experimento e podem mudar conforme o ambiente.

## 7. Métricas

Coletar:

- p50;
- p95;
- p99;
- throughput;
- error rate;
- active DB connections;
- lock wait time;
- deadlocks;
- queue depth;
- consumer lag;
- retries;
- reservation conflicts;
- overselling.
