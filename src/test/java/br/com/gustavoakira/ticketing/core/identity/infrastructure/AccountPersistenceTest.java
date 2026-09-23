package br.com.gustavoakira.ticketing.core.identity.infrastructure;

import br.com.gustavoakira.ticketing.core.identity.application.*;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest @Testcontainers
class AccountPersistenceTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired RegisterUserUseCase register;
    @Autowired CreateAccountUseCase create;
    @Autowired LoginUseCase login;
    @Autowired GrantOrganizerUseCase grant;
    @Autowired UserRepository users;
    @Autowired CredentialRepository credentials;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccessTokenIssuer issuer;
    static final String PASSWORD = "a valid password";
    @BeforeEach void setup() { jdbc.update("delete from users"); when(issuer.issue(any())).thenReturn("test-token"); }
    @Test void registrationNormalizesEmailAndGrantsOnlyCustomer() {
        var result = register.execute("Ana", " ANA@example.com ", PASSWORD);
        assertThat(result.roles()).containsExactly(Role.CUSTOMER);
        assertThat(result.email()).isEqualTo("ana@example.com");
        assertThat(credentials.findHash(result.id())).hasValueSatisfying(hash -> assertThat(hash).startsWith("{bcrypt}$2a$12$"));
        var pair = login.execute(" ANA@example.com ", PASSWORD);
        assertThat(pair.accessToken()).isEqualTo("test-token");
        assertThat(pair.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(login.execute(result.email(), PASSWORD).refreshToken()).isNotEqualTo(pair.refreshToken());
        assertThat(jdbc.queryForObject("select count(*) from refresh_sessions", Integer.class)).isEqualTo(2);
    }
    @Test void wrongMissingAndLegacyCredentialsHaveSameFailure() {
        register.execute("Ana", "ana@example.com", PASSWORD);
        users.save(new User("Legacy", "legacy@example.com", Set.of(Role.CUSTOMER)));
        for (String email : List.of("ana@example.com", "absent@example.com", "legacy@example.com")) {
            assertThatThrownBy(() -> login.execute(email, "wrong password")).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");
        }
        assertThatThrownBy(() -> login.execute(null, PASSWORD)).isInstanceOf(InvalidCredentialsException.class);
    }
    @Test void failedCredentialInsertLeavesNoPartialAccount() {
        jdbc.execute("alter table user_credentials add constraint reject_credential check (false) not valid");
        try { assertThatThrownBy(() -> register.execute("Ana", "ana@example.com", PASSWORD)).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("alter table user_credentials drop constraint reject_credential"); }
        assertThat(users.findByEmail("ana@example.com")).isEmpty();
    }
    @Test void concurrentDuplicateEmailHasExactlyOneCompleteAccount() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call = () -> { start.await(); try { register.execute("Ana", "ana@example.com", PASSWORD); return true; }
                catch (DuplicateEmailException e) { return false; } };
            var a = pool.submit(call); var b = pool.submit(call); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_credentials", Integer.class)).isEqualTo(1);
    }
    @Test void grantPreservesRolesAndIsIdempotentIncludingAudit() {
        var admin = create.execute("Admin", "admin@example.com", PASSWORD, Set.of(Role.ADMIN, Role.CUSTOMER));
        jdbc.update("update users set updated_at = '2000-01-01T00:00:00Z' where id = ?", admin.getId());
        grant.execute(admin.getId());
        var granted = users.findById(admin.getId()).orElseThrow();
        assertThat(granted.getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CUSTOMER, Role.ORGANIZER);
        grant.execute(admin.getId());
        assertThat(users.findById(admin.getId()).orElseThrow().getUpdatedAt()).isEqualTo(granted.getUpdatedAt());
        assertThatThrownBy(() -> grant.execute(UUID.randomUUID())).isInstanceOf(UserNotFoundException.class);
    }
    @Test void duplicateAdministrativeCreationDoesNotPromoteOrResetPassword() {
        var customer = register.execute("Ana", "ana@example.com", PASSWORD);
        var before = credentials.findHash(customer.id());
        assertThatThrownBy(() -> create.execute("Admin", "ana@example.com", "different password", Set.of(Role.ADMIN)))
            .isInstanceOf(DuplicateEmailException.class);
        assertThat(credentials.findHash(customer.id())).isEqualTo(before);
        assertThat(users.findById(customer.id()).orElseThrow().getRoles()).containsExactly(Role.CUSTOMER);
    }
}
