package br.com.gustavoakira.ticketing.core.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-10T12:10:00Z");
    private static final ReservationSnapshot SNAPSHOT = new ReservationSnapshot(
            UUID.randomUUID(), new BigDecimal("150.00"), UUID.randomUUID(), "BRL");

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    void createsPendingPaymentWithTenMinuteWindow(PaymentType type) {
        var payment = new Payment(SNAPSHOT, type, "checkout-1", CREATED_AT);

        assertNotNull(payment.getId());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertSame(SNAPSHOT, payment.getReservationSnapshot());
        assertEquals(type, payment.getType());
        assertEquals("checkout-1", payment.getIdempotencyKey());
        assertEquals(CREATED_AT, payment.getCreatedAt());
        assertEquals(EXPIRES_AT, payment.getExpiresAt());
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void restoresPersistedStateWithoutResettingExpiration(PaymentStatus status) {
        var id = UUID.randomUUID();
        var persistedExpiry = CREATED_AT.plusSeconds(90);

        var payment = Payment.restore(id, SNAPSHOT, status, PaymentType.DEBIT,
                "persisted-key", CREATED_AT, persistedExpiry);

        assertEquals(id, payment.getId());
        assertEquals(status, payment.getStatus());
        assertSame(SNAPSHOT, payment.getReservationSnapshot());
        assertEquals(PaymentType.DEBIT, payment.getType());
        assertEquals("persisted-key", payment.getIdempotencyKey());
        assertEquals(CREATED_AT, payment.getCreatedAt());
        assertEquals(persistedExpiry, payment.getExpiresAt());
    }

    @Test
    void rejectsMissingSnapshotOnCreationAndRestoration() {
        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> new Payment(null, PaymentType.CREDIT, "key", CREATED_AT)),
                () -> assertThrows(IllegalStateException.class,
                        () -> Payment.restore(UUID.randomUUID(), null, PaymentStatus.PENDING,
                                PaymentType.CREDIT, "key", CREATED_AT, EXPIRES_AT)));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void rejectsRestorationWithNonPositivePaymentWindow(long offset) {
        assertThrows(IllegalStateException.class,
                () -> Payment.restore(UUID.randomUUID(), SNAPSHOT, PaymentStatus.PENDING,
                        PaymentType.CREDIT, "key", CREATED_AT, CREATED_AT.plusNanos(offset)));
    }

    @Test
    void cancelsPendingPayment() {
        var payment = payment(PaymentStatus.PENDING);
        payment.cancel();
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsCancellationOutsidePending(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, payment::cancel);
        assertEquals(status, payment.getStatus());
    }

    @Test
    void startsProcessingImmediatelyBeforeExpiration() {
        var payment = payment(PaymentStatus.PENDING);
        payment.markAsProcessing(EXPIRES_AT.minusNanos(1));
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void rejectsStartingPaymentAtOrAfterExpiration(long offset) {
        var payment = payment(PaymentStatus.PENDING);
        assertThrows(IllegalStateException.class,
                () -> payment.markAsProcessing(EXPIRES_AT.plusNanos(offset)));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsStartingPaymentOutsidePending(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, () -> payment.markAsProcessing(CREATED_AT));
        assertEquals(status, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PROCESSING", "UNKNOWN"})
    void approvesProcessingOrReconciledPayment(PaymentStatus status) {
        var payment = payment(status);
        payment.approve();
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PROCESSING", "UNKNOWN"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsApprovalWithoutAnUnresolvedPayment(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, payment::approve);
        assertEquals(status, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PROCESSING", "UNKNOWN"})
    void rejectsProcessingOrReconciledPayment(PaymentStatus status) {
        var payment = payment(status);
        payment.reject();
        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PROCESSING", "UNKNOWN"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsDeclineWithoutAnUnresolvedPayment(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, payment::reject);
        assertEquals(status, payment.getStatus());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void expiresPendingPaymentAtOrAfterDeadline(long offset) {
        var payment = payment(PaymentStatus.PENDING);
        payment.expire(EXPIRES_AT.plusNanos(offset));
        assertEquals(PaymentStatus.EXPIRED, payment.getStatus());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void processingTimeoutRemainsUnknownUntilReconciled(long offset) {
        var payment = new Payment(SNAPSHOT, PaymentType.CREDIT, "key", CREATED_AT);
        payment.markAsProcessing(CREATED_AT);

        payment.expire(EXPIRES_AT.plusNanos(offset));

        assertEquals(PaymentStatus.UNKNOWN, payment.getStatus());
        payment.approve();
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"})
    void rejectsExpirationBeforeDeadline(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, () -> payment.expire(EXPIRES_AT.minusNanos(1)));
        assertEquals(status, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "PROCESSING"}, mode = EnumSource.Mode.EXCLUDE)
    void expirationCannotOverwriteResolvedOrUnknownState(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, () -> payment.expire(EXPIRES_AT));
        assertEquals(status, payment.getStatus());
    }

    @Test
    void marksProcessingPaymentUnknownAfterAmbiguousResponse() {
        var payment = payment(PaymentStatus.PROCESSING);
        payment.markAsUnknown();
        assertEquals(PaymentStatus.UNKNOWN, payment.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "PROCESSING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsUnknownTransitionOutsideProcessing(PaymentStatus status) {
        var payment = payment(status);
        assertThrows(IllegalStateException.class, payment::markAsUnknown);
        assertEquals(status, payment.getStatus());
    }

    private Payment payment(PaymentStatus status) {
        return Payment.restore(UUID.randomUUID(), SNAPSHOT, status,
                PaymentType.CREDIT, "key", CREATED_AT, EXPIRES_AT);
    }
}
