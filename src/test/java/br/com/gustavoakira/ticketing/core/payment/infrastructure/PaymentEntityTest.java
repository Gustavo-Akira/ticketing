package br.com.gustavoakira.ticketing.core.payment.infrastructure;

import br.com.gustavoakira.ticketing.core.payment.domain.*;
import br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence.JpaPaymentEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityTest {
    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void roundTripPreservesPersistedStateAndCreatesIndependentSnapshot(PaymentStatus status) {
        var snapshot = new ReservationSnapshot(UUID.randomUUID(), new BigDecimal("123.45"), UUID.randomUUID(), "BRL");
        var payment = Payment.restore(UUID.randomUUID(), snapshot, status, PaymentType.DEBIT,
                "checkout-123", Instant.parse("2026-10-11T12:00:00Z"), Instant.parse("2026-10-11T12:07:00Z"));

        var entity = JpaPaymentEntity.fromDomain(payment);
        var restored = entity.toDomain();

        assertThat(restored).usingRecursiveComparison().isEqualTo(payment);
        assertThat(restored).isNotSameAs(payment);
        assertThat(restored.getReservationSnapshot()).isNotSameAs(snapshot);
    }
}
