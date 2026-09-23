package br.com.gustavoakira.ticketing.core.identity.infrastructure;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class IdentityMigrationUpgradeTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Test void upgradesV4AndEnforcesNewConstraintsWithoutInventingCredentials() throws Exception {
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway.target("4").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            String id = "00000000-0000-4000-8000-000000000001";
            sql.executeUpdate("insert into users(id, name, email) values ('" + id + "', 'Legacy', 'legacy@example.com')");
            sql.executeUpdate("insert into user_roles(user_id, role) values ('" + id + "', 'CUSTOMER')");
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
            try (var result = sql.executeQuery("select name, email from users where id = '" + id + "'")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString("email")).isEqualTo("legacy@example.com");
            }
            try (var result = sql.executeQuery("select count(*) from user_credentials")) { result.next(); assertThat(result.getInt(1)).isZero(); }
            assertThatThrownBy(() -> sql.executeUpdate("insert into user_credentials(user_id, password_hash) values (gen_random_uuid(), 'hash')"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23503");
            assertThatThrownBy(() -> sql.executeUpdate("insert into user_credentials(user_id, password_hash) values ('" + id + "', null)"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23502");
            sql.executeUpdate("insert into refresh_sessions(id, user_id, expires_at) values ('" + id + "', '" + id + "', statement_timestamp() + interval '7 days')");
            sql.executeUpdate("insert into refresh_tokens(id, session_id, token_hash) values ('" + id + "', '" + id + "', '" + "a".repeat(64) + "')");
            assertThatThrownBy(() -> sql.executeUpdate("insert into refresh_tokens(id, session_id, token_hash) values (gen_random_uuid(), '" + id + "', '" + "a".repeat(64) + "')"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
            assertThatThrownBy(() -> sql.executeUpdate("insert into refresh_tokens(id, session_id, token_hash) values (gen_random_uuid(), '" + id + "', 'raw-token')"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23514");
        }
    }
}
