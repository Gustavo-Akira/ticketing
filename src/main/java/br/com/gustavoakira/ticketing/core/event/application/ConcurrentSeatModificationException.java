package br.com.gustavoakira.ticketing.core.event.application;

public class ConcurrentSeatModificationException extends RuntimeException {
    public ConcurrentSeatModificationException() {
        super("Seat was modified by another request; reload it before updating");
    }
}
