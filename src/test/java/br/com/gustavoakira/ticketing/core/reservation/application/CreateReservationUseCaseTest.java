package br.com.gustavoakira.ticketing.core.reservation.application;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.event.domain.SeatStatus;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservationStatus;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateReservationUseCaseTest {
    @Mock SeatRepository seats;
    @Mock ReservationRepository reservations;
    @Mock
    EventRepository eventRepository;
    private CreateReservationUseCase useCase;
    private CreateReservationCommand request;
    private List<UUID> seatIds;

    @BeforeEach
    void setup() {
        useCase = new CreateReservationUseCase(seats, reservations, eventRepository);
        seatIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        request = new CreateReservationCommand(UUID.randomUUID(), UUID.randomUUID(), seatIds);
    }

    @Test
    void acquiresAllSeatsInTheRequestedEventBeforeSavingAndReturnsPersistedSnapshot() {
        givenEventStatus(EventStatus.AVAILABLE);
        when(seats.updateSeatsStatusWithExpectedStatus(seatIds, request.eventId(), SeatStatus.RESERVED, SeatStatus.AVAILABLE))
                .thenReturn(2);
        var first = Seat.restore(seatIds.getFirst(), request.eventId(), "Floor", "A", "1",
                new BigDecimal("100.00"), "BRL", SeatStatus.RESERVED, 1L);
        var second = Seat.restore(seatIds.getLast(), request.eventId(), "Floor", "A", "2",
                new BigDecimal("150.00"), "USD", SeatStatus.RESERVED, 1L);
        when(seats.findAllByIds(seatIds)).thenReturn(List.of(second, first));
        var persisted = new Reservation(request.eventId(), request.customerId(),
                List.of(new ReservedSeat(first.getId(), first.getPrice(), first.getCurrency()),
                        new ReservedSeat(second.getId(), second.getPrice(), second.getCurrency())), Instant.now());
        when(reservations.createReservation(any(Reservation.class))).thenReturn(persisted);

        var before = Instant.now();
        assertThat(useCase.createReservation(request)).isSameAs(persisted);
        var after = Instant.now();

        var order = inOrder(eventRepository, seats, reservations);
        order.verify(eventRepository).findById(request.eventId());
        order.verify(seats).updateSeatsStatusWithExpectedStatus(seatIds, request.eventId(), SeatStatus.RESERVED, SeatStatus.AVAILABLE);
        order.verify(seats).findAllByIds(seatIds);
        var captured = ArgumentCaptor.forClass(Reservation.class);
        order.verify(reservations).createReservation(captured.capture());
        var created = captured.getValue();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getEventId()).isEqualTo(request.eventId());
        assertThat(created.getCustomerId()).isEqualTo(request.customerId());
        assertThat(created.getStatus()).isEqualTo(ReservationStatus.ON_HOLD);
        assertThat(created.getCreatedAt()).isBetween(before, after);
        assertThat(created.getExpiresAt()).isEqualTo(created.getCreatedAt().plusSeconds(600));
        assertThat(created.getSeats()).extracting(ReservedSeat::getId, ReservedSeat::getPrice, ReservedSeat::getCurrency)
                .containsExactlyInAnyOrder(tuple(first.getId(), first.getPrice(), first.getCurrency()),
                        tuple(second.getId(), second.getPrice(), second.getCurrency()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void doesNotSaveReservationWhenAnySeatWasNotAcquired(int acquired) {
        givenEventStatus(EventStatus.AVAILABLE);
        when(seats.updateSeatsStatusWithExpectedStatus(seatIds, request.eventId(), SeatStatus.RESERVED, SeatStatus.AVAILABLE))
                .thenReturn(acquired);

        assertThatThrownBy(() -> useCase.createReservation(request))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessage("Cannot create reservation one of seats is already taken");
        verify(seats, never()).findAllByIds(anyList());
        verifyNoInteractions(reservations);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsUnavailableEventBeforeAcquiringSeats(EventStatus status) {
        givenEventStatus(status);

        assertThatThrownBy(() -> useCase.createReservation(request))
                .isInstanceOf(EventNotAvailableException.class)
                .hasMessage("Event " + request.eventId() + " is not available");

        verifyNoInteractions(seats, reservations);
    }

    @Test
    void rejectsMissingEventBeforeAcquiringSeats() {
        when(eventRepository.findById(request.eventId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.createReservation(request)).isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(seats, reservations);
    }

    private void givenEventStatus(EventStatus status) {
        var now = Instant.now();
        var event = Event.restore(request.eventId(), "Concert", now.plusSeconds(86400), status,
                now, now, UUID.randomUUID());
        when(eventRepository.findById(request.eventId())).thenReturn(Optional.of(event));
    }
}
