# Identity Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution or superpowers:subagent-driven-development if the user chooses delegation. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Entregar cadastro CUSTOMER, autenticação JWT com refresh rotativo, logout, concessão de ORGANIZER por ADMIN e comando administrativo para criar ADMIN.

**Architecture:** Manter Identity por feature, domínio independente e casos de uso transacionais. Reutilizar UserRepository; portas específicas para credenciais, sessões, concessão de roles, hashing e emissão de tokens. Persistência de autenticação por JDBC para explicitar bloqueios e operações condicionais; usuários continuam com o adaptador JPA existente, participando da mesma transação.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Security Resource Server/Jose, BCrypt, PostgreSQL 17.6, Flyway, Gradle, JUnit, AssertJ, MockMvc e Testcontainers.

**Spec:** [Identity: autenticação e autorização](../specs/2026-09-22-identity-auth-design.md).

## Global Constraints

- Trabalhar somente em `feat/identity-auth`; nunca implementar ou fazer commits na `main`.
- Domínio sem Spring/JPA. Não modificar migrations V1 a V4.
- Cadastro exclusivamente CUSTOMER. Roles independentes; ADMIN não implica ORGANIZER.
- Propriedade de eventos fica para outro PR; ORGANIZER pode gerenciar qualquer evento.
- JWT RS256 por 15 minutos; refresh opaco de 32 bytes aleatórios; limite absoluto de sessão de 7 dias.
- Guardar somente SHA-256 do refresh e BCrypt custo 12 da senha, com prefixo `{bcrypt}`.
- Senha de 12 a 64 caracteres Unicode e até 72 bytes UTF-8, sem trim ou normalização.
- JSON para tokens; Bearer para acesso; sem Basic, sessão HTTP ou autenticação por cookie.
- Logout não invalida JWT emitido; revoga somente a sessão de renovação informada.
- Chaves externas ao Git; modo CLI não exige chaves e não abre HTTP.
- PostgreSQL real via Testcontainers, sem H2 ou testes de integração ignorados.
- `gradlew.bat check` exige 80% de linhas e branches, sem novas exclusões.

## Review Focus

1. Reutilização retorna 401, mas sua revogação deve sobreviver ao commit: tarefas 2 e 5.
2. Duas renovações ou refresh/logout simultâneos não podem ressuscitar sessão: tarefa 2.
3. UTF-8 pode exceder 72 bytes antes de 64 caracteres; nenhuma senha pode ser truncada: tarefa 1.
4. Concessão concorrente não pode perder roles ou atualizar auditoria sem mudança: tarefa 3.
5. CLI sem console, e-mail existente ou JWT ausente não pode iniciar servidor nem alterar conta existente: tarefa 6.

## Convenções e mapa de arquivos

`P` significa `src/main/java/br/com/gustavoakira/ticketing/core/identity` e `T`
significa `src/test/java/br/com/gustavoakira/ticketing/core/identity`. Caminhos relativos
abaixo são sempre expandidos a partir dessas raízes. Um tipo público por arquivo.

