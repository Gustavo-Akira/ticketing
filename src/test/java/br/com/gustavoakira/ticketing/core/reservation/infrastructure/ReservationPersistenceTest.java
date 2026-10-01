package br.com.gustavoakira.ticketing.core.reservation.infrastructure;

import br.com.gustavoakira.ticketing.core.reservation.application.CreateReservationUseCase;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence.SpringDataJpaReservationRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
@Transactional
class ReservationPersistenceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired CreateReservationUseCase createReservation;
    @Autowired SpringDataJpaReservationRepository reservations;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private UUID customerId;
    private UUID eventId;
    private UUID seatId;

    @BeforeEach
    void seed() {
        customerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        jdbc.update("insert into users(id, name, email) values (?, 'Customer', ?)",
                customerId, customerId + "@example.com");
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', statement_timestamp() + interval '1 day', 'AVAILABLE')", eventId);
        jdbc.update("insert into seats(id, event_id, section, seat_row, seat_number, price, currency) values (?, ?, 'Floor', 'A', '1', 100, 'BRL')", seatId, eventId);
    }

    @Test
    void persistsReservationFieldsAndGeneratesVersionSevenSeatIdentity() {
        var request = reservation("100.00");
        var saved = createReservation.createReservation(request);
        entityManager.clear();

        var entity = reservations.findById(saved.getId()).orElseThrow();
        var reloaded = entity.toDomain();
        assertThat(entity.getId()).isEqualTo(request.getId());
        assertThat(entity.getEventId()).isEqualTo(eventId);
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getStatus()).isEqualTo(request.getStatus());
        assertThat(entity.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        // PostgreSQL timestamps have microsecond precision; Java Instant also holds nanoseconds.
        assertThat(entity.getExpiresAt()).isCloseTo(saved.getExpiresAt(), within(1, ChronoUnit.MICROS));
        assertThat(reloaded.getExpiresAt()).isCloseTo(request.getExpiresAt(), within(1, ChronoUnit.MICROS));
        assertThat(entity.getSeats()).hasSize(1);
        assertThat(entity.getSeats().getFirst().getSeatId()).isEqualTo(seatId);
        assertThat(entity.getSeats().getFirst().getPrice()).isEqualByComparingTo("100.00");
        var snapshotId = jdbc.queryForObject("select id from reserved_seats where reservation_id = ?", UUID.class, saved.getId());
        assertThat(snapshotId).isNotEqualTo(seatId);
        assertThat(snapshotId.version()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select status from seats where id = ?", String.class, seatId)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select version from seats where id = ?", Long.class, seatId)).isEqualTo(1L);
    }

    @Test
    void reservingReleasedSeatPreservesPreviousReservationAndPrice() {
        var first = createReservation.createReservation(reservation("100.00"));
        // Simulate the release performed by a future cancellation/expiration flow.
        jdbc.update("update reservations set status = 'CANCELLED' where id = ?", first.getId());
        jdbc.update("update seats set status = 'AVAILABLE', version = version + 1, price = 150 where id = ?", seatId);
        entityManager.clear();

        var second = createReservation.createReservation(reservation("150.00"));
        entityManager.clear();

        var historical = reservations.findById(first.getId()).orElseThrow().toDomain();
        var current = reservations.findById(second.getId()).orElseThrow().toDomain();
        assertThat(historical.getSeats()).hasSize(1);
        assertThat(current.getSeats()).hasSize(1);
        assertThat(historical.getSeats().getFirst().getId()).isEqualTo(seatId);
        assertThat(current.getSeats().getFirst().getId()).isEqualTo(seatId);
        assertThat(historical.getSeats().getFirst().getPrice()).isEqualByComparingTo("100.00");
        assertThat(current.getSeats().getFirst().getPrice()).isEqualByComparingTo("150.00");
        assertThat(jdbc.queryForObject("select count(*) from reserved_seats where seat_id = ?", Integer.class, seatId)).isEqualTo(2);
    }

    @Test
    void databaseRejectsDuplicateSeatWithinSameReservation() {
        var saved = createReservation.createReservation(reservation("100.00"));

        assertThatThrownBy(() -> jdbc.update(
                "insert into reserved_seats(id, reservation_id, seat_id, price) values (?, ?, ?, 100)",
                UUID.randomUUID(), saved.getId(), seatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_reserved_seats_reservation_seat");
    }

    private Reservation reservation(String price) {
        return new Reservation(eventId, customerId,
                List.of(new ReservedSeat(seatId, new BigDecimal(price))), Instant.now());
    }
}
