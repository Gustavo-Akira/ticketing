package br.com.gustavoakira.ticketing.core.reservation.presentation.dto;

import br.com.gustavoakira.ticketing.core.reservation.application.CreateReservationCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull
        UUID eventId,
        @NotEmpty
        Set<@NotNull UUID> seatIds
) {
        public CreateReservationCommand toCommand(UUID customerId){
                return new CreateReservationCommand(eventId,customerId,seatIds.stream().toList());
        }
}
