package br.com.gustavoakira.ticketing.core.payment.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationSnapshotTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.01", "150.00"})
    void preservesReservationDetailsForPositiveAmounts(String value) {
        var reservationId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var amount = new BigDecimal(value);

        var snapshot = new ReservationSnapshot(reservationId, amount, customerId, "BRL");

        assertEquals(reservationId, snapshot.getId());
        assertEquals(amount, snapshot.getAmount());
        assertEquals(customerId, snapshot.getCustomerId());
        assertEquals("BRL", snapshot.getCurrency());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-150.00"})
    void rejectsNonPositivePaymentAmounts(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> new ReservationSnapshot(UUID.randomUUID(), new BigDecimal(value),
                        UUID.randomUUID(), "BRL"));
    }
}
