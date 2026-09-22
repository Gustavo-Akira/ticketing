# Identity Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Entregar o primeiro PR de identity com usuários, múltiplas roles e persistência testada.

**Architecture:** Feature `identity` com domínio independente, porta e adaptador JPA. Mapeamento explícito entre snapshots de domínio e entidades; PostgreSQL garante unicidade e integridade referencial. Autenticação será outro PR.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Data JPA, Flyway, PostgreSQL, JUnit 5, AssertJ, Testcontainers e Gradle existentes no projeto.

**Spec:** [Base de identity](../specs/2026-09-22-identity-base-design.md).

## Global Constraints

- Uma mesma identidade pode acumular as roles `CUSTOMER`, `ORGANIZER` e `ADMIN`.
- Domínio sem dependências de Spring ou JPA.
- ID com `UuidCreator.getTimeOrderedEpoch()`, conforme `Event`.
- Nome com até 255 caracteres; e-mail com até 254 caracteres.
- E-mail normalizado com remoção de espaços externos e `Locale.ROOT`.
- Auditoria gerada pelo banco na inserção, sem triggers.
- PostgreSQL real via Testcontainers; não adicionar H2.
- Não alterar migrations existentes nem configurações de segurança.
- Não adicionar dependências, endpoints, credenciais ou regras de autorização.
- Executar `gradlew.bat check`, incluindo cobertura de 80% para linhas e branches.

## Review Focus

1. Locale da JVM não pode mudar a identidade de um e-mail: teste com locale turco na tarefa 1.
2. Alterar o Set recebido ou devolvido não pode conceder roles: teste de cópia defensiva na tarefa 1.
3. Consultas sem transação do chamador precisam carregar roles com OSIV desativado: tarefa 2.
4. Cadastro concorrente com mesmo e-mail não pode persistir duas identidades: tarefa 2.
5. Erro após inserir usuário e antes de concluir roles deve desfazer toda a operação: tarefa 2.

## Mapa de arquivos

Os caminhos Java abaixo usam a raiz de produção
`src/main/java/br/com/gustavoakira/ticketing/core/identity/` e a raiz de testes
`src/test/java/br/com/gustavoakira/ticketing/core/identity/`.

| Arquivo relativo | Responsabilidade |
| --- | --- |
| `domain/Role.java` | Enum com as três roles |
| `domain/UserDetails.java` | Validação e normalização dos dados |
| `domain/User.java` | Identidade, roles imutáveis e reconstituição |
| `port/UserRepository.java` | Contrato de gravação e consulta |
| `infrastructure/persistence/UserJpaEntity.java` | Mapeamento JPA e conversão explícita |
| `infrastructure/persistence/SpringDataUserRepository.java` | Consultas Spring Data |
| `infrastructure/persistence/JpaUserRepository.java` | Adaptador transacional |
| `domain/UserDomainTest.java` (testes) | Invariantes e reconstituição |
| `domain/DomainIsolationTest.java` (testes) | Independência de frameworks |
| `infrastructure/UserPersistenceTest.java` (testes) | Banco, constraints, transações e concorrência |

Também criar `src/main/resources/db/migration/V4__create_users_and_user_roles.sql`
e atualizar `docs/domain-model.md`, `docs/architecture.md` e `README.md`.

## Tarefa 1: Domínio de usuário e roles

**Files:** Criar os três arquivos `domain` de produção e os dois testes de domínio do mapa.

**Interfaces produzidas:**

```java
public enum Role { CUSTOMER, ORGANIZER, ADMIN }
public record UserDetails(String name, String email, Set<Role> roles) {
    public static String normalizeEmail(String email);
}
// Assinaturas da classe User:
public User(String name, String email, Set<Role> roles);
public static User restore(UUID id, String name, String email, Set<Role> roles,
                           Instant createdAt, Instant updatedAt);
public UUID getId();
public String getName();
public String getEmail();
public Set<Role> getRoles();
public Instant getCreatedAt();
public Instant getUpdatedAt();
```

- [x] Escrever o teste inicial em `UserDomainTest`:

```java
@Test
void createsOneIdentityWithMultipleRolesAndNormalizedEmail() {
    var roles = EnumSet.of(Role.CUSTOMER, Role.ORGANIZER);
    var user = new User("Ana", " ANA@Example.COM ", roles);
    roles.clear();
    assertThat(user.getId().version()).isEqualTo(7);
    assertThat(user.getEmail()).isEqualTo("ana@example.com");
    assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.ORGANIZER);
    assertThatThrownBy(() -> user.getRoles().add(Role.ADMIN))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(user.getCreatedAt()).isNull();
    assertThat(user.getUpdatedAt()).isNull();
}
```

