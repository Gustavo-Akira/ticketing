package br.com.gustavoakira.ticketing.core.event.infrastructure;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByEventId(UUID eventId);
    Page<Seat> findByEventId(UUID eventId, Pageable pageable);
    Optional<Seat> findByIdAndEventId(UUID id, UUID eventId);
}
