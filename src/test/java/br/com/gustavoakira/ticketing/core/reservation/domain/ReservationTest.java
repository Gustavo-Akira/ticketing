package br.com.gustavoakira.ticketing.core.reservation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-27T18:00:00Z");

    @Test
    void shouldCreateReservationOnHold() {
        var seats = List.of(
                seat(new BigDecimal("100.00")),
                seat(new BigDecimal("150.00"))
        );

        var reservation = new Reservation(
                EVENT_ID,
                CUSTOMER_ID,
                seats,
                CREATED_AT
        );

        assertNotNull(reservation.getId());
        assertEquals(EVENT_ID, reservation.getEventId());
        assertEquals(CUSTOMER_ID, reservation.getCustomerId());
        assertEquals(ReservationStatus.ON_HOLD, reservation.getStatus());

        assertEquals(CREATED_AT, reservation.getCreatedAt());
        assertEquals(
                CREATED_AT.plusSeconds(10 * 60),
                reservation.getExpiresAt()
        );

        assertEquals(seats, reservation.getSeats());
    }

    @Test
    void shouldRejectReservationWithoutSeats() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Reservation(
                        EVENT_ID,
                        CUSTOMER_ID,
                        List.of(),
                        CREATED_AT
                )
        );
    }

    @Test
    void shouldRejectDuplicatedSeats() {
        UUID seatId = UUID.randomUUID();

        var seats = List.of(
                new ReservedSeat(seatId, new BigDecimal("100.00")),
                new ReservedSeat(seatId, new BigDecimal("100.00"))
        );

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Reservation(
                        EVENT_ID,
                        CUSTOMER_ID,
                        seats,
                        CREATED_AT
                )
        );

        assertEquals(
                "Reservation cannot contain duplicated seats",
                exception.getMessage()
        );
    }

    @Test
    void shouldRestoreReservation() {
        UUID reservationId = UUID.randomUUID();

        var seats = List.of(
                seat(new BigDecimal("100.00"))
        );

        Instant expiresAt = CREATED_AT.plusSeconds(600);

        var reservation = Reservation.restore(
                reservationId,
                EVENT_ID,
                CUSTOMER_ID,
                seats,
                ReservationStatus.CONFIRMED,
                CREATED_AT,
                expiresAt
        );

        assertEquals(reservationId, reservation.getId());
        assertEquals(EVENT_ID, reservation.getEventId());
        assertEquals(CUSTOMER_ID, reservation.getCustomerId());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(CREATED_AT, reservation.getCreatedAt());
        assertEquals(expiresAt, reservation.getExpiresAt());
    }

    @Test
    void shouldRejectRestoreWhenExpiresAtIsNotAfterCreatedAt() {
        var seats = List.of(
                seat(new BigDecimal("100.00"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Reservation.restore(
                        UUID.randomUUID(),
                        EVENT_ID,
                        CUSTOMER_ID,
                        seats,
                        ReservationStatus.ON_HOLD,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    @Test
    void shouldExpireReservationAfterExpirationTime() {
        var reservation = reservation();

        reservation.expire(
                reservation.getExpiresAt()
        );

        assertEquals(
                ReservationStatus.EXPIRED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotExpireReservationBeforeExpirationTime() {
        var reservation = reservation();

        var exception = assertThrows(
                IllegalStateException.class,
                () -> reservation.expire(
                        reservation.getExpiresAt().minusSeconds(1)
                )
        );

        assertEquals(
                "Could not expire reservation because expiration time is not over",
                exception.getMessage()
        );

        assertEquals(
                ReservationStatus.ON_HOLD,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotExpireReservationWhenStatusIsNotOnHold() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        assertThrows(
                IllegalStateException.class,
                () -> reservation.expire(
                        reservation.getExpiresAt()
                )
        );

        assertEquals(
                ReservationStatus.PAYMENT_PROCESSING,
                reservation.getStatus()
        );
    }

    @Test
    void shouldStartPaymentBeforeExpiration() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        assertEquals(
                ReservationStatus.PAYMENT_PROCESSING,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotStartPaymentAfterExpiration() {
        var reservation = reservation();

        var exception = assertThrows(
                IllegalStateException.class,
                () -> reservation.startPayment(
                        reservation.getExpiresAt()
                )
        );

        assertEquals(
                "Expired reservation cannot start payment",
                exception.getMessage()
        );

        assertEquals(
                ReservationStatus.ON_HOLD,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotStartPaymentFromInvalidStatus() {
        var reservation = reservation();

        reservation.cancel();

        assertThrows(
                IllegalStateException.class,
                () -> reservation.startPayment(
                        CREATED_AT.plusSeconds(60)
                )
        );

        assertEquals(
                ReservationStatus.CANCELLED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldCancelReservationOnHold() {
        var reservation = reservation();

        reservation.cancel();

        assertEquals(
                ReservationStatus.CANCELLED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldCancelReservationWhilePaymentIsProcessing() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        reservation.cancel();

        assertEquals(
                ReservationStatus.CANCELLED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotCancelConfirmedReservation() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        reservation.confirm();

        assertThrows(
                IllegalStateException.class,
                reservation::cancel
        );

        assertEquals(
                ReservationStatus.CONFIRMED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldConfirmReservationWhenPaymentIsProcessing() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        reservation.confirm();

        assertEquals(
                ReservationStatus.CONFIRMED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotConfirmReservationOnHold() {
        var reservation = reservation();

        assertThrows(
                IllegalStateException.class,
                reservation::confirm
        );

        assertEquals(
                ReservationStatus.ON_HOLD,
                reservation.getStatus()
        );
    }

    @Test
    void shouldReturnToOnHoldWhenPaymentIsReleasedBeforeExpiration() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        reservation.releasePayment(
                CREATED_AT.plusSeconds(120)
        );

        assertEquals(
                ReservationStatus.ON_HOLD,
                reservation.getStatus()
        );
    }

    @Test
    void shouldExpireWhenPaymentIsReleasedAfterExpiration() {
        var reservation = reservation();

        reservation.startPayment(
                CREATED_AT.plusSeconds(60)
        );

        reservation.releasePayment(
                reservation.getExpiresAt()
        );

        assertEquals(
                ReservationStatus.EXPIRED,
                reservation.getStatus()
        );
    }

    @Test
    void shouldNotReleasePaymentWhenPaymentIsNotProcessing() {
        var reservation = reservation();

        assertThrows(
                IllegalStateException.class,
                () -> reservation.releasePayment(
                        CREATED_AT.plusSeconds(60)
                )
        );

        assertEquals(
                ReservationStatus.ON_HOLD,
                reservation.getStatus()
        );
    }

    @Test
    void shouldExposeImmutableSeatsList() {
        var reservation = reservation();

        assertThrows(
                UnsupportedOperationException.class,
                () -> reservation.getSeats().clear()
        );
    }

    private Reservation reservation() {
        return new Reservation(
                EVENT_ID,
                CUSTOMER_ID,
                List.of(
                        seat(new BigDecimal("100.00")),
                        seat(new BigDecimal("150.00"))
                ),
                CREATED_AT
        );
    }

    private ReservedSeat seat(BigDecimal price) {
        return new ReservedSeat(
                UUID.randomUUID(),
                price
        );
    }
}