- [x] Executar `./gradlew.bat test --tests '*identity.domain.*'` e confirmar falha pela ausência dos tipos.
- [x] Implementar enum, record e classe. No construtor compacto do record, rejeitar nome nulo, em branco ou maior que 255; roles nulas, vazias ou com null; copiar com `Set.copyOf`. Rejeitar entradas inválidas com `IllegalArgumentException`, conforme o domínio existente. Normalização e formato do e-mail:

```java
if (email == null) throw new IllegalArgumentException("email is required");
var normalized = email.strip().toLowerCase(Locale.ROOT);
if (normalized.length() > 254
        || !normalized.matches("(?U)^[^\\s@]+@[^\\s@]+$")) {
    throw new IllegalArgumentException("email must be a valid address of up to 254 characters");
}
return normalized;
```

- [x] Construir `User` com dados de `UserDetails` e UUID temporal. `restore` usa os mesmos dados validados, exige ID e datas não nulos e preserva os valores recebidos. Não adicionar setters ou operações de alteração de roles.
- [x] Ampliar `UserDomainTest` com testes parametrizados para nomes nulos/em branco/256 caracteres; e-mails nulos/em branco/sem `@`/com dois `@`/com espaços internos/255 caracteres; roles nulas/vazias/com null. Aceitar limites de 255 para nome e 254 para e-mail. Cobrir cada enum e reconstituição com ID/datas conhecidos e suas variantes nulas.
- [x] Acrescentar o teste de locale, sempre restaurando o estado global:

```java
@Test
void emailNormalizationDoesNotDependOnJvmLocale() {
    var previous = Locale.getDefault();
    try {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertThat(new User("Iris", "IRIS@EXAMPLE.COM", Set.of(Role.CUSTOMER)).getEmail())
                .isEqualTo("iris@example.com");
    } finally {
        Locale.setDefault(previous);
    }
}
```

- [x] Em `DomainIsolationTest`, inspecionar anotações das classes, campos e métodos de `User`, `UserDetails` e `Role`; rejeitar pacotes `jakarta.persistence`, `org.hibernate` e `org.springframework`. Inspecionar também tipos dos campos, parâmetros e retornos para evitar dependências de frameworks por assinatura.
- [x] Executar novamente `./gradlew.bat test --tests '*identity.domain.*'`; exigir sucesso antes de registrar `feat: add identity user domain and roles`.

## Tarefa 2: Migration, porta e persistência transacional

**Files:** Criar os arquivos `port` e `infrastructure/persistence` do mapa, a migration V4 e `UserPersistenceTest`.

**Interfaces consumidas:** `User`, `UserDetails.normalizeEmail`, `Role` e os getters/restore da tarefa 1.

**Interfaces produzidas:**

```java
public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
}
// SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID>
Optional<UserJpaEntity> findByEmail(String email);
// Métodos de mapeamento em UserJpaEntity, com visibilidade de pacote:
static UserJpaEntity fromDomain(User user);
User toDomain();
```

- [x] Criar `UserPersistenceTest` com `@SpringBootTest`, `@Testcontainers`, `@ServiceConnection` e `PostgreSQLContainer("postgres:17.6-alpine")`, conforme `EventPersistenceTest`. Não marcar a classe inteira como `@Transactional`: precisamos provar que o adaptador funciona sem transação externa. Usar e-mails únicos por teste.
- [x] Escrever o primeiro teste e executar `./gradlew.bat test --tests '*identity.infrastructure.UserPersistenceTest'`, esperando falha antes da implementação:

```java
@Test
void savesAndLoadsAllFieldsWithoutCallerTransaction() {
    var email = UUID.randomUUID() + "@example.com";
    var saved = users.save(new User("Ana", email, EnumSet.allOf(Role.class)));
    var loaded = users.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getRoles()).containsExactlyInAnyOrder(Role.values());
    assertThat(loaded.getName()).isEqualTo("Ana");
    assertThat(loaded.getEmail()).isEqualTo(email);
    assertThat(loaded.getCreatedAt()).isNotNull().isEqualTo(saved.getCreatedAt());
    assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
    assertThat(users.findByEmail(" " + email.toUpperCase(Locale.ROOT) + " "))
            .get().extracting(User::getId).isEqualTo(saved.getId());
}
```

