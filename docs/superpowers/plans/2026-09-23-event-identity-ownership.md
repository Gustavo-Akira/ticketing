# Event Identity Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Associar eventos ao criador autenticado e bloquear gerenciamento por outros organizadores.

**Architecture:** Event guarda um UUID de proprietário, sem referência Java a User. Controllers extraem o subject do JWT validado; casos de uso transacionais verificam propriedade antes de trabalhar. A FK garante integridade e os mecanismos atuais de concorrência permanecem.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Security Resource Server, JPA, Flyway, PostgreSQL, JUnit 5, Mockito, MockMvc e Testcontainers.

**Spec:** [Especificação aprovada](../specs/2026-09-23-event-identity-ownership-design.md).

## Global Constraints

- Novos eventos exigem UUID não nulo no construtor.
- Não haverá operação de transferência de propriedade.
- Criar `V6__add_event_owner.sql`, preservando V1 a V5.
- Eventos legados permanecem consultáveis, mas qualquer tentativa de gerenciá-los recebe 403.
- ADMIN sozinho continua sem permissão de escrita.
- ADMIN com ORGANIZER também precisa ser o proprietário.
- GETs mantêm acesso a qualquer usuário autenticado, inclusive para DRAFT.
- Preservar atualização condicional de status, bloqueio do lote e versionamento otimista de assentos.
- Executar gradlew.bat check com PostgreSQL via Testcontainers e manter os limites existentes de 80% para linhas e branches, sem exclusões novas.
- Nenhuma dependência nova; não apagar dados locais, alterar migrations antigas ou instalar serviços.

## Review Focus

1. `ownerId: null` no JSON também é tentativa de fornecer propriedade e deve resultar em 400 (tarefa 3).
2. Ator nulo em chamada direta não pode ser igual a proprietário legado nulo (tarefa 1).
3. Assento de outro evento não pode ser alterado informando um evento próprio (tarefa 3).
4. Organizador estranho recebe 403 antes de descobrir conflitos de versão ou estado de assentos (tarefas 2 e 3).
5. Fixtures que apagam usuários após criar eventos devem limpar seats/events antes de users, sem afrouxar a FK (tarefa 3).

## Preparação e mapa de arquivos

Todos os caminhos Java abaixo são relativos a `src/main/java/br/com/gustavoakira/ticketing/core/` (M) ou `src/test/java/br/com/gustavoakira/ticketing/core/` (T). Recursos usam caminhos completos. Executar tarefas sequencialmente: as assinaturas e fixtures são compartilhadas.

- [ ] Ler spec, instruções locais e estado Git. Usar using-git-worktrees no início da execução; preservar mudanças do usuário.
- [ ] Verificar `java -version`, `docker info` e `./gradlew.bat --version`. Usar o wrapper existente e executar `./gradlew.bat check` como baseline. Registrar falhas de ambiente separadamente de falhas de produto.

Arquivos novos: migration V6; política/exceção de propriedade em event/application; testes focados de política, casos de uso, migration e API; fixture de organizador em event/support.
Arquivos modificados: Event, EventJpaEntity, EventResult, cinco casos de uso, dois controllers, dois handlers, EventRequest, testes afetados e documentação.

## Tarefa 1: Modelo persistido e política de propriedade

**Files:**
- Modify M: `event/domain/Event.java`, `event/infrastructure/persistence/EventJpaEntity.java`, `event/application/EventResult.java`.
- Create M: `event/application/EventOwnership.java`, `event/application/EventAccessDeniedException.java`.
- Create: `src/main/resources/db/migration/V6__add_event_owner.sql`.
- Modify T: `event/domain/EventDomainTest.java`, `event/infrastructure/EventPersistenceTest.java`, `event/application/ChangeEventStatusToAvailableUseCaseTest.java`.
- Create T: `event/application/EventOwnershipTest.java`, `event/infrastructure/EventOwnershipMigrationTest.java`.

**Interfaces:**
- Produces `Event(String name, Instant startsAt, UUID ownerId)`.
- Produces `Event.restore(UUID id, String name, Instant startsAt, EventStatus status, Instant createdAt, Instant updatedAt, UUID ownerId)` and `UUID getOwnerId()`.
- Produces `EventOwnership.requireOwner(Event event, UUID actorId): void` and `EventAccessDeniedException extends RuntimeException`.
- Append `UUID ownerId` to EventResult; update `from(Event)`.

- [ ] Acrescentar testes de construção e política, incluindo dono, estranho, ator nulo e legado; usar este núcleo de teste com JUnit/AssertJ e imports usuais:

