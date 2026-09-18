# Event/Seat persistence

## Scope and design

Implements PR 1 from the project roadmap and ADR-0001 (feature-oriented architecture).
All deliverables live in core, which is the Git repository root.
Use Java 25, Spring Boot, JPA, Flyway and PostgreSQL. Event and Seat belong to
`event/domain`; Spring Data repositories belong to `event/infrastructure`.
No reservation, payment or HTTP API is introduced in this milestone.

Event has UUID id, name, Instant startsAt and status (DRAFT by default).
Seat has UUID id/eventId, section, row, number, decimal price, AVAILABLE status
and optimistic-lock version. Identity and creation fields have no public setters.
Database constraints enforce required values, nonnegative price, allowed statuses,
event foreign key and unique (event, section, row, number).

## Execution

- [x] Write domain tests for defaults, invalid fields and monetary boundaries;
  run `./gradlew test --tests '*DomainTest'` before implementation.
- [x] Implement entities/enums and rerun domain tests.
- [x] Write PostgreSQL integration tests for round trips, event-scoped queries,
  migrations, uniqueness, foreign keys and check constraints.
- [x] Add repositories, Flyway migration and datasource configuration;
  run `./gradlew test` with Docker available.
- [x] Add Dockerfile and Compose for app/database startup.
- [x] Add JaCoCo 0.8.14, XML/HTML reports and minimum 80% LINE/BRANCH gates
  across all production classes; wire verification into `check`.
- [x] Add GitHub Actions for PRs and pushes, Java 25, Gradle check and reports.
- [x] Run clean check, prove the gate rejects insufficient coverage,
  verify Compose startup and review changes. Publish the branch as a draft PR.

## Acceptance

### Follow-up da revisão

- Novos IDs Event/Seat usam UUIDv7; UUIDs preexistentes permanecem válidos.
- A V2 adiciona timestamps de Event e moeda
  obrigatória do Seat. Backfill do draft: timestamps da migration e BRL.
- `currency` é código ISO 4217 validado no domínio; o banco exige formato AAA.
- O índice existente iniciado em event_id é validado com EXPLAIN.
- Testes cobrem round trip, update SQL, upgrade V1 com dados e moeda ausente/inválida.
- A V3 remove a manutenção de timestamps por trigger e a constraint temporal.
  Inserts usam defaults; updates atribuem updated_at explicitamente nas queries,
  sem requisito de monotonicidade.

PostgreSQL schema comes exclusively from migrations (`ddl-auto=validate`).
Testcontainers tests must fail if Docker is unavailable, never silently skip.
Coverage includes all production code, with no blanket entity/bootstrap exclusions.
CI runs the same `check` task used locally. A failed gate fails the workflow;
requiring that check for merges additionally depends on repository protection rules.
