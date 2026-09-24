package br.com.gustavoakira.ticketing.core.event.infrastructure;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class EventOwnershipMigrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Test void upgradePreservesLegacyAndEnforcesOwnerReference() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .target("5").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.executeUpdate("insert into events(id,name,starts_at) values ('00000000-0000-4000-8000-000000000001','Legacy','2027-01-01T00:00:00Z')");
            sql.executeUpdate("insert into seats(id,event_id,section,seat_row,seat_number,price,currency) values ('00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000001','Floor','A','1',100,'BRL')");
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
            try (var result = sql.executeQuery("select e.id, e.owner_id, s.id seat_id, s.price from events e join seats s on s.event_id=e.id")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("id")).isEqualTo("00000000-0000-4000-8000-000000000001");
                assertThat(result.getObject("owner_id")).isNull();
                assertThat(result.getString("seat_id")).isEqualTo("00000000-0000-4000-8000-000000000002");
                assertThat(result.getBigDecimal("price")).isEqualByComparingTo("100");
                assertThat(result.next()).isFalse();
            }
            assertThatThrownBy(() -> sql.executeUpdate("update events set owner_id='00000000-0000-4000-8000-000000000099'"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23503");
            sql.executeUpdate("insert into users(id,name,email) values ('00000000-0000-4000-8000-000000000099','Owner','owner@example.com')");
            sql.executeUpdate("update events set owner_id='00000000-0000-4000-8000-000000000099'");
            assertThatThrownBy(() -> sql.executeUpdate("delete from users"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23503");
        }
    }
}
