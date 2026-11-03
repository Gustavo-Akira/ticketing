package br.com.gustavoakira.ticketing.core.reservation.port;

import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservationStatus;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Reservation createReservation(Reservation reservation);
    int updateExpectedStatusToNewStatus(UUID id,ReservationStatus expectedStatus, ReservationStatus newStatus);
    Optional<Reservation> findReservationById(UUID id);
}
