package br.com.gustavoakira.ticketing.core.event.domain;

public class SeatLocationChangeException extends RuntimeException {
    public SeatLocationChangeException() {
        super("Seat section, row and number can only change while the event is DRAFT");
    }
}