```java
UUID owner = UUID.randomUUID();
Instant start = Instant.parse("2027-01-10T20:00:00Z");
Event event = new Event("Concert", start, owner);
assertThat(event.getOwnerId()).isEqualTo(owner);
assertThatIllegalArgumentException().isThrownBy(() -> new Event("Concert", start, null));
assertThatCode(() -> EventOwnership.requireOwner(event, owner)).doesNotThrowAnyException();
assertThatThrownBy(() -> EventOwnership.requireOwner(event, UUID.randomUUID()))
    .isInstanceOf(EventAccessDeniedException.class);
Event legacy = Event.restore(UUID.randomUUID(), "Legacy", start, EventStatus.DRAFT, start, start, null);
assertThatThrownBy(() -> EventOwnership.requireOwner(legacy, null))
    .isInstanceOf(EventAccessDeniedException.class);
```

- [ ] Executar `./gradlew.bat test --tests '*EventDomainTest' --tests '*EventOwnershipTest'`; esperar falha por ausência das novas interfaces.
- [ ] Implementar campo obrigatório na construção com `Fields.required(ownerId, "ownerId")`, atribuição nullable apenas em restore, getter e mapeamento/result. Política sem Spring:

```java
public static void requireOwner(Event event, UUID actorId) {
    if (actorId == null || !actorId.equals(event.getOwnerId())) {
        throw new EventAccessDeniedException();
    }
}
// Construtor de EventAccessDeniedException:
// super("Only the event owner can manage this event");
```

- [ ] Adicionar migration e mapear `@Column(name = "owner_id", updatable = false)` como UUID simples:

```sql
ALTER TABLE events ADD COLUMN owner_id UUID;
ALTER TABLE events ADD CONSTRAINT fk_events_owner
    FOREIGN KEY (owner_id) REFERENCES users(id);
CREATE INDEX ix_events_owner_id ON events(owner_id);
```

- [ ] No teste de persistência, inserir usuário antes de salvar Event e verificar round-trip do UUID. Adaptar chamadas de construção/restore existentes, sempre com dono explícito, sem overload legado permissivo. A alteração da criação HTTP pertence à tarefa 2; tarefas 1 e 2 formam a mudança compilável antes do commit.
- [ ] No novo teste de migration, usar container próprio e Flyway `target("5")`, inserir evento e assento com currency, migrar até latest e verificar `owner_id IS NULL`, mesmos IDs e assento preservado. Inserir usuário e evento com dono; tentar owner inexistente e delete do usuário referenciado, ambos com SQLState 23503. SQL de verificação:

```sql
SELECT e.owner_id, s.currency FROM events e JOIN seats s ON s.event_id = e.id;
UPDATE events SET owner_id = '00000000-0000-4000-8000-000000000099';
```

O UPDATE negativo ocorre antes de existir o usuário 99, em statement isolado sem transação abortada compartilhada. Não usar Flyway clean contra banco local.

## Tarefa 2: Aplicação e entrada autenticada

**Files:**
- Modify M: `event/application/CreateEventUseCase.java`, `UpdateEventUseCase.java`, `ChangeEventStatusToAvailableUseCase.java`, `CreateSeatsBatchUseCase.java`, `UpdateSeatUseCase.java` (todos sob event/application).
- Modify M: `event/presentation/EventController.java`, `SeatController.java`, `EventExceptionHandler.java`, `SeatExceptionHandler.java` (todos sob event/presentation).
- Modify T: `event/application/ChangeEventStatusToAvailableUseCaseTest.java`, `event/application/CreateSeatsBatchUseCaseTest.java`.
- Create T: `event/application/UpdateEventUseCaseTest.java`, `event/application/UpdateSeatUseCaseTest.java`, `event/application/CreateEventUseCaseTest.java`.

**Interfaces:**
- Consumes Event e EventOwnership da tarefa 1.
- Produces `CreateEventUseCase.execute(EventDetails details, UUID actorId): EventResult`.
- Produces `UpdateEventUseCase.execute(UUID id, EventDetails details, UUID actorId): EventResult`.
- Produces `ChangeEventStatusToAvailableUseCase.execute(UUID eventId, UUID actorId): void`.
- Produces `CreateSeatsBatchUseCase.execute(UUID eventId, SeatBatchCreationCommand command, UUID actorId): void`.
- Produces `UpdateSeatUseCase.execute(UUID eventId, UUID id, SeatDetails details, Long expectedVersion, UUID actorId): SeatResult`.

- [ ] Escrever testes por caso de uso para dono, estranho, legado e evento ausente. Para escrita negada verificar zero gravações e zero consultas de assentos. Exemplo completo do corpo de teste de publicação, usando mocks `events`, `seats` e `useCase` já existentes:

