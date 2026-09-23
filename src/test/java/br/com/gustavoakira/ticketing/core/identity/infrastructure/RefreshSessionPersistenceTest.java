package br.com.gustavoakira.ticketing.core.identity.infrastructure;

import br.com.gustavoakira.ticketing.core.identity.application.*;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RefreshSessionPersistenceTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired RefreshSessionUseCase refresh;
    @Autowired LogoutUseCase logout;
    @Autowired RefreshSessionRepository sessions;
    @Autowired RefreshTokenGenerator tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    UUID userId;

    @BeforeEach void setup() {
        jdbc.update("delete from users");
        userId = users.save(new User("Ana", UUID.randomUUID() + "@example.com", Set.of(Role.CUSTOMER))).getId();
    }
    private String seed() {
        String raw = tokens.generate();
        UUID session = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sessions.createSession(session, userId, Instant.now().plusSeconds(604800));
            sessions.insertToken(UUID.randomUUID(), session, tokens.digest(raw));
        });
        return raw;
    }
    @Test void replayRevocationSurvivesRejectedResult() {
        String original = seed();
        var rotated = refresh.execute(original);
        assertThat(rotated.accepted()).isTrue();
        assertThat(rotated.refreshToken()).isNotEqualTo(original);
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(refresh.execute(original).accepted()).isFalse();
        assertThat(refresh.execute(rotated.refreshToken()).accepted()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from refresh_sessions where revoked_at is not null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select token_hash from refresh_tokens", String.class))
            .hasSize(2).doesNotContain(original, rotated.refreshToken());
    }
    @Test void rotationKeepsAbsoluteExpiryAndLogoutCanUseConsumedToken() {
        String original = seed();
        var first = refresh.execute(original);
        var second = refresh.execute(first.refreshToken());
        assertThat(second.refreshExpiresAt()).isEqualTo(first.refreshExpiresAt());
        String independent = seed();
        logout.execute(original);
        logout.execute(original);
        logout.execute(tokens.generate());
        assertThat(refresh.execute(second.refreshToken()).accepted()).isFalse();
        assertThat(refresh.execute(independent).accepted()).isTrue();
    }
    @Test void unknownExpiredAndDeletedSessionsCannotRenew() {
        assertThat(refresh.execute(tokens.generate()).accepted()).isFalse();
        String expired = seed();
        jdbc.update("update refresh_sessions set expires_at = statement_timestamp() - interval '1 second'");
        assertThat(refresh.execute(expired).accepted()).isFalse();
        String deleted = seed();
        jdbc.update("delete from users where id = ?", userId);
        assertThat(refresh.execute(deleted).accepted()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isZero();
    }
    @Test void concurrentRefreshHasOneWinnerAndRevokesItsSuccessor() throws Exception {
        String original = seed();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<RefreshResult> call = () -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return refresh.execute(original); };
            var a = executor.submit(call); var b = executor.submit(call);
            start.countDown();
            var results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(RefreshResult::accepted).hasSize(1);
            var winner = results.stream().filter(RefreshResult::accepted).findFirst().orElseThrow();
            assertThat(refresh.execute(winner.refreshToken()).accepted()).isFalse();
        }
    }
    @Test void concurrentLogoutNeverLeavesUsableSuccessor() throws Exception {
        String original = seed();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return refresh.execute(original); });
            var b = executor.submit(() -> { start.await(); logout.execute(original); return true; });
            start.countDown();
            var result = a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
            if (result.accepted()) assertThat(refresh.execute(result.refreshToken()).accepted()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from refresh_sessions where revoked_at is null", Integer.class)).isZero();
        }
    }
    @Test void failedSuccessorInsertionRollsBackConsumption() {
        String original = seed();
        jdbc.execute("alter table refresh_tokens add constraint block_successor check (false) not valid");
        try {
            assertThatThrownBy(() -> refresh.execute(original)).isInstanceOf(RuntimeException.class);
        } finally { jdbc.execute("alter table refresh_tokens drop constraint block_successor"); }
        assertThat(refresh.execute(original).accepted()).isTrue();
    }
}
