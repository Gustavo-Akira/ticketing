package br.com.gustavoakira.ticketing.core.payment.infrastructure;

import br.com.gustavoakira.ticketing.core.payment.domain.*;
import br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence.JpaPaymentEntity;
import br.com.gustavoakira.ticketing.core.payment.port.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class PaymentPersistenceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentRepository payments;

    private UUID reservationId;
    private UUID customerId;

    @BeforeEach
    void seedReservation() {
        customerId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbc.update("insert into users(id, name, email) values (?, 'Customer', ?)",
                customerId, customerId + "@example.com");
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', statement_timestamp() + interval '1 day', 'AVAILABLE')", eventId);
        jdbc.update("insert into reservations(id, event_id, customer_id, status, expires_at) values (?, ?, ?, 'ON_HOLD', statement_timestamp() + interval '10 minutes')",
                reservationId, eventId, customerId);
    }

    @Test
    void persistsPaymentAndReloadsItsCapturedSnapshot() {
        var payment = payment("first", "123.45", "BRL");
        payment.markAsProcessing(Instant.parse("2026-10-11T12:01:00Z"));
        payment.approve();
        entityManager.persist(JpaPaymentEntity.fromDomain(payment));
        entityManager.flush();
        entityManager.clear();

        var reloaded = entityManager.find(JpaPaymentEntity.class, payment.getId()).toDomain();

        assertThat(reloaded).usingRecursiveComparison().isEqualTo(payment);
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, payment.getId()))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select type from payment where id = ?", String.class, payment.getId()))
                .isEqualTo("CREDIT");
    }

    @Test
    void paymentsForSameReservationKeepIndependentSnapshotsWhenStatusChanges() {
        var first = payment("first", "123.45", "BRL");
        var second = payment("second", "200.00", "USD");
        entityManager.persist(JpaPaymentEntity.fromDomain(first));
        entityManager.persist(JpaPaymentEntity.fromDomain(second));
        entityManager.flush();
        entityManager.clear();

        first.cancel();
        entityManager.merge(JpaPaymentEntity.fromDomain(first));
        entityManager.flush();
        entityManager.clear();

        var restoredFirst = entityManager.find(JpaPaymentEntity.class, first.getId()).toDomain();
        var restoredSecond = entityManager.find(JpaPaymentEntity.class, second.getId()).toDomain();
        assertThat(restoredFirst).usingRecursiveComparison().isEqualTo(first);
        assertThat(restoredSecond).usingRecursiveComparison().isEqualTo(second);
        assertThat(jdbc.queryForObject("select count(*) from payment where reservation_id = ?", Integer.class, reservationId))
                .isEqualTo(2);
    }

    @Test
    void repositorySavesAndFindsPaymentWithAllCapturedFields() {
        var original = payment("repository-save", "123.45", "BRL");

        var saved = payments.savePayment(original);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved).usingRecursiveComparison().isEqualTo(original);
        assertThat(payments.findPaymentById(original.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void repositoryReturnsEmptyForMissingPaymentAndReservation() {
        payments.savePayment(payment("existing", "123.45", "BRL"));
        entityManager.flush();
        entityManager.clear();

        assertThat(payments.findPaymentById(UUID.randomUUID())).isEmpty();
        assertThat(payments.findAllPaymentsByReservationId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void repositoryFindsAllPaymentsOnlyForRequestedReservation() {
        var first = payment("first", "123.45", "BRL");
        var second = payment("second", "200.00", "USD");
        payments.savePayment(first);
        payments.savePayment(second);
        UUID requestedReservationId = reservationId;

        seedReservation();
        var unrelated = payment("unrelated", "50.00", "BRL");
        payments.savePayment(unrelated);
        entityManager.flush();
        entityManager.clear();

        assertThat(payments.findAllPaymentsByReservationId(requestedReservationId))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(first, second);
        assertThat(payments.findAllPaymentsByReservationId(reservationId))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(unrelated);
    }

    @Test
    void repositoryUpdatesExistingPaymentWithoutInsertingAnotherRow() {
        var original = payment("update", "123.45", "BRL");
        payments.savePayment(original);
        entityManager.flush();
        entityManager.clear();
        original.cancel();

        var updated = payments.updatePayment(original);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).usingRecursiveComparison().isEqualTo(original);
        assertThat(payments.findPaymentById(original.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from payment where reservation_id = ?",
                Integer.class, reservationId)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "amount | 0 | ck_payment_amount",
            "amount | -1 | ck_payment_amount",
            "expires_at | created_at | ck_payment_expiry",
            "expires_at | created_at - interval '1 second' | ck_payment_expiry",
            "status | 'INVALID' | ck_payment_status",
            "type | 'INVALID' | ck_payment_type"
    }, delimiter = '|', quoteCharacter = '"')
    void databaseRejectsInvalidPaymentState(String column, String value, String constraint) {
        var payment = payments.savePayment(payment("constraint", "123.45", "BRL"));
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update(
                "update payment set " + column + " = " + value + " where id = ?", payment.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservation_id", "customer_id"})
    void databaseRejectsMissingReservationOrCustomer(String column) {
        var payment = payments.savePayment(payment("foreign-key", "123.45", "BRL"));
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update(
                "update payment set " + column + " = ? where id = ?", UUID.randomUUID(), payment.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("payment_" + column + "_fkey");
    }

    @Test
    void databaseRejectsDuplicateIdempotencyKeyForSameReservation() {
        payments.savePayment(payment("same-key", "123.45", "BRL"));
        entityManager.flush();
        entityManager.clear();
        var duplicate = payment("same-key", "123.45", "BRL");

        assertThatThrownBy(() -> jdbc.update("""
                insert into payment(id, reservation_id, amount, customer_id, currency,
                                    status, type, idempotency_key, expires_at, created_at)
                select ?, reservation_id, amount, customer_id, currency,
                       status, type, idempotency_key, expires_at, created_at
                from payment where reservation_id = ?
                """, duplicate.getId(), reservationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payment_reservation_idempotency");
    }

    @Test
    void repositoryAllowsSameIdempotencyKeyForDifferentReservations() {
        var first = payment("shared-key", "123.45", "BRL");
        payments.savePayment(first);
        seedReservation();
        var second = payment("shared-key", "123.45", "BRL");
        payments.savePayment(second);
        entityManager.flush();
        entityManager.clear();

        assertThat(payments.findPaymentById(first.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(first);
        assertThat(payments.findPaymentById(second.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(second);
    }

    private Payment payment(String key, String amount, String currency) {
        return new Payment(new ReservationSnapshot(reservationId, new BigDecimal(amount), customerId, currency),
                PaymentType.CREDIT, key, Instant.parse("2026-10-11T12:00:00Z"));
    }
}
