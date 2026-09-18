# Domain Model

## Event

Representa um evento comercializável.

```text
Event
- id
- name
- startsAt
- status
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
