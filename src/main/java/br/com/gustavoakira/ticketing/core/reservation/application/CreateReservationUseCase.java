package br.com.gustavoakira.ticketing.core.reservation.application;

import br.com.gustavoakira.ticketing.core.event.domain.SeatStatus;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
@Service
public class CreateReservationUseCase {
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    public CreateReservationUseCase(SeatRepository seatRepository, ReservationRepository reservationRepository) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
    }
    @Transactional(rollbackFor = Exception.class)
    public Reservation createReservation(Reservation reservation){
        List<UUID> seatsIds = reservation.getSeats().stream().map(ReservedSeat::getId).toList();
        int modified = seatRepository.updateSeatsStatusWithExpectedStatus(seatsIds, reservation.getEventId(),SeatStatus.RESERVED, SeatStatus.AVAILABLE);
        if(modified != seatsIds.size()){
            throw new IllegalStateException("Cannot create reservation one of seats is already taken");
        }

        return reservationRepository.createReservation(reservation);
    }
}
