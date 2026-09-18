# ADR-0006: Payment Idempotency and UNKNOWN State

## Status

Accepted

## Context

Integrações de pagamento possuem falhas ambíguas.

Exemplo:

```text
request sent
provider captures
response lost
client sees timeout
```

Timeout não prova falha.

Retries cegos podem cobrar duas vezes.

## Decision

### Idempotency

Cada request de pagamento recebe uma idempotency key.

```text
UNIQUE(reservation_id, idempotency_key)
```

A mesma combinação representa o mesmo pagamento lógico.

### UNKNOWN state

Quando não é possível determinar o resultado externo:

```text
Payment.status = UNKNOWN
```

Nunca converter timeout diretamente em `FAILED`.

### Reconciliation

Quando o fornecedor permitir, consultar o estado usando:

```text
providerReference
```

ou:

```text
merchantReference / paymentId
```

antes de decidir retry.

### Worker coordination

Lease e fencing token serão usados para coordenação interna.

Eles não substituem idempotência fornecida pelo provider.

## Consequences

### Positive

- reduz duplicação de cobrança;
- representa corretamente incerteza distribuída;
- permite reconciliação segura.

### Negative

- fluxo possui mais estados;
- alguns pagamentos podem permanecer UNKNOWN temporariamente;
- garantias finais dependem das capacidades do fornecedor.
