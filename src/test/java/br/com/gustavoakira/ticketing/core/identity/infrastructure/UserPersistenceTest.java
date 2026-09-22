package br.com.gustavoakira.ticketing.core.identity.infrastructure;

import br.com.gustavoakira.ticketing.core.identity.domain.Role;
import br.com.gustavoakira.ticketing.core.identity.domain.User;
import br.com.gustavoakira.ticketing.core.identity.port.UserRepository;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class UserPersistenceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void savesAndLoadsAllFieldsWithoutCallerTransaction() {
        var user = new User("Ana", email(), EnumSet.allOf(Role.class));
        var saved = users.save(user);
        var loaded = users.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(user.getId());
        assertThat(loaded.getName()).isEqualTo("Ana");
        assertThat(loaded.getEmail()).isEqualTo(user.getEmail());
        assertThat(loaded.getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.ORGANIZER, Role.ADMIN);
        assertThat(loaded.getCreatedAt()).isNotNull().isEqualTo(saved.getCreatedAt());
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        var byEmail = users.findByEmail(" " + user.getEmail().toUpperCase(Locale.ROOT) + " ").orElseThrow();
        assertThat(byEmail.getId()).isEqualTo(saved.getId());
        assertThat(byEmail.getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.ORGANIZER, Role.ADMIN);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '4' and success", Integer.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void rolesRoundTripByName(Role role) {
        var saved = users.save(new User("Ana", email(), Set.of(role)));
        assertThat(users.findById(saved.getId()).orElseThrow().getRoles()).containsExactly(role);
        assertThat(jdbc.queryForObject("select role from user_roles where user_id = ?", String.class, saved.getId()))
                .isEqualTo(role.name());
    }

    @Test
    void absentUsersReturnEmpty() {
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
        assertThat(users.findByEmail(email())).isEmpty();
    }

    @Test
    void savingSameSnapshotDoesNotDuplicateRolesOrResetAudit() {
        var saved = users.save(new User("Ana", email(), EnumSet.allOf(Role.class)));
        var again = users.save(saved);
        assertThat(again.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(again.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, saved.getId()))
                .isEqualTo(3);
    }

    @Test
    void duplicateNormalizedEmailCannotCreateAnotherUser() {
        var saved = users.save(new User("Ana", email(), Set.of(Role.CUSTOMER)));
        var duplicate = new User("Another", " " + saved.getEmail().toUpperCase(Locale.ROOT) + " ", Set.of(Role.ADMIN));
        assertThatThrownBy(() -> users.save(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findById(duplicate.getId())).isEmpty();
        assertThat(users.findById(saved.getId()).orElseThrow().getRoles()).containsExactly(Role.CUSTOMER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name = ''", "name = ' '", "name = null", "email = null", "email = ''",
            "email = 'invalid'", "email = 'a@@b'", "email = 'a b@c'", "email = 'UPPER@example.com'",
            "email = ' a@b '", "created_at = null", "updated_at = null"})
    void databaseRejectsInvalidUserData(String assignment) {
        var saved = users.save(new User("Ana", email(), Set.of(Role.CUSTOMER)));
        assertThatThrownBy(() -> jdbc.update("update users set " + assignment + " where id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesEmailUniquenessEvenWithoutDomain() {
        var first = users.save(new User("Ana", email(), Set.of(Role.CUSTOMER)));
        var second = users.save(new User("Bea", email(), Set.of(Role.CUSTOMER)));
        assertThatThrownBy(() -> jdbc.update("update users set email = ? where id = ?", first.getEmail(), second.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidDuplicateAndOrphanRoles() {
        var saved = users.save(new User("Ana", email(), Set.of(Role.CUSTOMER)));
        assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, 'INVALID')", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, 'CUSTOMER')", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, 'CUSTOMER')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, null)", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserRemovesOnlyItsRoles() {
        var first = users.save(new User("Ana", email(), EnumSet.allOf(Role.class)));
        var other = users.save(new User("Bea", email(), Set.of(Role.ORGANIZER)));
        jdbc.update("delete from users where id = ?", first.getId());
        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, first.getId())).isZero();
        assertThat(users.findById(other.getId()).orElseThrow().getRoles()).containsExactly(Role.ORGANIZER);
    }

    @Test
    void auditUpdateMustBeExplicit() {
        var saved = users.save(new User("Ana", email(), Set.of(Role.CUSTOMER)));
        jdbc.update("update users set name = 'Bea' where id = ?", saved.getId());
        var loaded = users.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Bea");
        assertThat(loaded.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void invalidRoleRollsBackUserInSameTransaction() {
        var user = new User("Ana", email(), Set.of(Role.CUSTOMER));
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            users.save(user);
            jdbc.update("insert into user_roles (user_id, role) values (?, 'INVALID')", user.getId());
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void concurrentInsertsOfSameEmailHaveExactlyOneWinner() throws Exception {
        var email = email();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> insertAfter(start, email, Role.CUSTOMER));
            var second = executor.submit(() -> insertAfter(start, email, Role.ORGANIZER));
            start.countDown();
            assertThat(new Boolean[] {first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
            assertThat(users.findByEmail(email).orElseThrow().getRoles()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean insertAfter(CountDownLatch start, String email, Role role) throws InterruptedException {
        if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start latch timed out");
        try {
            users.save(new User("Ana", email, Set.of(role)));
            return true;
        } catch (DataIntegrityViolationException expected) {
            return false;
        }
    }

    private String email() {
        return UUID.randomUUID() + "@example.com";
    }
}
