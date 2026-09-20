package br.com.gustavoakira.ticketing.core.event.application;

public class EventNotDraftException extends RuntimeException {
    public EventNotDraftException(String message) {
        super(message);
    }
}