```java
var event = new Event("Concert", START, UUID.randomUUID());
when(events.findById(event.getId())).thenReturn(Optional.of(event));
assertThatThrownBy(() -> useCase.execute(event.getId(), UUID.randomUUID()))
    .isInstanceOf(EventAccessDeniedException.class);
verify(events).findById(event.getId());
verifyNoMoreInteractions(events);
verifyNoInteractions(seats);
```

- [ ] Executar `./gradlew.bat test --tests '*event.application.*'`; confirmar falha esperada antes de implementar a autorização.
- [ ] Trocar assinaturas sem preservar overloads inseguros. Create constrói Event com actorId. UpdateEvent carrega evento e autoriza antes de updateDetails. Os demais autorizam imediatamente após carregar evento, antes de regras de estado ou consultas de assentos. Conservar o getEventByIdForUpdate do lote e o update condicional da publicação.

```java
var event = events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
EventOwnership.requireOwner(event, actorId);
// Em UpdateEventUseCase, continuar com updateDetails e releitura existentes.
```

- [ ] Nos cinco métodos HTTP de escrita, acrescentar parâmetro `@AuthenticationPrincipal Jwt jwt` (Spring Security), passar `UUID.fromString(jwt.getSubject())` ao caso de uso. Não acrescentar fallback para username, usuário padrão ou corpo da requisição. GETs mantêm as assinaturas atuais.
- [ ] Nos dois handlers acrescentar tratamento específico, sem converter outras falhas em 403:

```java
@ExceptionHandler(EventAccessDeniedException.class)
public ProblemDetail forbidden(EventAccessDeniedException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
}
```

- [ ] Ajustar todas as chamadas diretas de execute/construtor nos testes com IDs explícitos. Executar `./gradlew.bat test --tests '*event.domain.*' --tests '*event.application.*' --tests '*EventPersistenceTest' --tests '*EventOwnershipMigrationTest'`. Esperar sucesso; registrar qualquer falha real.
- [ ] Conferir diff e criar commit local das tarefas 1–2: `feat: enforce event ownership in application use cases`. Incluir somente arquivos destas tarefas.

## Tarefa 3: Contrato HTTP e regressões de integração

**Files:**
- Modify M: `event/presentation/EventRequest.java`.
- Create T: `event/presentation/EventOwnershipApiTest.java`, `event/support/OrganizerFixture.java`.
- Modify T: `event/presentation/EventApiTest.java`, `SeatApiTest.java`, `SeatConcurrencyApiTest.java`, `EventStatusConflictApiTest.java` (todos sob event/presentation), `identity/presentation/IdentityApiTest.java`.

**Interfaces:**
- Consumes assinaturas HTTP/casos de uso das tarefas 1–2.
- Produces fixture `OrganizerFixture.seed(JdbcTemplate jdbc, UUID id): void` e `OrganizerFixture.jwt(UUID id): RequestPostProcessor` para testes existentes; nova API de ownership usa AccessTokenIssuer real.
- Mantém `EventRequest(String name, Instant startsAt)` e `toDetails()`; adicionar creator estático para detectar presença de ownerId sem importar RequestFields de Identity.

- [ ] Escrever testes HTTP de POST/PUT com `ownerId` string e null; esperar 400 e banco inalterado. Usar creator Jackson delegating com nó JSON do Jackson 3 existente (`tools.jackson.databind.JsonNode`) e `com.fasterxml.jackson.annotation.JsonCreator`:

```java
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
public static EventRequest from(JsonNode node) {
    if (node == null || !node.isObject() || node.has("ownerId")) {
        throw new IllegalArgumentException("Event owner cannot be supplied in the request");
    }
    JsonNode name = node.get("name");
    JsonNode startsAt = node.get("startsAt");
    if (name == null || !name.isTextual() || startsAt == null || !startsAt.isTextual()) {
        throw new IllegalArgumentException("name and startsAt must be strings");
    }
    try {
        return new EventRequest(name.asText(), Instant.parse(startsAt.asText()));
    } catch (java.time.format.DateTimeParseException exception) {
        throw new IllegalArgumentException("startsAt must be an ISO-8601 instant", exception);
    }
}
```

Preservar campos extras não relacionados conforme comportamento atual; não ativar rejeição global que afete outros endpoints. Manter testes de data inválida e corpo ausente.

- [ ] Implementar fixture para persistir usuário/role e usar JwtAuthenticationToken simulado, com subject UUID e authority explícita:

