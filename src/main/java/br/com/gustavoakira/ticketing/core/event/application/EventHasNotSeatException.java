package br.com.gustavoakira.ticketing.core.event.application;

import java.util.UUID;

public class EventHasNotSeatException extends RuntimeException {
    public EventHasNotSeatException(UUID eventId) {
        super("Event with id " + eventId + " has not seat");
    }
}
