package br.com.gustavoakira.ticketing.core.event.infrastructure.persistence;


import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataEventRepository extends JpaRepository<EventJpaEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE events SET name = :name, starts_at = :startsAt,
                updated_at = statement_timestamp()
            WHERE id = :id
            """, nativeQuery = true)
    int updateDetails(@Param("id") UUID id, @Param("name") String name,
                      @Param("startsAt") Instant startsAt);

    @Query(value = """
        SELECT *
        FROM events
        WHERE id = :eventId
        FOR UPDATE;
    """, nativeQuery = true)
    Optional<EventJpaEntity> getEventByIdForUpdate(@Param("eventId") UUID eventId);
}
