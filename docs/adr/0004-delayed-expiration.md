# ADR-0004: Delayed Reservation Expiration

## Status

Accepted

## Context

Reservas pendentes não podem bloquear inventário indefinidamente.

## Decision

Persistir `expiresAt` e agendar uma mensagem para execução posterior.

A mensagem não é fonte da verdade.

O consumer deve executar uma transição condicional:

```text
PENDING -> EXPIRED
```

somente se a reserva ainda estiver elegível.

## Consequences

### Positive

- expiração assíncrona;
- reduz polling contínuo;
- suporta processamento idempotente.

### Negative

- delayed delivery depende da infraestrutura;
- mensagens podem atrasar;
- ainda será necessário mecanismo de reconciliação para falhas extremas.
