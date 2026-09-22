package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.SeatDetails;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
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
    public SeatResult execute(UUID eventId, UUID id, SeatDetails details, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be a non-negative integer");
        }
        var event = events.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        var seat = seats.findByIdAndEventId(id, eventId)
                .orElseThrow(() -> new SeatNotFoundException(id));
        if (!expectedVersion.equals(seat.getVersion())) {
            throw new ConcurrentSeatModificationException();
        }
        seat.updateDetails(details, event.getStatus());
        return SeatResult.from(seats.save(seat));
    }
}
