package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.infrastructure.SeatRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSeatUseCase {
    private final SeatRepository seats;

    public GetSeatUseCase(SeatRepository seats) {
        this.seats = seats;
    }

    @Transactional(readOnly = true)
    public SeatResult execute(UUID eventId, UUID id) {
        return SeatResult.from(seats.findByIdAndEventId(id, eventId)
                .orElseThrow(() -> new SeatNotFoundException(id)));
    }
}
