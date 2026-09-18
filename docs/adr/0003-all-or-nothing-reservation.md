# ADR-0003: All-or-Nothing Reservation

## Status

Accepted

## Context

Uma solicitação pode conter vários assentos e parte deles pode já estar indisponível.

## Decision

A reserva é atômica do ponto de vista do usuário.

Exemplo:

```text
requested:
A15
A16
A17

A17 unavailable
```

Resultado:

```text
reservation failed
A15 not reserved
A16 not reserved
```

## Consequences

### Positive

- sem reservas parciais;
- regras mais simples;
- experiência previsível;
- rollback claro.

### Negative

- usuário pode perder assentos disponíveis quando apenas um item conflita;
- menor aproveitamento de inventário em alguns cenários.
