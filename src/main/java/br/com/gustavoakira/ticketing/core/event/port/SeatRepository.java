package br.com.gustavoakira.ticketing.core.event.port;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository {
    /** Saves using the snapshot's version and returns the persisted snapshot. */
    Seat save(Seat seat);
    void saveAll(List<Seat> seats);
    Optional<Seat> findById(UUID id);
    List<Seat> findByEventId(UUID eventId);
    PageResult<Seat> findByEventId(UUID eventId, int page, int size);
    Optional<Seat> findByIdAndEventId(UUID id, UUID eventId);
    boolean existsByEventId(UUID eventId);
}