| Arquivos | Responsabilidade |
| --- | --- |
| `P/domain/PasswordPolicy.java` | Validar senha sem armazená-la |
| `P/domain/RefreshSession.java`, `RefreshToken.java` | Snapshots persistidos e regras temporais |
| `P/port/PasswordHasher.java`, `CredentialRepository.java` | Hashing e armazenamento de credenciais |
| `P/port/RefreshSessionRepository.java`, `RefreshTokenGenerator.java` | Persistência e geração/digest de refresh |
| `P/port/RoleGrantRepository.java`, `AccessTokenIssuer.java` | Concessão aditiva e emissão JWT |
| `P/infrastructure/security/BCryptPasswordHasher.java`, `SecureRefreshTokenGenerator.java` | Adaptadores criptográficos |
| `P/infrastructure/persistence/JdbcCredentialRepository.java`, `JdbcRefreshSessionRepository.java`, `JdbcRoleGrantRepository.java` | SQL e conversão explícitos |
| `P/application/CreateAccountUseCase.java`, `RegisterUserUseCase.java`, `LoginUseCase.java` | Criação interna, cadastro público e login |
| `P/application/RefreshSessionUseCase.java`, `LogoutUseCase.java`, `GrantOrganizerUseCase.java` | Renovação, logout e autorização administrativa |
| `P/application/TokenPair.java`, `UserResult.java`, `RefreshResult.java` | Resultados sem referências a frameworks |
| `P/application/InvalidCredentialsException.java`, `DuplicateEmailException.java`, `UserNotFoundException.java` | Erros esperados |
| `P/infrastructure/security/JwtConfiguration.java`, `JwtAccessTokenIssuer.java`, `SecurityConfiguration.java`, `SecurityProblemHandler.java` | Beans JWT, filtro oficial, matriz de acesso e erros |
| `P/infrastructure/IdentityConfiguration.java` | Clock e beans independentes de HTTP |
| `P/presentation/AuthController.java`, `RoleController.java`, `IdentityExceptionHandler.java` | Contrato HTTP e Problem Details |
| `P/presentation/RegisterRequest.java`, `LoginRequest.java`, `RefreshRequest.java` | Entrada JSON sem toString que exponha segredos |
| `P/presentation/cli/CreateAdminCommand.java`, `PasswordConsole.java`, `SystemPasswordConsole.java` | Entrada administrativa, parsing e senha oculta |

Modificar ainda `CoreApplication.java`, `build.gradle`, `application.yaml`,
`compose.yaml`, `.gitignore`, `README.md`, `docs/architecture.md` e `docs/domain-model.md`.
Criar V5, `src/test/resources/application.yaml` e PEMs de teste sob
`src/test/resources/identity/`. Chaves de teste são públicas e exclusivamente de teste.

## Tarefa 1: Credenciais e primitivas de refresh

**Files:** tipos domain/port de senha e refresh, adaptadores BCrypt e gerador;
`T/domain/PasswordPolicyTest.java`, `T/domain/RefreshSessionTest.java`,
`T/infrastructure/IdentityCryptoTest.java`.

**Interfaces produzidas:**

```java
// PasswordPolicy
public static void validate(String password);
// PasswordHasher
String hash(String password);
boolean matches(String password, String encoded);
// CredentialRepository
void save(UUID userId, String passwordHash);
Optional<String> findHash(UUID userId);
// RefreshTokenGenerator
String generate();
String digest(String rawToken);
// domain snapshots
public record RefreshSession(UUID id, UUID userId, Instant createdAt,
    Instant expiresAt, Instant revokedAt) {
    public boolean activeAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }
}
public record RefreshToken(UUID id, UUID sessionId, String tokenHash, Instant consumedAt) {}
```

- [x] Escrever testes de limites, null, senha não aparada, Unicode multibyte e fronteira exata de expiração:

```java
@Test void rejectsBcryptByteOverflow() {
    assertThatThrownBy(() -> PasswordPolicy.validate("é".repeat(37)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> PasswordPolicy.validate("é".repeat(36))).doesNotThrowAnyException();
}
@Test void expiresAtTheExactBoundary() {
    var end = Instant.parse("2026-10-01T00:00:00Z");
    var session = new RefreshSession(UUID.randomUUID(), UUID.randomUUID(),
        end.minusSeconds(604800), end, null);
    assertThat(session.activeAt(end.minusNanos(1))).isTrue();
    assertThat(session.activeAt(end)).isFalse();
}
```

- [x] Executar `gradlew.bat test --tests '*PasswordPolicyTest' --tests '*RefreshSessionTest' --tests '*IdentityCryptoTest'`; esperar falha pelos novos tipos ausentes.
- [x] Implementar validação por `codePointCount` e comprimento UTF-8; BCrypt 12 com prefixo e nenhuma exposição da senha. Gerar 32 bytes SecureRandom, Base64 URL sem padding; digest SHA-256 hexadecimal.
- [x] Testar hash diferente para a mesma senha, matches correto/incorreto, prefixo/custo, segredo de 43 caracteres e digest estável de 64 caracteres hexadecimais. Testar que nenhuma senha é aparada.
- [x] Reexecutar os três testes; esperar todos verdes. Commit: `feat: add identity credential and refresh primitives`.

## Tarefa 2: Persistência e transações de renovação

