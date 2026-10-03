package br.com.gustavoakira.ticketing.core.reservation.presentation.dto;

import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        UUID customerId,
        List<ReservedSeatResponse> seats,
        Instant expiresAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getCustomerId(),
                reservation.getSeats().stream().map(ReservedSeatResponse::from).toList(),
                reservation.getExpiresAt());
    }
    public record ReservedSeatResponse(
            UUID seatId,
            BigDecimal price,
            String currency
    ){
        public static  ReservedSeatResponse from(ReservedSeat reservedSeat) {
            return new ReservedSeatResponse(reservedSeat.getId(), reservedSeat.getPrice(), reservedSeat.getCurrency());
        }
    }
}
