# Domain Persistence Separation Implementation Plan

> Execute com superpowers:executing-plans, tarefa por tarefa.

**Goal:** Resolver a issue #6 separando domínio de entidades JPA.
**Architecture:** Portas locais à feature e adaptadores JPA com mapping explícito.
**Tech Stack:** Java 25, Spring Boot, Hibernate, PostgreSQL, JUnit, Testcontainers.
**Spec:** `docs/superpowers/specs/2026-09-20-domain-persistence-design.md`

## Restrições

- Branch `refactor/issue-6-domain-persistence`; não alterar a main.
- Preservar APIs, schema, auditoria, locks, UUIDs, validações e versão otimista.
- Domínio e portas sem JPA, Hibernate ou Spring Data.
- Cobertura mínima existente: 80% de linhas e branches, sem exclusões.

## Tarefa 1 — Domínio, portas e adaptadores

- [x] Verificar baseline `./gradlew.bat check` com JDK 25 e Docker.
- [x] Adicionar `event/domain/DomainIsolationTest.java`; verificar falha pelas anotações atuais.
- [x] Adicionar teste em `event/infrastructure/EventPersistenceTest.java` que altera um
  domínio lido, faz flush e confirma que nenhuma escrita ocorreu sem `save`.
- [x] Criar `Event.restore(...)` e `Seat.restore(...)` preservando todos os campos.
- [x] Criar `event/port/{EventRepository,SeatRepository,PageResult}.java`.
- [x] Criar `event/infrastructure/persistence/{EventJpaEntity,SeatJpaEntity,
  SpringDataEventRepository,SpringDataSeatRepository,JpaEventRepository,JpaSeatRepository}.java`.
- [x] Migrar imports e chamadas em `event/application/*UseCase.java` e testes.
- [x] Mover paginação Spring Data para adaptadores, com ordenação por ID.
- [x] Executar testes de domínio e persistência.

## Tarefa 2 — Regressões e documentação

- [x] Testar duas leituras do mesmo assento e rejeitar escrita com versão antiga,
  incluindo conflito após limpeza do contexto de persistência.
- [x] Verificar round-trip de auditoria, status, IDs e versão nos testes existentes.
- [x] Atualizar README com a separação e as responsabilidades dos adaptadores.
- [x] Executar `./gradlew.bat clean check`, verificar relatórios e cobertura.
- [x] Revisar diff e status final; reportar resultado na branch separada.

## Resultado da validação

Baseline: 123 testes existentes selecionados passaram; os dois testes novos de snapshots falharam antes da implementação. DomainIsolationTest falhou para Event e Seat antes da extração. Após a refatoração, clean check passou com PostgreSQL/Testcontainers e o gate de cobertura de 80%. Revisão independente não encontrou correções necessárias.