**Files:** V5 e adaptadores JDBC de credencial/sessão;
`P/application/RefreshSessionUseCase.java`, `LogoutUseCase.java`, `RefreshResult.java`,
`T/infrastructure/RefreshSessionPersistenceTest.java`, `IdentityMigrationUpgradeTest.java`.

**Interfaces produzidas:**

```java
// RefreshSessionRepository: métodos usados dentro da transação do caso de uso
void createSession(UUID id, UUID userId, Instant expiresAt);
void insertToken(UUID id, UUID sessionId, String hash);
Optional<RefreshToken> findToken(String hash);
Optional<RefreshSession> lockSession(UUID id);
void consume(UUID tokenId, Instant now);
void revoke(UUID sessionId, Instant now);
// RefreshResult: resultado transacional sem exceção em rejeição
public record RefreshResult(UUID userId, String refreshToken, Instant refreshExpiresAt) {
    public boolean accepted() { return userId != null; }
    public static RefreshResult rejected() { return new RefreshResult(null, null, null); }
}
// RefreshSessionUseCase
RefreshResult execute(String rawToken);
// LogoutUseCase
void execute(String rawToken);
```

- [x] Escrever teste de reutilização que consulte o banco após retorno rejeitado, sem transação envolvendo o teste. O fixture cria usuário, sessão e token por SQL:

```java
@Test void replayRevocationSurvivesTheRejectedResult() {
    String original = seedSession();
    var rotated = refresh.execute(original);
    assertThat(rotated.accepted()).isTrue();
    assertThat(refresh.execute(original).accepted()).isFalse();
    assertThat(refresh.execute(rotated.refreshToken()).accepted()).isFalse();
    assertThat(jdbc.queryForObject("select count(*) from refresh_sessions where revoked_at is not null",
        Integer.class)).isEqualTo(1);
}
```

- [x] Executar `gradlew.bat test --tests '*RefreshSessionPersistenceTest' --tests '*IdentityMigrationUpgradeTest'`; esperar falha por ausência de implementação/schema.
- [x] Criar V5: user_credentials PK/FK user_id, hash obrigatório; refresh_sessions PK UUID, FK user_id, timestamps e expires_at; refresh_tokens PK UUID, FK session_id, hash CHAR(64) único, consumed_at. Defaults statement_timestamp em created_at; índices das FKs; ON DELETE CASCADE.
- [x] Implementar `lockSession` com `SELECT ... FOR UPDATE`. Renovação encontra token, bloqueia sessão e relê token. Tokens ausentes/expirados/revogados rejeitam; consumidos revogam a sessão e retornam rejeição; ativos são consumidos e substituídos dentro da mesma transação.

```java
// Dentro de execute @Transactional, depois de localizar e bloquear:
if (!session.activeAt(clock.instant())) return RefreshResult.rejected();
if (current.consumedAt() != null) {
    sessions.revoke(session.id(), clock.instant());
    return RefreshResult.rejected();
}
String successor = tokens.generate();
sessions.consume(current.id(), clock.instant());
sessions.insertToken(UuidCreator.getTimeOrderedEpoch(), session.id(), tokens.digest(successor));
return new RefreshResult(session.userId(), successor, session.expiresAt());
```

- [x] Implementar logout com o mesmo bloqueio; unknown/expired/revoked é idempotente. Nunca renovar expiresAt nem lançar exceção de autenticação dentro da transação que persiste revogação.
- [x] Testar duas threads com CountDownLatch e futures com timeout: no máximo uma renovação aceita; replay invalida sucessor. Testar refresh/logout concorrentes, duas sessões independentes, expiração exata por Clock e rollback ao falhar inserção do sucessor.
- [x] Testar V4→V5 preservando usuário legado sem credencial e rejeição de hashes duplicados/FKs inválidas. Verificar que o segredo gerado não está no banco.
- [x] Reexecutar os dois testes; esperar todos verdes. Commit: `feat: persist and rotate identity refresh sessions`.

## Tarefa 3: Cadastro, login e concessão de ORGANIZER

**Files:** casos de uso de conta/cadastro/login/concessão, resultados, exceções,
RoleGrantRepository e adaptador; `T/application/IdentityUseCasesTest.java`,
`T/infrastructure/AccountPersistenceTest.java`.

