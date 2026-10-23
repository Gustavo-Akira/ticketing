package br.com.gustavoakira.ticketing.core.payment.port;

import br.com.gustavoakira.ticketing.core.payment.domain.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment savePayment(Payment payment);
    Optional<Payment> findPaymentById(UUID id);
    List<Payment> findAllPaymentsByReservationId(UUID reservationId);
    Payment updatePayment(Payment payment);
}
