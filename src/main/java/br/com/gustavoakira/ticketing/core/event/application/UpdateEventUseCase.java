package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.EventDetails;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateEventUseCase {
    private final EventRepository events;

    public UpdateEventUseCase(EventRepository events) {
        this.events = events;
    }

    @Transactional
    public EventResult execute(UUID id, EventDetails details) {
        if (events.updateDetails(id, details.name(), details.startsAt()) == 0) {
            throw new EventNotFoundException(id);
        }
        return EventResult.from(events.findById(id).orElseThrow(() -> new EventNotFoundException(id)));
    }
}