**Interfaces produzidas/consumidas:**

```java
// CreateAccountUseCase: interno; consumidores são cadastro e CLI
User execute(String name, String email, String password, Set<Role> roles);
// RegisterUserUseCase: nunca recebe roles do cliente
UserResult execute(String name, String email, String password);
// AccessTokenIssuer
String issue(User user);
public record TokenPair(String accessToken, String tokenType, long expiresIn,
    String refreshToken, Instant refreshExpiresAt) {}
public record UserResult(UUID id, String name, String email, Set<Role> roles,
    Instant createdAt, Instant updatedAt) {}
// LoginUseCase
TokenPair execute(String email, String password);
// RoleGrantRepository: false somente quando usuário não existe
boolean grantOrganizer(UUID userId);
// GrantOrganizerUseCase
void execute(UUID userId);
```

- [x] Escrever teste real de cadastro atômico e duplicidade, além de login que falha para usuário sem credencial:

```java
@Test void publicRegistrationNeverGrantsPrivilegedRoles() {
    var result = register.execute("Ana", " ANA@example.com ", "a valid password");
    var user = users.findById(result.id()).orElseThrow();
    assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
    assertThat(user.getEmail()).isEqualTo("ana@example.com");
    assertThat(credentials.findHash(user.getId())).hasValueSatisfying(hash ->
        assertThat(hash).startsWith("{bcrypt}$2"));
}
```

- [x] Executar `gradlew.bat test --tests '*IdentityUseCasesTest' --tests '*AccountPersistenceTest'`; esperar falhas pelos casos de uso ausentes.
- [x] Implementar criação transacional com UserRepository/credencial e converter apenas `uk_users_email` em DuplicateEmailException. Cadastro passa `Set.of(Role.CUSTOMER)` internamente.
- [x] Login normaliza e-mail, verifica senha com hash fictício pré-calculado para usuário/credencial ausentes e retorna erro genérico. Após autenticar, cria sessão de 7 dias e token opaco; retorna JWT via porta, sem dados sensíveis no erro.
- [x] Concessão bloqueia linha de usuário para serializar operações e usa inserção aditiva:

```sql
INSERT INTO user_roles (user_id, role) VALUES (?, 'ORGANIZER')
ON CONFLICT (user_id, role) DO NOTHING;
```

Atualizar users.updated_at com statement_timestamp somente se a inserção afetar uma linha. Não reutilizar save(User) para substituir o conjunto de roles.
- [x] Testar duplicidade concorrente, rollback ao falhar credencial, usuário legado, login inválido, duas sessões de login independentes, idempotência de concessão, preservação de ADMIN/CUSTOMER e auditoria. Usar stub AccessTokenIssuer somente nesta camada; JWT real será testado nas tarefas 4/5.
- [x] Reexecutar os testes; esperar todos verdes. Commit: `feat: add identity registration login and role grants`.

## Tarefa 4: JWT e configuração de segurança

**Files:** configurações/adaptadores JWT/security, IdentityConfiguration, build.gradle,
application.yaml, fixtures de teste, compose.yaml, .gitignore;
`T/infrastructure/JwtSecurityTest.java`, `SecurityConfigurationTest.java`.

**Interfaces:** JwtAccessTokenIssuer implementa `String issue(User user)`;
JwtConfiguration fornece JwtEncoder/JwtDecoder, somente no modo web;
IdentityConfiguration fornece Clock.systemUTC e PasswordHasher em ambos os modos.

- [x] Escrever testes de emissão/validação com par RSA de teste: ID como sub, roles, TTL 900s e rejeição de algoritmo/assinatura/issuer/audience/expiração/UUID inválidos. Não mockar JwtDecoder nestes testes.
- [x] Executar `gradlew.bat test --tests '*JwtSecurityTest' --tests '*SecurityConfigurationTest'`; esperar falha pelos componentes ausentes.
- [x] Adicionar starter OAuth2 Resource Server gerenciado pelo BOM e configurar NimbusJwtEncoder/NimbusJwtDecoder RS256. Validar todos os claims exigidos. Fixar expiração de 15 minutos e usar Clock na emissão; testes de expiração não dependem de sleep.
- [x] Configurar matchers por método e rota exatos, terminando em denyAll. GETs events/seats autenticados; POST/PUT/PATCH existentes exigem ORGANIZER; concessão exige ADMIN; somente as quatro rotas auth são públicas.

