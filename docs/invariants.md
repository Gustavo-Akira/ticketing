# Domain Invariants

Este documento contém regras que devem permanecer verdadeiras independentemente da implementação.

## Reservation

### INV-001 — Reserva all-or-nothing

Uma reserva deve conter todos os assentos solicitados ou nenhum.

Não pode existir:

```text
requested = [A15, A16, A17]

reserved = [A15, A16]
failed = [A17]
```

A transação inteira deve falhar.

---

### INV-002 — Um único evento por reserva

Todos os assentos de uma reserva pertencem ao mesmo `Event`.

---

### INV-003 — Reserva pendente possui expiração

Toda `Reservation` em `PENDING` deve possuir `expiresAt`.

---

### INV-004 — Reserva confirmada não expira

`CONFIRMED` não pode transicionar para `EXPIRED`.

---

## Seat

### INV-005 — Um assento não pode ser vendido duas vezes

Nunca podem existir duas reservas confirmadas para o mesmo assento.

Objetivo:

```text
overselling = 0
```

---

### INV-006 — Reserva só adquire assentos disponíveis

A transição válida é:

```text
AVAILABLE -> RESERVED
```

`SOLD` e `RESERVED` não podem ser reservados novamente.

---

### INV-007 — Expiração libera os assentos

Quando uma reserva `PENDING` expira, seus assentos devem retornar para `AVAILABLE`.

---

## Payment

### INV-008 — Timeout não significa falha

Timeout ou perda de resposta externa deve gerar:

```text
UNKNOWN
```

e nunca automaticamente:

```text
FAILED
```

---

### INV-009 — Idempotência de pagamento

Para:

```text
same reservation
+
same idempotency key
```

o sistema deve retornar o mesmo pagamento lógico.

---

### INV-010 — Expiração não compete com pagamento sem coordenação

Apenas uma das transições pode vencer:

```text
PENDING -> EXPIRED
```

ou:

```text
PENDING -> PAYMENT_PROCESSING
```

Ambas devem ser protegidas por atualização condicional.

---

## Worker coordination

### INV-011 — Worker obsoleto não sobrescreve worker atual

Quando lease expira e outro worker assume:

```text
Worker A token 41
Worker B token 42
```

o token 41 não pode persistir mudanças que invalidem estado produzido pelo token 42.

---

## Messaging

### INV-012 — Consumers devem ser idempotentes

A mesma mensagem pode ser processada várias vezes.

Exemplo:

```text
ReservationExpired
ReservationExpired
ReservationExpired
```

O resultado deve permanecer equivalente a uma única execução.
