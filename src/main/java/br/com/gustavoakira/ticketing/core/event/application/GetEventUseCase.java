package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.infrastructure.EventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetEventUseCase {
    private final EventRepository events;

    public GetEventUseCase(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventResult execute(UUID id) {
        return EventResult.from(events.findById(id).orElseThrow(() -> new EventNotFoundException(id)));
    }
}
