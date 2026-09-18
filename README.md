# Ticketing Core

Primeira entrega do roadmap: persistência de eventos e assentos em um monólito
Spring Boot, organizado por feature conforme ADR-0001.

## Executar

Requisitos: Docker com Compose. A partir desta pasta:

```sh
docker compose up --build -d
docker compose logs -f app
docker compose down
```

A aplicação usa a porta 8080 e o PostgreSQL a 5432, ambas limitadas ao localhost.
O volume `postgres-data` preserva os dados. As credenciais padrão são apenas para
desenvolvimento local; `DB_PASSWORD` permite alterá-las antes de criar o banco.
Para executar fora do Compose, use JDK 25, inicie `docker compose up -d postgres`
e execute `./gradlew bootRun` (`gradlew.bat bootRun` no Windows).
Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` conforme o ambiente.

Esta etapa entrega persistência. Não há endpoints de eventos nem operações de
reserva/pagamento ainda. Spring Security mantém a configuração padrão do bootstrap.

## Modelo

- `event/domain`: Event, Seat e seus estados; validações na criação.
- `event/infrastructure`: repositories JPA, incluindo consulta de assentos por evento.
- `db/migration/V1__create_events_and_seats.sql`: schema gerenciado por Flyway.

Eventos começam em `DRAFT`; assentos começam em `AVAILABLE`. Um assento pertence a
um evento existente, e sua combinação de setor, fila e número é única nesse evento.
Preços usam `NUMERIC(12,2)` e nunca são arredondados silenciosamente pelo domínio.
`Seat.version` é gerenciada por `@Version` (zero após persistir). O schema aceita
os estados documentados; as transições serão implementadas com os respectivos casos
de uso. `@Version` não substitui o UPDATE condicional planejado para reservas.

Hibernate apenas valida o schema. Migrations criam as tabelas e suas restrições.
Kafka não é necessário nesta fase.

## Testes e cobertura

Com JDK 25 e Docker disponíveis:

```sh
./gradlew clean check
```

No Windows: `gradlew.bat clean check`. A suíte usa PostgreSQL real via Testcontainers;
Docker indisponível causa falha, sem ignorar testes de integração.

JaCoCo exige **80% de linhas e 80% de branches** no conjunto completo de classes de
produção, sem exclusões. `check` depende de `jacocoTestCoverageVerification` e
falha abaixo de qualquer limite. `test` gera os relatórios; execute `check` para
aplicar obrigatoriamente o gate.

- Testes: `build/reports/tests/test/index.html`
- Cobertura: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`

O workflow `Core CI` executa o mesmo comando em pushes e pull requests, inclusive
drafts, e publica os relatórios como artifacts. Para impedir merge quando o gate
falha, configure `Tests and coverage (80%)` como check obrigatório nas regras de
proteção da branch `main` no GitHub.

O diretório local `core` é a raiz deste repositório Git: `.github/workflows/ci.yml`
fica na raiz esperada pelo GitHub. O plano da entrega está em
`docs/plans/event-seat.md`; os documentos de referência do projeto estão em `docs/`.
