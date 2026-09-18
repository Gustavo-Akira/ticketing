# ADR-0002: Atomic Seat Reservation

## Status

Accepted

## Context

O padrão:

```text
SELECT seat
if available
    UPDATE seat
```

possui race condition sob concorrência.

## Decision

Reservar assentos usando operação condicional no banco.

```sql
UPDATE seats
SET status = 'RESERVED',
    version = version + 1
WHERE event_id = :eventId
  AND id IN (:seatIds)
  AND status = 'AVAILABLE';
```

A operação só tem sucesso se:

```text
updatedRows == requestedSeats
```

Caso contrário, a transação é revertida.

## Consequences

### Positive

- reduz janela de corrida;
- banco atua como coordenador inicial;
- simples de testar;
- evita lock distribuído prematuro.

### Negative

- alta contenção pode aumentar lock wait;
- hot seats podem concentrar carga no banco;
- solução pode precisar evoluir em volumes maiores.
