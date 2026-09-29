package br.com.gustavoakira.ticketing.core.reservation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservedSeatTest {

    @Test
    void shouldCreateReservedSeat() {
        UUID id = UUID.randomUUID();
        BigDecimal price = new BigDecimal("150.00");

        var seat = new ReservedSeat(
                id,
                price
        );

        assertEquals(id, seat.getId());
        assertEquals(price, seat.getPrice());
    }

    @Test
    void shouldRejectNullId() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ReservedSeat(
                        null,
                        new BigDecimal("100.00")
                )
        );

        assertEquals(
                "Reserved Seat ID cannot be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectZeroPrice() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ReservedSeat(
                        UUID.randomUUID(),
                        BigDecimal.ZERO
                )
        );

        assertEquals(
                "Price must be greater than zero",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNegativePrice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReservedSeat(
                        UUID.randomUUID(),
                        new BigDecimal("-0.01")
                )
        );
    }
}