```java
http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .csrf(csrf -> csrf.disable())
    .httpBasic(basic -> basic.disable())
    .formLogin(form -> form.disable());
```

Usar conversor roles→ROLE_* e handlers 401/403 em application/problem+json, incluindo WWW-Authenticate no 401 Bearer. Não aceitar cookies/Basic como autenticação.
- [x] Configurar `identity.jwt.private-key`, `public-key`, `issuer`, `audience` via ambiente. Usar @ConditionalOnWebApplication nos componentes JWT e SecurityFilterChain para permitir CLI sem chaves.
- [x] Adicionar recursos de teste para todos os @SpringBootTest existentes, sem chave padrão em produção. Compose monta diretório local de chaves somente leitura; .gitignore protege esse diretório.
- [x] Reexecutar testes JWT/configuração; esperar todos verdes. Commit: `feat: configure JWT authentication and role authorization`.

## Tarefa 5: API e regressão de eventos/assentos

**Files:** controllers/requests/handler de Identity;
`T/presentation/IdentityApiTest.java`, `AuthorizationApiTest.java`;
modificar testes EventApiTest, SeatApiTest, EventStatusConflictApiTest e SeatConcurrencyApiTest.

**Interfaces consumidas:** os casos de uso das tarefas 2/3; AccessTokenIssuer.issue(User).
AuthController coordena chamadas: concluir `RefreshSessionUseCase.execute`,
depois retornar 401 se rejeitado; se aceito, consultar usuário atual e emitir JWT.
Não envolver essa coordenação HTTP em transação que possa desfazer a revogação.

- [x] Escrever testes MockMvc reais para o contrato completo:

```java
@Test void registerRejectsClientSuppliedRoles() throws Exception {
    mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"name":"Ana","email":"ana@example.com","password":"a valid password",
             "roles":["ADMIN"]}
            """))
        .andExpect(status().isBadRequest());
    assertThat(users.findByEmail("ana@example.com")).isEmpty();
}
```

- [x] Executar `gradlew.bat test --tests '*IdentityApiTest' --tests '*AuthorizationApiTest'`; esperar falha porque endpoints não existem.
- [x] Criar requests com validação explícita, sem toString de segredos. Rejeitar roles no cadastro, JSON null/incompleto e tipos incorretos. Preservar parsing de corpos dos controllers existentes.
- [x] Implementar os cinco endpoints da spec. Respostas com tokens usam no-store; perfil não contém hashes/segredos. Logout retorna 204 inclusive para token desconhecido bem formado. Handler restrito aos controllers de Identity.
- [x] Adicionar fluxo HTTP cadastro→login→GET→refresh→replay→401 do sucessor e conferir revoked_at após respostas. Conceder role com ADMIN, verificar JWT antigo sem permissão e JWT renovado com ORGANIZER.
- [x] Exercitar cada rota protegida com JWT real: sem autenticação 401, CUSTOMER/ADMIN sem ORGANIZER 403 em escrita, ORGANIZER autorizado; concessão exige ADMIN. Rotas desconhecidas negadas.
- [x] Atualizar testes funcionais existentes com autoridades adequadas e retirar expectativa de CSRF; substituir os casos negativos por ausência de role. Manter cenários funcionais/concorrentes originais, sem desabilitar filtros.
- [x] Executar `gradlew.bat test`; esperar suíte inteira verde. Commit: `feat: expose identity authentication and authorization API`.

## Tarefa 6: Comando ADMIN, documentação e verificação final

**Files:** CoreApplication.java, classes presentation/cli, documentação;
`T/presentation/cli/CreateAdminCommandTest.java`, `AdminCommandIntegrationTest.java`.

**Interfaces:**

```java
// PasswordConsole
char[] readPassword(String prompt);
// CreateAdminCommand, recebe CreateAccountUseCase e PasswordConsole
int execute(String[] args);
```

