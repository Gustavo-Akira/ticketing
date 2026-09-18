# ADR-0001: Feature-Oriented Architecture

## Status

Accepted

## Context

O projeto precisa evoluir de forma incremental e manter regras de domínio próximas às features responsáveis por elas.

Uma estrutura global baseada apenas em:

```text
controller/
service/
repository/
```

tende a espalhar uma mesma feature por todo o projeto.

## Decision

Organizar o código por feature:

```text
event/
reservation/
payment/
outbox/
```

Cada feature pode possuir:

```text
domain/
application/
port/
infrastructure/
presentation/
```

## Consequences

### Positive

- maior coesão;
- evolução incremental;
- separação clara das fronteiras;
- facilita futura extração de serviços.

### Negative

- pode existir alguma duplicação entre features;
- exige disciplina para não criar abstrações globais prematuras.
