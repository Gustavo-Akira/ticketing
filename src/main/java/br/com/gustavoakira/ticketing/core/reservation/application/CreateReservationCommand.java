package br.com.gustavoakira.ticketing.core.reservation.application;

import java.util.List;
import java.util.UUID;

public record CreateReservationCommand(
        UUID eventId,
        UUID customerId,
        List<UUID> seatIds
) {
}
