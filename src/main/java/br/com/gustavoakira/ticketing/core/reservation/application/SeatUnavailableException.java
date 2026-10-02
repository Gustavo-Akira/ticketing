package br.com.gustavoakira.ticketing.core.reservation.application;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
