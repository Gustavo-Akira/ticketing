package br.com.gustavoakira.ticketing.core.event.infrastructure;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class EventMigrationUpgradeTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Test
    void upgradesExistingV1DataWithoutChangingIdsAndRequiresCurrencyForNewSeats() throws SQLException {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("1").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into events (id, name, starts_at)
                    values ('00000000-0000-4000-8000-000000000001', 'Existing event', '2027-01-10T20:00:00Z')
                    """);
            statement.executeUpdate("""
                    insert into seats (id, event_id, section, seat_row, seat_number, price)
                    values ('00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001', 'Floor', 'A', '15', 120.50)
                    """);

            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .load().migrate();

            try (var result = statement.executeQuery("""
                    select e.id, e.created_at, e.updated_at, s.currency, s.price
                    from events e join seats s on s.event_id = e.id
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("id")).isEqualTo("00000000-0000-4000-8000-000000000001");
                assertThat(result.getTimestamp("created_at")).isNotNull();
                assertThat(result.getTimestamp("updated_at")).isEqualTo(result.getTimestamp("created_at"));
                assertThat(result.getString("currency")).isEqualTo("BRL");
                assertThat(result.getBigDecimal("price")).isEqualByComparingTo("120.50");
            }

            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into seats (id, event_id, section, seat_row, seat_number, price)
                    values ('00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000001', 'Floor', 'A', '16', 120.50)
                    """)).isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23502");
        }
    }
}
