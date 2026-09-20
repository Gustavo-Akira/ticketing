package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListEventsUseCase {
    private final EventRepository events;

    public ListEventsUseCase(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventPage execute(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be nonnegative and size must be between 1 and 100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset must not exceed " + Integer.MAX_VALUE);
        }
        var result = events.findAll(page, size);
        return new EventPage(result.content().stream().map(EventResult::from).toList(),
                page, size, result.totalElements(), result.totalPages());
    }
}
