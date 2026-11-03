package br.com.gustavoakira.ticketing.core.payment.application;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
