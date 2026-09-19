package br.com.gustavoakira.ticketing.core.event.infrastructure;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE events SET name = :name, starts_at = :startsAt,
                updated_at = statement_timestamp()
            WHERE id = :id
            """, nativeQuery = true)
    int updateDetails(@Param("id") UUID id, @Param("name") String name,
                      @Param("startsAt") Instant startsAt);
}
