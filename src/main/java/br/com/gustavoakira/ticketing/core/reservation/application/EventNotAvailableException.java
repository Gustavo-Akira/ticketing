package br.com.gustavoakira.ticketing.core.reservation.application;

public class EventNotAvailableException extends RuntimeException {
    public EventNotAvailableException(String message) {
        super(message);
    }
}
