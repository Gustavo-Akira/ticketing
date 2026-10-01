package br.com.gustavoakira.ticketing.core.reservation.port;

import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;

public interface ReservationRepository {
    Reservation createReservation(Reservation reservation);
}
