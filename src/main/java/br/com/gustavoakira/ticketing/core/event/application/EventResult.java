package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import java.time.Instant;
import java.util.UUID;

public record EventResult(UUID id, String name, Instant startsAt, EventStatus status,
                          Instant createdAt, Instant updatedAt, UUID ownerId) {
    static EventResult from(Event event) {
        return new EventResult(event.getId(), event.getName(), event.getStartsAt(),
                event.getStatus(), event.getCreatedAt(), event.getUpdatedAt(), event.getOwnerId());
    }
}