```java
// seed(JdbcTemplate jdbc, UUID id):
jdbc.update("insert into users(id,name,email) values (?, ?, ?) on conflict (id) do nothing",
    id, "Organizer", id + "@example.com");
jdbc.update("insert into user_roles(user_id,role) values (?, 'ORGANIZER') on conflict do nothing", id);
// jwt(UUID id):
return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
    .jwt(token -> token.subject(id.toString()))
    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ORGANIZER"));
```

- [ ] Adaptar fixtures de escrita existentes para seed usuário, seed owner_id e JWT correspondente. Manter users simulados nos testes somente de leitura quando não exigirem Jwt principal. Limpar `seats`, `events`, `users` nessa ordem em IdentityApiTest; não remover constraints para facilitar teardown.
- [ ] Criar EventOwnershipApiTest com SpringBootTest, MockMvc/springSecurity e container próprio. Criar contas por CreateAccountUseCase e emitir tokens por AccessTokenIssuer (padrão de IdentityApiTest). Para dono A e estranho B, testar os quatro endpoints de gerenciamento com corpos válidos, snapshots antes/depois de evento e assentos e status esperados 200/204/403. Exemplo de tentativa negada:

```java
var before = jdbc.queryForMap("select * from events where id = ?", eventId);
mvc.perform(put("/events/{id}", eventId)
    .header("Authorization", "Bearer " + otherToken)
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"Changed\",\"startsAt\":\"2027-01-10T20:00:00Z\"}"))
    .andExpect(status().isForbidden())
    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
assertThat(jdbc.queryForMap("select * from events where id = ?", eventId)).isEqualTo(before);
```

- [ ] Cobrir na mesma suíte: ownerId da resposta e do banco igual ao subject real; 401 anônimo; 403 CUSTOMER, ADMIN e estranho ADMIN+ORGANIZER; 404 para evento ausente com ORGANIZER; 403 para legado; GET autenticado de DRAFT/legado; publicação estranha sem assentos retorna 403; edição estranha com versão desatualizada retorna 403; assento de B sob eventId de A retorna 404 sem alteração. Para cada tentativa negada comparar snapshots persistidos.
- [ ] Executar `./gradlew.bat test --tests '*event.presentation.*' --tests '*IdentityApiTest'`. Confirmar que testes anteriores de concorrência continuam passando com donos válidos e que erros de segurança não mascaram o cenário de concorrência.
- [ ] Revisar diff e criar commit local: `test: cover event ownership across authenticated endpoints`, incluindo EventRequest e adaptações de contrato desta tarefa.

## Tarefa 4: Documentação e validação final

**Files:** `README.md`, `docs/architecture.md`, `docs/invariants.md`, `docs/validation/event-seat.md`.

- [ ] Documentar ownerId vindo do token, 403 para outro organizador, sem bypass ADMIN, leitura autenticada preservada, migration V6 e legado somente leitura. Texto-base:

```text
Todo novo evento pertence ao organizador identificado pelo subject do JWT de
criação. Somente esse proprietário com ORGANIZER pode editar, publicar e
gerenciar assentos. O corpo não aceita ownerId. Eventos legados sem proprietário
continuam consultáveis, mas operações de gerenciamento retornam 403.
```

- [ ] Executar `./gradlew.bat check` e `git diff --check`. Confirmar zero falhas e cobertura de linhas/branches de pelo menos 80%. Não declarar testes executados quando Docker/JDK impedirem o comando; resolver o ambiente dentro da autorização existente ou reportar o bloqueio real.
- [ ] Verificar ausência de overloads de escrita sem actorId com `rg -n 'public .*execute|new Event\(|Event.restore\(' src/main/java/br/com/gustavoakira/ticketing/core/event`. Conferir que nenhuma query atualiza owner_id e nenhuma migration V1–V5 foi alterada.
- [ ] Registrar resultados reais de validação, revisar alterações completas contra a spec e corrigir problemas encontrados. Reexecutar somente verificações afetadas por correções.
- [ ] Criar commit local `docs: document event ownership authorization`. Seguir o fluxo de revisão final do método de execução escolhido; não publicar/mesclar automaticamente.

## Handoff

Recomendação: execução nativa nesta sessão, em sequência, porque as tarefas compartilham assinaturas e fixtures. Revisão independente ao final. Alternativa: subagente por tarefa com revisão entre tarefas, custando mais contextos. A tarefa 1 deve ser agrupada com a 2 na execução para evitar um commit com contrato de criação inconsistente.

Auto-revisão do plano: spec coberta por modelo/migration (1), autorização/erros/concorrência (2), contrato HTTP e regressões (3), documentação/check (4). Os cinco focos de revisão possuem testes atribuídos; todas as assinaturas usam actorId como último argumento.
