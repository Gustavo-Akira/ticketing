package br.com.gustavoakira.ticketing.core.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentAttemptTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void createsStartedAttemptWithFutureProcessingDeadline() {
        var paymentId = UUID.randomUUID();
        var provider = provider(null);
        var attempt = new PaymentAttempt(paymentId, 2, provider, CREATED_AT,
                CREATED_AT.plusSeconds(30), null);

        assertNotNull(attempt.getId());
        assertEquals(paymentId, attempt.getPaymentId());
        assertEquals(2, attempt.getAttemptNumber());
        assertEquals(PaymentAttemptStatus.STARTED, attempt.getStatus());
        assertSame(provider, attempt.getProvider());
        assertEquals(CREATED_AT, attempt.getCreatedAt());
        assertEquals(CREATED_AT.plusSeconds(30), attempt.getProcessingUntil());
        assertNull(attempt.getError());
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void restoresAttemptIncludingProviderReferenceAndError(PaymentAttemptStatus status) {
        var id = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var provider = provider("persisted-reference");
        var error = new PaymentAttemptError("Response lost", "TIMEOUT");

        var attempt = PaymentAttempt.restore(id, paymentId, 2, provider, status,
                CREATED_AT, CREATED_AT.plusSeconds(30), error);

        assertEquals(id, attempt.getId());
        assertEquals(paymentId, attempt.getPaymentId());
        assertEquals(2, attempt.getAttemptNumber());
        assertSame(provider, attempt.getProvider());
        assertEquals(status, attempt.getStatus());
        assertEquals(CREATED_AT, attempt.getCreatedAt());
        assertEquals(CREATED_AT.plusSeconds(30), attempt.getProcessingUntil());
        assertEquals("Response lost", attempt.getError().getMessage());
        assertEquals("TIMEOUT", attempt.getError().getCode());
    }

    @Test
    void rejectsDeadlineBeforeCreationOnCreationAndRestoration() {
        var paymentId = UUID.randomUUID();
        var deadline = CREATED_AT.minusNanos(1);
        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> new PaymentAttempt(paymentId, 1, provider(null), CREATED_AT, deadline, null)),
                () -> assertThrows(IllegalStateException.class,
                        () -> PaymentAttempt.restore(UUID.randomUUID(), paymentId, 1, provider(null),
                                PaymentAttemptStatus.STARTED, CREATED_AT, deadline, null)));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentAttemptStatus.class, names = {"STARTED", "UNKNOWN"})
    void completesStartedOrReconciledAttempt(PaymentAttemptStatus status) {
        var attempt = attempt(status, "reference");
        attempt.complete();
        assertEquals(PaymentAttemptStatus.COMPLETED, attempt.getStatus());
    }

    @Test
    void rejectsCompletingAnAlreadyCompletedAttempt() {
        var attempt = attempt(PaymentAttemptStatus.COMPLETED, "reference");
        assertThrows(IllegalStateException.class, attempt::complete);
        assertEquals(PaymentAttemptStatus.COMPLETED, attempt.getStatus());
    }

    @Test
    void recordsTimeoutErrorAfterAttemptStarts() {
        var attempt = attempt(PaymentAttemptStatus.STARTED, "reference");
        attempt.markAsUnknown();
        attempt.setError(new PaymentAttemptError("Response lost", "TIMEOUT"));

        assertEquals(PaymentAttemptStatus.UNKNOWN, attempt.getStatus());
        assertEquals("Response lost", attempt.getError().getMessage());
        assertEquals("TIMEOUT", attempt.getError().getCode());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentAttemptStatus.class, names = {"COMPLETED", "UNKNOWN"})
    void rejectsMarkingNonStartedAttemptUnknown(PaymentAttemptStatus status) {
        var attempt = attempt(status, "reference");
        assertThrows(IllegalStateException.class, attempt::markAsUnknown);
        assertEquals(status, attempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void recordsFirstProviderReferenceWithoutChangingAttemptIdentity(PaymentAttemptStatus status) {
        var attempt = attempt(status, null);
        var id = attempt.getId();
        var response = provider("stripe-reference");

        attempt.setProvider(response);

        assertEquals("stripe-reference", attempt.getProvider().getProviderReference());
        assertEquals(PaymentProviderName.STRIPE, attempt.getProvider().getProviderName());
        assertEquals(id, attempt.getId());
        assertEquals(1, attempt.getAttemptNumber());
        assertEquals(status, attempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void rejectsChangingProviderWithoutMutatingAttempt(PaymentAttemptStatus status) {
        var attempt = attempt(status, "stripe-reference");
        var original = attempt.getProvider();
        var other = new PaymentProvider("stripe-reference", PaymentProviderName.MERCADO_PAGO, Map.of());

        assertThrows(IllegalStateException.class, () -> attempt.setProvider(other));

        assertSame(original, attempt.getProvider());
        assertEquals(status, attempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void rejectsClearingProvider(PaymentAttemptStatus status) {
        var attempt = attempt(status, "stripe-reference");
        var original = attempt.getProvider();

        assertThrows(IllegalArgumentException.class, () -> attempt.setProvider(null));

        assertSame(original, attempt.getProvider());
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void rejectsReplacingRecordedReference(PaymentAttemptStatus status) {
        var attempt = attempt(status, "stripe-reference");
        var original = attempt.getProvider();

        assertThrows(IllegalStateException.class, () -> attempt.setProvider(provider("another-reference")));

        assertSame(original, attempt.getProvider());
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void rejectsClearingRecordedReference(PaymentAttemptStatus status) {
        var attempt = attempt(status, "stripe-reference");
        var original = attempt.getProvider();

        assertThrows(IllegalStateException.class, () -> attempt.setProvider(provider(null)));

        assertSame(original, attempt.getProvider());
    }

    @Test
    void acceptsRepeatedReferenceWithUpdatedMetadata() {
        var attempt = attempt(PaymentAttemptStatus.UNKNOWN, "stripe-reference");
        var response = new PaymentProvider("stripe-reference", PaymentProviderName.STRIPE,
                Map.of("result", "captured"));

        attempt.setProvider(response);
        attempt.setProvider(response);

        assertEquals("stripe-reference", attempt.getProvider().getProviderReference());
        assertEquals(Map.of("result", "captured"), attempt.getProvider().getMetadata());
        assertEquals(PaymentAttemptStatus.UNKNOWN, attempt.getStatus());
    }

    private PaymentAttempt attempt(PaymentAttemptStatus status, String reference) {
        var attempt = new PaymentAttempt(UUID.randomUUID(), 1, provider(reference),
                CREATED_AT, CREATED_AT.plusSeconds(30), null);
        switch (status) {
            case UNKNOWN -> attempt.markAsUnknown();
            case COMPLETED -> attempt.complete();
            case STARTED -> { }
        }
        return attempt;
    }

    private PaymentProvider provider(String reference) {
        return new PaymentProvider(reference, PaymentProviderName.STRIPE, Map.of());
    }
}
