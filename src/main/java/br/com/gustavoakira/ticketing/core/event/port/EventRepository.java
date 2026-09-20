package br.com.gustavoakira.ticketing.core.event.port;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {
    /** Saves and returns the persisted snapshot, including generated audit timestamps. */
    Event save(Event event);
    Optional<Event> findById(UUID id);
    boolean existsById(UUID id);
    PageResult<Event> findAll(int page, int size);
    int updateDetails(UUID id, String name, Instant startsAt);
    /** Holds the event lock until the caller's transaction ends. */
    Optional<Event> getEventByIdForUpdate(UUID eventId);
    int updateEventStatusWithExpectedStatus(UUID eventId, EventStatus newStatus, EventStatus expectedStatus);
}