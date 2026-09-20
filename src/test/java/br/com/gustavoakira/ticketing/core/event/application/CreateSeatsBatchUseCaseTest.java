package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import br.com.gustavoakira.ticketing.core.event.infrastructure.EventRepository;
import br.com.gustavoakira.ticketing.core.event.infrastructure.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSeatsBatchUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private Event event;

    private CreateSeatsBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateSeatsBatchUseCase(
                eventRepository,
                seatRepository
        );
    }

    @Test
    void shouldCreateSeatsBatchWhenEventIsDraft() {
        UUID eventId = UUID.randomUUID();

        SeatBatchCreationCommand command = new SeatBatchCreationCommand(
                List.of(
                        new SeatBatchCreationCommand.SeatSectionConfiguration(
                                "VIP",
                                2,
                                3,
                                new BigDecimal("350.00"),
                                "BRL"
                        )
                )
        );

        when(eventRepository.getEventByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        when(event.getStatus())
                .thenReturn(EventStatus.DRAFT);

        useCase.execute(eventId, command);

        ArgumentCaptor<List<Seat>> seatsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(seatRepository).saveAll(seatsCaptor.capture());

        List<Seat> seats = seatsCaptor.getValue();

        assertEquals(6, seats.size());

        verify(eventRepository).getEventByIdForUpdate(eventId);
        verifyNoMoreInteractions(seatRepository);
    }

    @Test
    void shouldCreateSeatsForMultipleSections() {
        UUID eventId = UUID.randomUUID();

        SeatBatchCreationCommand command = new SeatBatchCreationCommand(
                List.of(
                        new SeatBatchCreationCommand.SeatSectionConfiguration(
                                "VIP",
                                2,
                                3,
                                new BigDecimal("350.00"),
                                "BRL"
                        ),
                        new SeatBatchCreationCommand.SeatSectionConfiguration(
                                "PREMIUM",
                                3,
                                4,
                                new BigDecimal("200.00"),
                                "BRL"
                        )
                )
        );

        when(eventRepository.getEventByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        when(event.getStatus())
                .thenReturn(EventStatus.DRAFT);

        useCase.execute(eventId, command);

        ArgumentCaptor<List<Seat>> seatsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(seatRepository).saveAll(seatsCaptor.capture());

        List<Seat> seats = seatsCaptor.getValue();

        assertEquals(18, seats.size());
    }

    @Test
    void shouldNotCreateSeatsWhenEventIsNotDraft() {
        UUID eventId = UUID.randomUUID();

        SeatBatchCreationCommand command = new SeatBatchCreationCommand(
                List.of(
                        new SeatBatchCreationCommand.SeatSectionConfiguration(
                                "VIP",
                                2,
                                3,
                                new BigDecimal("350.00"),
                                "BRL"
                        )
                )
        );

        when(eventRepository.getEventByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        when(event.getStatus())
                .thenReturn(EventStatus.AVAILABLE);

        EventNotDraftException exception = assertThrows(
                EventNotDraftException.class,
                () -> useCase.execute(eventId, command)
        );

        assertEquals(
                "Event status must be DRAFT to update/create seats",
                exception.getMessage()
        );

        verify(eventRepository).getEventByIdForUpdate(eventId);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void shouldRejectSectionWithMoreThan26Rows() {
        UUID eventId = UUID.randomUUID();

        SeatBatchCreationCommand command = new SeatBatchCreationCommand(
                List.of(
                        new SeatBatchCreationCommand.SeatSectionConfiguration(
                                "VIP",
                                27,
                                10,
                                new BigDecimal("350.00"),
                                "BRL"
                        )
                )
        );

        when(eventRepository.getEventByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        when(event.getStatus())
                .thenReturn(EventStatus.DRAFT);

        InvalidSeatConfigurationException exception = assertThrows(
                InvalidSeatConfigurationException.class,
                () -> useCase.execute(eventId, command)
        );

        assertEquals(
                "A section cannot contain more than 26 rows",
                exception.getMessage()
        );

        verify(eventRepository).getEventByIdForUpdate(eventId);
        verifyNoInteractions(seatRepository);
    }
}