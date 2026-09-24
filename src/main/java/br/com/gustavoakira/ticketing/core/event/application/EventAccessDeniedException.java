package br.com.gustavoakira.ticketing.core.event.application;

public class EventAccessDeniedException extends RuntimeException {
    public EventAccessDeniedException() {
        super("Only the event owner can manage this event");
    }
}
