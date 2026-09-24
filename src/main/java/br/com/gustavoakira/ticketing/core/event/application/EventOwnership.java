package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import java.util.UUID;

public final class EventOwnership {
    private EventOwnership() {}

    public static void requireOwner(Event event, UUID actorId) {
        if (actorId == null || !actorId.equals(event.getOwnerId())) {
            throw new EventAccessDeniedException();
        }
    }
}
