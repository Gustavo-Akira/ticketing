package br.com.gustavoakira.ticketing.core.payment.infrastructure;

import br.com.gustavoakira.ticketing.core.payment.application.CreatePaymentCommand;
import br.com.gustavoakira.ticketing.core.payment.application.CreatePaymentUseCase;
import br.com.gustavoakira.ticketing.core.payment.domain.Payment;
import br.com.gustavoakira.ticketing.core.payment.domain.PaymentType;
import br.com.gustavoakira.ticketing.core.payment.port.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// No test transaction: observe actual service commits and rollbacks.
@SpringBootTest
@Testcontainers
class CreatePaymentIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired CreatePaymentUseCase createPayment;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    private UUID reservationId;
    private UUID customerId;

    @BeforeEach
    void seed() {
        customerId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        jdbc.update("insert into users(id, name, email) values (?, 'Customer', ?)",
                customerId, customerId + "@example.com");
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', statement_timestamp() + interval '1 day', 'AVAILABLE')", eventId);
        jdbc.update("insert into reservations(id, event_id, customer_id, status, expires_at) values (?, ?, ?, 'ON_HOLD', statement_timestamp() + interval '10 minutes')",
                reservationId, eventId, customerId);
        insertSeat(eventId, "1", "100.25");
        insertSeat(eventId, "2", "50.50");
    }

    @Test
    void commitsPaymentSnapshotAndReservationTransitionTogether() {
        var result = createPayment.execute(command("checkout"));

        assertThat(payments.findPaymentById(result.getId())).isPresent();
        var row = jdbc.queryForMap("select amount, customer_id, currency, status, type, idempotency_key from payment where id = ?", result.getId());
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("150.75");
        assertThat(row).containsEntry("customer_id", customerId).containsEntry("currency", "BRL")
                .containsEntry("status", "PENDING").containsEntry("type", "CREDIT")
                .containsEntry("idempotency_key", "checkout");
        assertReservationStatus("PAYMENT_PROCESSING");
        assertPaymentCount(1);
    }

    @Test
    void retryReturnsSamePaymentAfterReservationHasStartedProcessing() {
        var original = createPayment.execute(command("checkout"));

        var retry = createPayment.execute(command("checkout"));

        assertSamePayment(retry, original);
        assertPaymentCount(1);
        assertReservationStatus("PAYMENT_PROCESSING");
    }

    @Test
    void failedPaymentInsertRollsBackReservationTransition() {
        // The database's 255-character limit fails during persistence, after the status update.
        assertThatThrownBy(() -> createPayment.execute(command("x".repeat(256))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertPaymentCount(0);
        assertReservationStatus("ON_HOLD");
        assertThat(createPayment.execute(command("valid-retry"))).isNotNull();
        assertPaymentCount(1);
    }

    @Test
    void expiredReservationCannotCreatePayment() {
        jdbc.update("update reservations set created_at = statement_timestamp() - interval '20 minutes', expires_at = statement_timestamp() - interval '10 minutes' where id = ?", reservationId);

        assertThatThrownBy(() -> createPayment.execute(command("checkout")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Expired reservation");

        assertPaymentCount(0);
        assertReservationStatus("ON_HOLD");
    }

    @Test
    void differentKeyCannotCreateAnotherPaymentWhileReservationIsProcessing() {
        var first = createPayment.execute(command("first"));

        assertThatThrownBy(() -> createPayment.execute(command("second")))
                .isInstanceOf(IllegalStateException.class);

        assertPaymentCount(1);
        assertThat(payments.findPaymentByIdempotencyKeyAndReservationId("first", reservationId))
                .get().extracting(Payment::getId).isEqualTo(first.getId());
        assertReservationStatus("PAYMENT_PROCESSING");
    }

    @Test
    void concurrentIdenticalRequestsReturnOneCommittedPayment() throws Exception {
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return createPayment.execute(command("concurrent"));
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return createPayment.execute(command("concurrent"));
            });
            var firstResult = first.get(30, TimeUnit.SECONDS);
            var secondResult = second.get(30, TimeUnit.SECONDS);
            assertSamePayment(secondResult, firstResult);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertPaymentCount(1);
        assertReservationStatus("PAYMENT_PROCESSING");
    }

    private CreatePaymentCommand command(String key) {
        return new CreatePaymentCommand(reservationId, key, PaymentType.CREDIT);
    }

    private void assertSamePayment(Payment actual, Payment expected) {
        assertThat(actual).usingRecursiveComparison().ignoringFields("createdAt", "expiresAt")
                .isEqualTo(expected);
        // PostgreSQL stores timestamps at microsecond precision; Instant can retain nanoseconds.
        assertThat(actual.getCreatedAt()).isCloseTo(expected.getCreatedAt(), within(1, ChronoUnit.MICROS));
        assertThat(actual.getExpiresAt()).isCloseTo(expected.getExpiresAt(), within(1, ChronoUnit.MICROS));
    }

    private void insertSeat(UUID eventId, String number, String price) {
        var seatId = UUID.randomUUID();
        jdbc.update("insert into seats(id, event_id, section, seat_row, seat_number, price, currency, status) values (?, ?, 'Floor', 'A', ?, ?, 'BRL', 'RESERVED')",
                seatId, eventId, number, new BigDecimal(price));
        jdbc.update("insert into reserved_seats(id, seat_id, price, currency, reservation_id) values (?, ?, ?, 'BRL', ?)",
                UUID.randomUUID(), seatId, new BigDecimal(price), reservationId);
    }

    private void assertReservationStatus(String expected) {
        assertThat(jdbc.queryForObject("select status from reservations where id = ?", String.class, reservationId))
                .isEqualTo(expected);
    }

    private void assertPaymentCount(int expected) {
        assertThat(jdbc.queryForObject("select count(*) from payment where reservation_id = ?", Integer.class, reservationId))
                .isEqualTo(expected);
    }
}
