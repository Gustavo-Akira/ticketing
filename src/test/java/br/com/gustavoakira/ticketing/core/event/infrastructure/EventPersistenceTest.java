package br.com.gustavoakira.ticketing.core.event.infrastructure;

import br.com.gustavoakira.ticketing.core.event.domain.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class EventPersistenceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired EventRepository events;
    @Autowired SeatRepository seats;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsCreateSchemaAndRepositoriesRoundTripAllFields() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
        var event = events.saveAndFlush(new Event("Concert", Instant.parse("2027-01-10T20:00:00Z")));
        var seat = seats.saveAndFlush(new Seat(event.getId(), "Floor", "A", "15", new BigDecimal("120.50")));
        entityManager.clear();

        var storedEvent = events.findById(event.getId()).orElseThrow();
        assertThat(storedEvent.getName()).isEqualTo("Concert");
        assertThat(storedEvent.getStartsAt()).isEqualTo(Instant.parse("2027-01-10T20:00:00Z"));
        assertThat(storedEvent.getStatus()).isEqualTo(EventStatus.DRAFT);
        var storedSeat = seats.findById(seat.getId()).orElseThrow();
        assertThat(storedSeat.getEventId()).isEqualTo(event.getId());
        assertThat(storedSeat.getSection()).isEqualTo("Floor");
        assertThat(storedSeat.getRow()).isEqualTo("A");
        assertThat(storedSeat.getNumber()).isEqualTo("15");
        assertThat(storedSeat.getPrice()).isEqualByComparingTo("120.50");
        assertThat(storedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(storedSeat.getVersion()).isZero();
    }

    @Test
    void seatQueryIsScopedToAnEventAndLocationCanRepeatAcrossEvents() {
        var first = event();
        var second = event();
        var firstSeat = seats.saveAndFlush(seat(first.getId()));
        seats.saveAndFlush(seat(second.getId()));
        entityManager.clear();
        assertThat(seats.findByEventId(first.getId())).extracting(Seat::getId).containsExactly(firstSeat.getId());
        assertThat(seats.findByEventId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void duplicateLocationWithinAnEventIsRejected() {
        var event = event();
        seats.saveAndFlush(seat(event.getId()));
        assertThatThrownBy(() -> seats.saveAndFlush(seat(event.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seatMustReferenceAnExistingEvent() {
        assertThatThrownBy(() -> seats.saveAndFlush(seat(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventWithSeatsCannotBeDeleted() {
        var event = event();
        seats.saveAndFlush(seat(event.getId()));
        assertThatThrownBy(() -> jdbc.update("delete from events where id = ?", event.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(EventStatus.class)
    void eventStatusesRoundTripByName(EventStatus status) {
        var event = event();
        jdbc.update("update events set status = ? where id = ?", status.name(), event.getId());
        entityManager.clear();
        assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(SeatStatus.class)
    void seatStatusesRoundTripByName(SeatStatus status) {
        var seat = seats.saveAndFlush(seat(event().getId()));
        jdbc.update("update seats set status = ? where id = ?", status.name(), seat.getId());
        entityManager.clear();
        assertThat(seats.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"price = -0.01", "status = 'INVALID'", "section = ''", "seat_row = ' '", "seat_number = ''", "version = -1", "event_id = null"})
    void databaseRejectsInvalidSeatsEvenWhenBypassingDomain(String assignment) {
        var seat = seats.saveAndFlush(seat(event().getId()));
        assertThatThrownBy(() -> jdbc.update("update seats set " + assignment + " where id = ?", seat.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status = 'INVALID'", "name = ''", "name = ' '", "starts_at = null"})
    void databaseRejectsInvalidEventsEvenWhenBypassingDomain(String assignment) {
        var event = event();
        assertThatThrownBy(() -> jdbc.update("update events set " + assignment + " where id = ?", event.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Event event() {
        return events.saveAndFlush(new Event("Concert", Instant.parse("2027-01-10T20:00:00Z")));
    }

    private Seat seat(UUID eventId) {
        return new Seat(eventId, "Floor", "A", "15", new BigDecimal("120.50"));
    }
}
