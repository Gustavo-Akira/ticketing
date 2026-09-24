package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.*;
import br.com.gustavoakira.ticketing.core.event.port.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipUseCasesTest {
    final EventRepository events = mock(EventRepository.class);
    final SeatRepository seats = mock(SeatRepository.class);
    static final Instant START = Instant.parse("2027-01-10T20:00:00Z");
    final UUID owner = UUID.randomUUID();
    final UUID id = UUID.randomUUID();

    @ParameterizedTest @ValueSource(strings = {"update", "publish", "batch", "seat"})
    void deniedBeforeOtherBusinessRulesOrWrites(String operation) {
        for (UUID eventOwner : Arrays.asList(owner, null)) {
            reset(events, seats);
            Event event = Event.restore(id, "Concert", START, EventStatus.AVAILABLE, START, START, eventOwner);
            if (operation.equals("batch")) when(events.getEventByIdForUpdate(id)).thenReturn(Optional.of(event));
            else when(events.findById(id)).thenReturn(Optional.of(event));
            assertThatThrownBy(() -> execute(operation, UUID.randomUUID())).isInstanceOf(EventAccessDeniedException.class);
            if (operation.equals("batch")) verify(events).getEventByIdForUpdate(id);
            else verify(events).findById(id);
            verifyNoMoreInteractions(events);
            verifyNoInteractions(seats);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"update", "publish", "batch", "seat"})
    void absentEventCannotBeWritten(String operation) {
        assertThatThrownBy(() -> execute(operation, owner)).isInstanceOf(EventNotFoundException.class);
        verifyNoInteractions(seats);
    }

    @Test void creationPassesOwnerToPersistenceAndRejectsMissingActor() {
        when(events.save(any())).thenAnswer(call -> call.getArgument(0));
        var useCase = new CreateEventUseCase(events);
        var result = useCase.execute(new EventDetails("Concert", START), owner);
        assertThat(result.ownerId()).isEqualTo(owner);
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(new EventDetails("Concert", START), null));
    }

    @Test void updateDetectsEventRemovedAfterAuthorization() {
        when(events.findById(id)).thenReturn(Optional.of(new Event("Concert", START, owner)));
        assertThatThrownBy(() -> execute("update", owner)).isInstanceOf(EventNotFoundException.class);
    }

    private void execute(String operation, UUID actor) {
        switch (operation) {
            case "update" -> new UpdateEventUseCase(events).execute(id, new EventDetails("Changed", START), actor);
            case "publish" -> new ChangeEventStatusToAvailableUseCase(events, seats).execute(id, actor);
            case "batch" -> new CreateSeatsBatchUseCase(events, seats).execute(id,
                new SeatBatchCreationCommand(List.of(new SeatBatchCreationCommand.SeatSectionConfiguration("VIP", 1, 1, BigDecimal.TEN, "BRL"))), actor);
            case "seat" -> new UpdateSeatUseCase(seats, events).execute(id, UUID.randomUUID(),
                new SeatDetails("Floor", "A", "1", BigDecimal.TEN, "BRL"), 0L, actor);
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
