package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventDetails;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class CreateEventUseCase {
    private final EventRepository events;

    public CreateEventUseCase(EventRepository events) {
        this.events = events;
    }

    @Transactional
    public EventResult execute(EventDetails details, UUID actorId) {
        return EventResult.from(events.save(new Event(details.name(), details.startsAt(), actorId)));
    }
}
