package br.com.gustavoakira.ticketing.core.reservation.application;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String message) {
        super(message);
    }
}