- [x] Escrever teste que cria ADMIN e outro que repete e-mail sem alterar senha/roles; console fake injeta arrays de senha, sem substituto em produção.
- [x] Executar `gradlew.bat test --tests '*CreateAdminCommandTest' --tests '*AdminCommandIntegrationTest'`; esperar falha pelos tipos ausentes.
- [x] Detectar `identity create-admin` antes de iniciar Spring; selecionar WebApplicationType.NONE. Validar --name/--email únicos, rejeitar argumentos desconhecidos e opções de senha. Executar caso de uso com `Set.of(Role.ADMIN)`, fechar contexto e retornar exit code.
- [x] SystemPasswordConsole exige System.console; ler senha e confirmação sem eco e limpar arrays em finally. Divergência, ausência de console e e-mail duplicado retornam falha sem gravação parcial; nunca promover usuário existente.
- [x] Testar contexto CLI sem propriedades JWT e sem web server. Testar argumentos incorretos, confirmação divergente, limpeza dos buffers e sucesso/erro. Comando normal sem argumentos continua iniciando HTTP.
- [x] Atualizar README com geração/montagem de chaves, exemplos HTTP, expiração/logout, concessão e comando ADMIN no servidor/Compose com TTY. Substituir documentação antiga de Basic/CSRF. Atualizar arquitetura e modelo com novas tabelas/portas.
- [x] Executar `gradlew.bat check`, ler resultado e relatórios de cobertura; esperar zero falhas e limites de 80% atendidos. Cobrir branches de comportamento faltantes sem baixar gate ou excluir classes.
- [x] Executar `git diff --check` e revisão da branch contra a spec; corrigir problemas materiais e reexecutar testes afetados e check se houver alteração de código.
- [x] Commit: `feat: add administrative account command and document identity auth`.

## Referências técnicas para a execução

- [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security OAuth2 e JwtEncoder](https://docs.spring.io/spring-security/reference/servlet/oauth2/)
- [RFC 9700, refresh tokens](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)
- Padrões locais: `event/application/CreateEventUseCase.java`, `event/presentation/EventExceptionHandler.java`, `identity/infrastructure/persistence/JpaUserRepository.java`.

## Estado

Implementado na branch `feat/identity-auth`. Resultado final: `gradlew.bat check bootJar` passou com 278 testes, zero falhas, zero erros e zero testes ignorados. JaCoCo: 96,39% de linhas e 84,51% de branches. `git diff --check` e `docker compose config --quiet` passaram. O JAR não contém chaves PEM nem classes de teste.

## Decisões e evidências da execução

- Execução direta no checkout dedicado `feat/identity-auth`, sem modificar main.
- Java localizado em `D:/.jdks/openjdk-25.0.2`; testes PostgreSQL executados com
  acesso ao Docker fora do sandbox. Logs em `build/identity-auth-work`.
- Primitivas, sessões, contas, JWT, HTTP e CLI tiveram testes executados antes
  da implementação; falhas por tipos/rotas ausentes registradas nos logs RED.
- Os testes planejados de casos de uso foram agrupados em AccountPersistenceTest
  com PostgreSQL real. A matriz de autorização está em IdentityApiTest, e a
  configuração JWT está em JwtSecurityTest, evitando fixtures duplicadas.
- Revisão independente confirmou bloqueios/transações e identificou coerção
  indevida de números JSON. Teste reproduziu cadastro 201 para nome numérico;
  parsing estrito corrigiu o comportamento para 400. Foram acrescentados testes
  de algoritmo RS512, JWT renovado com nova role e bootstrap administrativo real.
- Uma nova rodada do revisor ficou indisponível por limite do provedor; as correções
  foram inspecionadas localmente e passaram na suíte completa.
- O teste de rollback permite atualizar o token original e rejeita especificamente
  a inserção do sucessor, comprovando a reversão do consumo.
- A matriz HTTP usa payload de lote parseável. A validação preexistente de sections
  ausente na criação de assentos não foi alterada neste PR de Identity.
- Credenciais/rotação foram consolidadas em um commit após os dois ciclos de teste;
  API, configuração e CLI são concluídas após a verificação integrada.
