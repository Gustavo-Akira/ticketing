package br.com.gustavoakira.ticketing.core.event.infrastructure.persistence;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataSeatRepository extends JpaRepository<SeatJpaEntity, UUID> {
    List<SeatJpaEntity> findByEventId(UUID eventId);
    Page<SeatJpaEntity> findByEventId(UUID eventId, Pageable pageable);
    Optional<SeatJpaEntity> findByIdAndEventId(UUID id, UUID eventId);
    @Query(value = """
    SELECT EXISTS (
        SELECT 1
          FROM seats
         WHERE event_id = :eventId
    )
    """, nativeQuery = true)
    boolean existsAvailableSeatByEventId(UUID eventId);
}
