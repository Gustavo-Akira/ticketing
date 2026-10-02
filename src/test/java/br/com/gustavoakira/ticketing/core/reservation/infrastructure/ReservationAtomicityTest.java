package br.com.gustavoakira.ticketing.core.reservation.infrastructure;

import br.com.gustavoakira.ticketing.core.reservation.application.CreateReservationUseCase;
import br.com.gustavoakira.ticketing.core.reservation.application.SeatUnavailableException;
import br.com.gustavoakira.ticketing.core.reservation.application.EventNotAvailableException;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.reservation.application.CreateReservationCommand;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

// No test transaction: calls must commit or roll back through the real service proxy.
@SpringBootTest
@Testcontainers
class ReservationAtomicityTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired CreateReservationUseCase createReservation;
    @Autowired JdbcTemplate jdbc;
    private UUID customerId;
    private UUID eventId;
    private UUID firstSeat;
    private UUID secondSeat;

    @BeforeEach
    void seed() {
        customerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        firstSeat = UUID.randomUUID();
        secondSeat = UUID.randomUUID();
        jdbc.update("insert into users(id, name, email) values (?, 'Customer', ?)", customerId, customerId + "@example.com");
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', statement_timestamp() + interval '1 day', 'AVAILABLE')", eventId);
        insertSeat(firstSeat, eventId, "1");
        insertSeat(secondSeat, eventId, "2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVED", "SOLD"})
    void conflictRollsBackAvailableSeatsAndTheirVersions(String unavailableStatus) {
        jdbc.update("update seats set status = ? where id = ?", unavailableStatus, secondSeat);
        var request = reservation(customerId, List.of(firstSeat, secondSeat));

        assertThatThrownBy(() -> createReservation.createReservation(request)).isInstanceOf(SeatUnavailableException.class);

        assertSeat(firstSeat, "AVAILABLE", 0L);
        assertSeat(secondSeat, unavailableStatus, 0L);
        assertNoReservation(request);
    }

    @Test
    void seatFromAnotherEventRollsBackTheEntireRequest() {
        var otherEvent = UUID.randomUUID();
        jdbc.update("insert into events(id, name, starts_at) values (?, 'Other concert', statement_timestamp() + interval '1 day')", otherEvent);
        jdbc.update("update seats set event_id = ? where id = ?", otherEvent, secondSeat);
        var request = reservation(customerId, List.of(firstSeat, secondSeat));

        assertThatThrownBy(() -> createReservation.createReservation(request)).isInstanceOf(SeatUnavailableException.class);

        assertSeat(firstSeat, "AVAILABLE", 0L);
        assertSeat(secondSeat, "AVAILABLE", 0L);
        assertNoReservation(request);
    }

    @Test
    void missingSeatRollsBackExistingSeats() {
        var request = reservation(customerId, List.of(firstSeat, UUID.randomUUID()));

        assertThatThrownBy(() -> createReservation.createReservation(request)).isInstanceOf(SeatUnavailableException.class);

        assertSeat(firstSeat, "AVAILABLE", 0L);
        assertNoReservation(request);
    }

    @Test
    void persistenceFailureRollsBackSeatAcquisition() {
        var request = reservation(UUID.randomUUID(), List.of(firstSeat, secondSeat));

        assertThatThrownBy(() -> createReservation.createReservation(request)).isInstanceOf(DataIntegrityViolationException.class);

        assertSeat(firstSeat, "AVAILABLE", 0L);
        assertSeat(secondSeat, "AVAILABLE", 0L);
        assertNoReservation(request);
    }

    @Test
    void concurrentRequestsCommitExactlyOneCompleteReservation() throws Exception {
        var first = reservation(customerId, List.of(firstSeat, secondSeat));
        var second = reservation(customerId, List.of(firstSeat, secondSeat));
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> attempt(first, start));
            var b = executor.submit(() -> attempt(second, start));
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertSeat(firstSeat, "RESERVED", 1L);
        assertSeat(secondSeat, "RESERVED", 1L);
        assertThat(jdbc.queryForObject("select count(*) from reservations where event_id = ?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reserved_seats where seat_id in (?, ?)", Integer.class, firstSeat, secondSeat)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct reservation_id) from reserved_seats where seat_id in (?, ?)", Integer.class, firstSeat, secondSeat)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void unavailableEventLeavesSeatsAndReservationsUnchanged(EventStatus status) {
        jdbc.update("update events set status = ? where id = ?", status.name(), eventId);
        var request = reservation(customerId, List.of(firstSeat, secondSeat));

        assertThatThrownBy(() -> createReservation.createReservation(request))
                .isInstanceOf(EventNotAvailableException.class);

        assertSeat(firstSeat, "AVAILABLE", 0L);
        assertSeat(secondSeat, "AVAILABLE", 0L);
        assertNoReservation(request);
    }

    private boolean attempt(CreateReservationCommand request, CyclicBarrier start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            createReservation.createReservation(request);
            return true;
        } catch (SeatUnavailableException conflict) {
            assertThat(conflict).hasMessage("Cannot create reservation one of seats is already taken");
            return false;
        }
    }

    private CreateReservationCommand reservation(UUID customer, List<UUID> ids) {
        return new CreateReservationCommand(eventId, customer, ids);
    }

    private void insertSeat(UUID id, UUID event, String number) {
        jdbc.update("insert into seats(id, event_id, section, seat_row, seat_number, price, currency) values (?, ?, 'Floor', 'A', ?, 100, 'BRL')", id, event, number);
    }

    private void assertSeat(UUID id, String status, long version) {
        var row = jdbc.queryForMap("select status, version from seats where id = ?", id);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(version);
    }

    private void assertNoReservation(CreateReservationCommand request) {
        assertThat(jdbc.queryForObject("select count(*) from reservations where event_id = ?", Integer.class, request.eventId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reserved_seats where seat_id in (?, ?)",
                Integer.class, firstSeat, secondSeat)).isZero();
    }
}
