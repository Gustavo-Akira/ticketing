package br.com.gustavoakira.ticketing.core.event.infrastructure;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByEventId(UUID eventId);
}
