package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.infrastructure.EventRepository;
import br.com.gustavoakira.ticketing.core.event.infrastructure.SeatRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSeatsUseCase {
    private final SeatRepository seats;
    private final EventRepository events;

    public ListSeatsUseCase(SeatRepository seats, EventRepository events) {
        this.seats = seats;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public SeatPage execute(UUID eventId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be nonnegative and size must be between 1 and 100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset must not exceed " + Integer.MAX_VALUE);
        }
        if (!events.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        var result = seats.findByEventId(eventId, PageRequest.of(page, size, Sort.by("id")));
        return new SeatPage(result.getContent().stream().map(SeatResult::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }
}