- [x] Criar a migration com as seguintes definições:

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL CHECK (name ~ '[^[:space:]]'),
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_email_format CHECK (email ~ '^[^[:space:]@]+@[^[:space:]@]+$')
);
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('CUSTOMER', 'ORGANIZER', 'ADMIN'))
);
```

- [x] Implementar entidade com `@Table(name = "users")`, construtor protegido, campos equivalentes ao domínio e `@ElementCollection` para roles. Usar `@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))`, `@Enumerated(EnumType.STRING)` e coluna `role` com comprimento 20. Copiar as roles no mapeamento.
- [x] Mapear as duas datas com `@Generated(event = INSERT)` e `insertable = false`; `createdAt` também usa `updatable = false`, conforme `EventJpaEntity`. `toDomain` chama `User.restore`.
- [x] Implementar Spring Data e adaptador. `save` usa `@Transactional` e `saveAndFlush(...).toDomain()`; consultas usam `@Transactional(readOnly = true)`, convertendo o resultado antes de encerrar a transação. `findByEmail` chama `UserDetails.normalizeEmail`. Não adicionar pré-consulta de unicidade nem capturar e ocultar violações de integridade.
- [x] Testar ausência por ID e e-mail, todas as roles, repetição de `save` do mesmo snapshot sem duplicação de associações e rejeição de e-mail duplicado após normalização. Para constraints, executar SQL direto com `JdbcTemplate` e esperar `DataIntegrityViolationException` para nome inválido, e-mail inválido/não normalizado, datas nulas, role inválida, associação duplicada e FK inexistente.
- [x] Testar cascata: gravar um usuário, executar `delete from users where id = ?` e verificar zero linhas em `user_roles` para seu ID. Testar que um update SQL sem atribuição de auditoria não muda `updated_at` automaticamente.
- [x] Testar atomicidade com `TransactionTemplate`: dentro da mesma transação, salvar um usuário e executar `insert into user_roles (user_id, role) values (?, 'INVALID')`; após a exceção, verificar que `findById` está vazio. Isso também confirma a participação do adaptador na transação externa.
- [x] Testar concorrência com duas tarefas em `Executors.newFixedThreadPool(2)` e `CountDownLatch(1)` como início comum. Ambas chamam `users.save` com o mesmo e-mail e IDs distintos. Capturar apenas `DataIntegrityViolationException` como conflito esperado, usar `Future.get(30, TimeUnit.SECONDS)` e encerrar o executor em `finally`. Exigir uma gravação e um conflito; verificar uma única linha em `users` e as roles da gravação vencedora.
- [x] Executar `./gradlew.bat test --tests '*identity.*'`. Confirmar migration aplicada, mapeamento válido e todos os testes verdes antes de registrar `feat: persist identity users and roles`.

## Tarefa 3: Documentação e validação do PR

**Files:** Atualizar `docs/domain-model.md`, `docs/architecture.md` e `README.md`.

**Interfaces consumidas:** Modelo e contrato de persistência entregues nas tarefas 1 e 2; nenhum novo contrato público.

- [x] Incluir `User` e `Role` no modelo de domínio, documentando múltiplas roles independentes e normalização do e-mail.
- [x] Incluir o módulo `identity` na organização arquitetural. No README, explicar que o PR entrega domínio/persistência; cadastro e autenticação continuam na próxima entrega. Manter estilo e organização atuais dos documentos.
- [x] Executar a verificação completa:

```powershell
./gradlew.bat check
git diff --check
```

- [x] Se falhar, distinguir erro de código, falha de teste e requisito de ambiente (Java 25/Docker). Corrigir falhas no escopo e repetir somente as verificações afetadas; não reduzir cobertura nem desabilitar testes para obter sucesso.
- [x] Conferir diff contra a especificação, especialmente ausência de alterações na segurança/API e nas migrations V1–V3. Revisar se o teste de concorrência e o de rollback falhariam sem as constraints/transações correspondentes.
- [x] Registrar documentação e preparar o primeiro PR com comportamento, limites de escopo e comandos de validação efetivamente executados. Não iniciar autenticação no mesmo PR.

## Revisão do plano

O domínio e suas invariantes estão na tarefa 1; schema, repositórios, datas, unicidade,
cardinalidade e atomicidade estão na tarefa 2; documentação e regressões estão na
tarefa 3. Os cinco riscos de Review Focus possuem testes atribuídos. As assinaturas
do domínio e da porta são únicas e compartilhadas entre as tarefas.

## Execução proposta

Execução direta nesta sessão, em sequência, com revisão ao final. São duas tarefas
de código dependentes entre si e uma de integração/documentação; dividir a
implementação entre agentes não oferece paralelismo útil neste escopo.

## Resultado da execução

Implementado na branch `feat/identity-base`, diretamente nesta sessão.
`gradlew.bat check`: 236 testes, zero falhas, zero erros e zero testes ignorados.
JaCoCo: 99,2% de linhas e 100% de branches; `git diff --check` sem erros.

A revisão independente apontou uma falha ao salvar novamente o objeto original
com auditoria ainda nula. O teste de regressão reproduziu a violação de NOT NULL;
o adaptador passou a preservar a auditoria persistida antes do merge. O teste
ficou verde e a suíte completa foi executada novamente com sucesso.

Decisão de execução: usar a branch dedicada existente e logs em
`build/identity-work`, sem criar outro worktree. Autenticação permanece no segundo PR.