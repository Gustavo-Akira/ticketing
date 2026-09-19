package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.SeatDetails;
import br.com.gustavoakira.ticketing.core.event.infrastructure.EventRepository;
import br.com.gustavoakira.ticketing.core.event.infrastructure.SeatRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateSeatUseCase {
    private final SeatRepository seats;
    private final EventRepository events;

    public UpdateSeatUseCase(SeatRepository seats, EventRepository events) {
        this.seats = seats;
        this.events = events;
    }

    @Transactional
    public SeatResult execute(UUID eventId, UUID id, SeatDetails details) {
        // Keep the event status stable until the seat update commits.
        var event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        var seat = seats.findByIdAndEventId(id, eventId)
                .orElseThrow(() -> new SeatNotFoundException(id));
        seat.updateDetails(details, event.getStatus());
        return SeatResult.from(seats.saveAndFlush(seat));
    }
}
