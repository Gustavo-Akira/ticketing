package br.com.gustavoakira.ticketing.core.payment.application;

import br.com.gustavoakira.ticketing.core.payment.domain.Payment;
import br.com.gustavoakira.ticketing.core.payment.domain.ReservationSnapshot;
import br.com.gustavoakira.ticketing.core.payment.port.PaymentRepository;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservationStatus;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class CreatePaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    public CreatePaymentUseCase(PaymentRepository paymentRepository, ReservationRepository reservationRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
    }
    @Transactional(rollbackFor = Exception.class)
    public Payment execute(CreatePaymentCommand createPaymentCommand){
        Optional<Payment> alreadySavedPayment = paymentRepository.findPaymentByIdempotencyKeyAndReservationId(createPaymentCommand.idempotencyKey(),createPaymentCommand.reservationId());
        if(alreadySavedPayment.isPresent()){
            return alreadySavedPayment.get();
        }
        Reservation reservation = reservationRepository.findReservationById(createPaymentCommand.reservationId()).orElseThrow(()->new ReservationNotFoundException("Reservation Not Found with id "+ createPaymentCommand.reservationId()));
        BigDecimal price = reservation.getSeats().stream().map(ReservedSeat::getPrice).reduce(BigDecimal::add).orElseThrow();
        ReservationSnapshot snapshot = new ReservationSnapshot(reservation.getId(),price,reservation.getCustomerId(),reservation.getSeats().getFirst().getCurrency());
        try {
            reservation.startPayment(Instant.now());
        }catch (IllegalStateException e){
            Optional<Payment> payment = paymentRepository.findPaymentByIdempotencyKeyAndReservationId(createPaymentCommand.idempotencyKey(),createPaymentCommand.reservationId());
            if(payment.isPresent()){
                return payment.get();
            }else{
                throw e;
            }
        }
        Payment payment = new Payment(snapshot, createPaymentCommand.paymentType(), createPaymentCommand.idempotencyKey(), Instant.now());
        int updatedReservation = reservationRepository.updateExpectedStatusToNewStatus(createPaymentCommand.reservationId(), ReservationStatus.ON_HOLD,ReservationStatus.PAYMENT_PROCESSING);
        if(updatedReservation == 0){
            return paymentRepository.findPaymentByIdempotencyKeyAndReservationId(createPaymentCommand.idempotencyKey(), createPaymentCommand.reservationId()).orElseThrow(()->new ReservationNotFoundException("Reservation Not Found with id "+ createPaymentCommand.reservationId()));
        }
        return paymentRepository.savePayment(payment);
    }
}
