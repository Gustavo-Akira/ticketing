package br.com.gustavoakira.ticketing.core.reservation.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.event.domain.SeatStatus;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
@Service
public class CreateReservationUseCase {
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    public CreateReservationUseCase(SeatRepository seatRepository, ReservationRepository reservationRepository, EventRepository eventRepository) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
    }
    @Transactional(rollbackFor = Exception.class)
    public Reservation createReservation(CreateReservationCommand reservationCommand){

        if(eventRepository.findById(reservationCommand.eventId()).orElseThrow().getStatus() != EventStatus.AVAILABLE){
            throw new EventNotAvailableException("Event "+reservationCommand.eventId()+" is not available");
        }
        int modified = seatRepository.updateSeatsStatusWithExpectedStatus(reservationCommand.seatIds(), reservationCommand.eventId(),SeatStatus.RESERVED, SeatStatus.AVAILABLE);
        if(modified != reservationCommand.seatIds().size()){
            throw new SeatUnavailableException("Cannot create reservation one of seats is already taken");
        }
        List<ReservedSeat> reservedSeats =seatRepository.findAllByIds(reservationCommand.seatIds()).stream().map(
                seat->new ReservedSeat(seat.getId(),seat.getPrice(), seat.getCurrency())
        ).toList();
        Reservation reservation = new Reservation(reservationCommand.eventId(),reservationCommand.customerId(),reservedSeats, Instant.now());
        return reservationRepository.createReservation(reservation);
    }
}
