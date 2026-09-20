package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeEventStatusToAvailableUseCaseTest {
    private static final Instant START = Instant.parse("2027-01-10T20:00:00Z");
    @Mock EventRepository events;
    ChangeEventStatusToAvailableUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new ChangeEventStatusToAvailableUseCase(events);
    }

    @Test
    void makesDraftAvailableUsingItsOriginalStatusAsTheUpdateCondition() {
        var event = new Event("Concert", START);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(events.updateEventStatusWithExpectedStatus(event.getId(), EventStatus.AVAILABLE, EventStatus.DRAFT))
                .thenReturn(1);

        useCase.execute(event.getId());

        verify(events).findById(event.getId());
        verify(events).updateEventStatusWithExpectedStatus(event.getId(), EventStatus.AVAILABLE, EventStatus.DRAFT);
        verifyNoMoreInteractions(events);
    }

    @Test
    void missingEventIsNotUpdated() {
        var id = UUID.randomUUID();
        when(events.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(EventNotFoundException.class);

        verify(events).findById(id);
        verifyNoMoreInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void nonDraftIsRejectedBeforeWriting(EventStatus status) {
        var event = Event.restore(UUID.randomUUID(), "Concert", START, status, START, START);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(event.getId())).isInstanceOf(EventNotDraftException.class);

        verify(events).findById(event.getId());
        verifyNoMoreInteractions(events);
    }

    @Test
    void failedConditionalUpdateReportsConcurrentModification() {
        var event = new Event("Concert", START);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(events.updateEventStatusWithExpectedStatus(event.getId(), EventStatus.AVAILABLE, EventStatus.DRAFT))
                .thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(event.getId()))
                .isInstanceOf(ConcurrentEventModificationException.class);

        verify(events).findById(event.getId());
        verify(events).updateEventStatusWithExpectedStatus(event.getId(), EventStatus.AVAILABLE, EventStatus.DRAFT);
        verifyNoMoreInteractions(events);
    }
}
