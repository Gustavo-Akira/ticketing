package br.com.gustavoakira.ticketing.core.event.infrastructure;

import br.com.gustavoakira.ticketing.core.event.domain.*;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
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
    void changingDomainSnapshotDoesNotWriteWithoutExplicitSave() {
        var stored = seats.save(seat(event().getId()));
        var snapshot = seats.findById(stored.getId()).orElseThrow();
        snapshot.updateDetails(new SeatDetails("Floor", "A", "15", new BigDecimal("250.00"), "USD"), EventStatus.DRAFT);
        entityManager.flush();
        entityManager.clear();
        var reloaded = seats.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo("120.50");
        assertThat(reloaded.getCurrency()).isEqualTo("BRL");
        assertThat(reloaded.getVersion()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleDomainSnapshotCannotOverwriteANewerUpdate(boolean clearContext) {
        var stored = seats.save(seat(event().getId()));
        var first = seats.findById(stored.getId()).orElseThrow();
        var stale = seats.findById(stored.getId()).orElseThrow();
        first.updateDetails(new SeatDetails("Floor", "A", "15", new BigDecimal("250.00"), "USD"), EventStatus.DRAFT);
        assertThat(seats.save(first).getVersion()).isEqualTo(1L);
        if (clearContext) {
            entityManager.clear();
        }
        stale.updateDetails(new SeatDetails("Floor", "A", "15", new BigDecimal("300.00"), "EUR"), EventStatus.DRAFT);
        assertThatThrownBy(() -> seats.save(stale))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    void migrationsCreateSchemaAndRepositoriesRoundTripAllFields() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
        var event = events.save(new Event("Concert", Instant.parse("2027-01-10T20:00:00Z")));
        var seat = seats.save(new Seat(event.getId(), "Floor", "A", "15", new BigDecimal("120.50"), "BRL"));
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
        assertThat(storedSeat.getCurrency()).isEqualTo("BRL");
        assertThat(storedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(storedSeat.getVersion()).isZero();
    }

    @Test
    void auditTimestampsAreGeneratedOnInsertAndSurviveReload() {
        var event = event();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getUpdatedAt()).isEqualTo(event.getCreatedAt());
        entityManager.clear();
        var stored = events.findById(event.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(event.getCreatedAt());
        assertThat(stored.getUpdatedAt()).isEqualTo(event.getUpdatedAt());
    }

    @Test
    void auditTimeIsUpdatedExplicitlyByTheQuery() {
        var event = event();
        var createdAt = event.getCreatedAt();
        var audit = jdbc.queryForMap("""
                update events set name = ?, updated_at = statement_timestamp() where id = ?
                returning updated_at, statement_timestamp() as statement_time
                """, "Renamed", event.getId());
        assertThat(audit.get("updated_at")).isEqualTo(audit.get("statement_time"));
        var updatedAt = ((java.sql.Timestamp) audit.get("updated_at")).toInstant();
        entityManager.clear();
        var stored = events.findById(event.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Renamed");
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(stored.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void updateWithoutAuditAssignmentHasNoHiddenTimestampSideEffect() {
        var event = event();
        jdbc.update("update events set name = ? where id = ?", "Renamed", event.getId());
        entityManager.clear();
        var stored = events.findById(event.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Renamed");
        assertThat(stored.getCreatedAt()).isEqualTo(event.getCreatedAt());
        assertThat(stored.getUpdatedAt()).isEqualTo(event.getUpdatedAt());
    }

    @Test
    void eventScopedLookupCanUseExistingLeadingColumnIndex() {
        // Tiny fixtures often favor a sequential scan. Disable it only in this
        // transaction to verify index eligibility, not to claim a load benchmark.
        jdbc.execute("set local enable_seqscan = off");
        var plan = jdbc.queryForList("explain (costs off) select * from seats where event_id = ?", String.class, UUID.randomUUID());
        assertThat(String.join("\n", plan)).contains("uk_seats_event_location", "Index Cond:", "event_id");
    }

    @Test
    void postgresUuidOrderingMatchesNewEventCreationOrder() {
        var first = event();
        var second = event();
        assertThat(jdbc.queryForList("select id from events where id in (?, ?) order by id", UUID.class, first.getId(), second.getId()))
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void seatQueryIsScopedToAnEventAndLocationCanRepeatAcrossEvents() {
        var first = event();
        var second = event();
        var firstSeat = seats.save(seat(first.getId()));
        seats.save(seat(second.getId()));
        entityManager.clear();
        assertThat(seats.findByEventId(first.getId())).extracting(Seat::getId).containsExactly(firstSeat.getId());
        assertThat(seats.findByEventId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void duplicateLocationWithinAnEventIsRejected() {
        var event = event();
        seats.save(seat(event.getId()));
        assertThatThrownBy(() -> seats.save(seat(event.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seatMustReferenceAnExistingEvent() {
        assertThatThrownBy(() -> seats.save(seat(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventWithSeatsCannotBeDeleted() {
        var event = event();
        seats.save(seat(event.getId()));
        assertThatThrownBy(() -> jdbc.update("delete from events where id = ?", event.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(EventStatus.class)
    void eventStatusesRoundTripByName(EventStatus status) {
        var event = event();
        jdbc.update("update events set status = ?, updated_at = statement_timestamp() where id = ?", status.name(), event.getId());
        entityManager.clear();
        assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(SeatStatus.class)
    void seatStatusesRoundTripByName(SeatStatus status) {
        var seat = seats.save(seat(event().getId()));
        jdbc.update("update seats set status = ? where id = ?", status.name(), seat.getId());
        entityManager.clear();
        assertThat(seats.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"price = -0.01", "status = 'INVALID'", "section = ''", "seat_row = ' '", "seat_number = ''", "version = -1", "event_id = null", "currency = null", "currency = 'brl'", "currency = 'US'", "currency = '123'"})
    void databaseRejectsInvalidSeatsEvenWhenBypassingDomain(String assignment) {
        var seat = seats.save(seat(event().getId()));
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
        return events.save(new Event("Concert", Instant.parse("2027-01-10T20:00:00Z")));
    }

    private Seat seat(UUID eventId) {
        return new Seat(eventId, "Floor", "A", "15", new BigDecimal("120.50"), "BRL");
    }

    @Test
    void conditionalStatusUpdateChangesOnlyTheMatchingEvent() {
        var target = event();
        jdbc.update("update events set updated_at = '2000-01-01T00:00:00Z' where id = ?", target.getId());
        var other = event();
        assertThat(events.updateEventStatusWithExpectedStatus(target.getId(), EventStatus.AVAILABLE, EventStatus.DRAFT))
                .isEqualTo(1);
        assertThat(events.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.AVAILABLE);
        assertThat(events.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.DRAFT);
        var stored = events.findById(target.getId()).orElseThrow();
        assertThat(stored.getUpdatedAt()).isAfter(Instant.parse("2000-01-01T00:00:00Z"));
        assertThat(stored.getCreatedAt()).isEqualTo(target.getCreatedAt());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void conditionalStatusUpdateCannotOverwriteANewerStatus(EventStatus status) {
        var target = event();
        jdbc.update("update events set status = ? where id = ?", status.name(), target.getId());
        entityManager.clear();
        assertThat(events.updateEventStatusWithExpectedStatus(target.getId(), EventStatus.AVAILABLE, EventStatus.DRAFT))
                .isZero();
        assertThat(events.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @Test
    void conditionalStatusUpdateOfMissingEventReturnsZero() {
        assertThat(events.updateEventStatusWithExpectedStatus(UUID.randomUUID(), EventStatus.AVAILABLE, EventStatus.DRAFT))
                .isZero();
    }
}
