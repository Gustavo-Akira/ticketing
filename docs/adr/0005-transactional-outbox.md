# ADR-0005: Transactional Outbox

## Status

Accepted

## Context

Existe uma falha entre:

```text
DB COMMIT
```

e:

```text
publish message
```

Se a aplicação morrer nesse intervalo, o estado é persistido mas o evento pode ser perdido.

## Decision

Persistir eventos em uma tabela de outbox na mesma transação da alteração de domínio.

```text
BEGIN

domain changes
outbox insert

COMMIT
```

Um relay separado publica os eventos posteriormente.

## Consequences

### Positive

- evita dual-write inconsistency;
- publicação pode ser retomada;
- eventos ficam auditáveis.

### Negative

- entrega será pelo menos uma vez;
- consumers devem ser idempotentes;
- exige limpeza e observabilidade da outbox.
