package br.com.gustavoakira.ticketing.core.event.application;

import java.util.UUID;

public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(UUID id) {
        super("Seat not found: " + id);
    }
}
