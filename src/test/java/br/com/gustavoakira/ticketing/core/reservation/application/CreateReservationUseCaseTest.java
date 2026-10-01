package br.com.gustavoakira.ticketing.core.reservation.application;

import br.com.gustavoakira.ticketing.core.event.domain.SeatStatus;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateReservationUseCaseTest {
    @Mock SeatRepository seats;
    @Mock ReservationRepository reservations;
    private CreateReservationUseCase useCase;
    private Reservation request;
    private List<UUID> seatIds;

    @BeforeEach
    void setup() {
        useCase = new CreateReservationUseCase(seats, reservations);
        seatIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        request = new Reservation(UUID.randomUUID(), UUID.randomUUID(),
                seatIds.stream().map(id -> new ReservedSeat(id, new BigDecimal("100.00"))).toList(),
                Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void acquiresAllSeatsInTheRequestedEventBeforeSavingAndReturnsPersistedSnapshot() {
        when(seats.updateSeatsStatusWithExpectedStatus(seatIds, request.getEventId(), SeatStatus.RESERVED, SeatStatus.AVAILABLE))
                .thenReturn(2);
        var persisted = Reservation.restore(request.getId(), request.getEventId(), request.getCustomerId(),
                request.getSeats(), request.getStatus(), request.getCreatedAt().plusSeconds(1), request.getExpiresAt());
        when(reservations.createReservation(request)).thenReturn(persisted);

        assertThat(useCase.createReservation(request)).isSameAs(persisted);

        var order = inOrder(seats, reservations);
        order.verify(seats).updateSeatsStatusWithExpectedStatus(seatIds, request.getEventId(), SeatStatus.RESERVED, SeatStatus.AVAILABLE);
        order.verify(reservations).createReservation(request);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void doesNotSaveReservationWhenAnySeatWasNotAcquired(int acquired) {
        when(seats.updateSeatsStatusWithExpectedStatus(seatIds, request.getEventId(), SeatStatus.RESERVED, SeatStatus.AVAILABLE))
                .thenReturn(acquired);

        assertThatThrownBy(() -> useCase.createReservation(request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(reservations);
    }
}
