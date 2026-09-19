# Roadmap

## Fase 1 — Núcleo transacional

### PR 1 — Bootstrap + Event/Seat persistence

Entregas:

- projeto Spring Boot;
- PostgreSQL;
- migrations;
- Event;
- Seat;
- repositories;
- testes básicos.

Critérios de aceite:

- aplicação sobe via Docker Compose;
- banco é criado por migration;
- Event e Seat podem ser persistidos;
- assentos possuem `AVAILABLE`, `RESERVED`, `SOLD`.

---

### Próxima entrega — Event use cases e API

Etapa adicionada antes do domínio de reservas:

- criar evento (`POST /events`);
- consultar evento por ID (`GET /events/{id}`);
- listar eventos com paginação (`GET /events`);
- atualizar nome e data (`PUT /events/{id}`);
- validação de entrada, respostas de erro e testes com PostgreSQL real.

Eventos são criados em `DRAFT`. A atualização não altera status.
Publicação e cancelamento de eventos ficam para uma entrega futura, com use cases
próprios e regras de transição. Plano: [Event API](plans/event-api.md).

Os números abaixo identificam as etapas originais do roadmap, não necessariamente
o número atribuído pelo GitHub ao pull request.

---

### PR 2 — Reservation domain

Entregas:

- Reservation;
- ReservationStatus;
- ReservationSeat;
- regras de transição;
- testes unitários.

Critérios de aceite:

- `PENDING` possui `expiresAt`;
- não é possível confirmar reserva em estado inválido;
- reserva expirada não inicia pagamento.

---

### PR 3 — Atomic seat reservation

Entregas:

- operação condicional de reserva;
- transação all-or-nothing;
- tratamento de conflito.

Critérios de aceite:

```text
100 concorrentes
1 seat

expected:
1 success
99 conflicts
0 overselling
```

---

### PR 4 — Reservation API

Endpoint:

```text
POST /events/{eventId}/reservations
```

Critérios:

- validação de input;
- erro de conflito;
- resposta com expiration;
- nenhuma reserva parcial.

---

### PR 5 — Concurrent reservation test

Cenários:

```text
100 users -> A15
```

e:

```text
100 users -> [A15, A16, A17]
```

Critérios:

- somente uma reserva completa;
- nenhuma parcial;
- overselling igual a zero.

---

## Fase 2 — Expiração

### PR 6 — Expiration model
### PR 7 — Transactional outbox
### PR 8 — Expiration worker

Objetivos:

- eliminar reserva presa indefinidamente;
- provar idempotência;
- demonstrar a falha `commit succeeded / message lost` antes do outbox.

---

## Fase 3 — Pagamento

### PR 9 — Payment domain
### PR 10 — Idempotency key
### PR 11 — Fake provider
### PR 12 — Payment processing
### PR 13 — UNKNOWN + reconciliation

Objetivos:

- modelar integração não confiável;
- distinguir decline de timeout;
- simular fornecedor idempotente e não idempotente.

---

## Fase 4 — Coordenação distribuída

### PR 14 — Lease
### PR 15 — Fencing token

Testes:

- worker lento;
- lease expirado;
- outro worker assume;
- worker antigo tenta persistir.

---

## Fase 5 — Escala

### PR 16 — Load tests
### PR 17 — Observability
### PR 18 — Failure injection

Métricas iniciais:

```text
throughput
p50
p95
p99
error rate
DB connections
lock wait
queue depth
consumer lag
retry volume
overselling
```

---

## Fase 6 — Evoluções futuras

Somente após evidência de necessidade:

- Redis;
- Kafka;
- waiting room;
- global/rate limiting;
- bot mitigation;
- hot partition mitigation;
- backpressure;
- DLQ;
- adaptive circuit breaker;
- multi-region;
- home region;
- load shedding.
