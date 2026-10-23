package br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence;

import br.com.gustavoakira.ticketing.core.payment.domain.Payment;
import br.com.gustavoakira.ticketing.core.payment.port.PaymentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataJpaPaymentRepository springDataJpaPaymentRepository;

    public JpaPaymentRepository(SpringDataJpaPaymentRepository springDataJpaPaymentRepository) {
        this.springDataJpaPaymentRepository = springDataJpaPaymentRepository;
    }

    @Override
    public Payment savePayment(Payment payment) {
        return this.springDataJpaPaymentRepository.save(JpaPaymentEntity.fromDomain(payment)).toDomain();
    }

    @Override
    public Optional<Payment> findPaymentById(UUID id) {
        return springDataJpaPaymentRepository.findById(id).map(JpaPaymentEntity::toDomain);
    }

    @Override
    public List<Payment> findAllPaymentsByReservationId(UUID reservationId) {
        return springDataJpaPaymentRepository.findAllByReservationSnapshotId(reservationId).stream().map(JpaPaymentEntity::toDomain).toList();
    }

    @Override
    public Payment updatePayment(Payment payment) {
        return springDataJpaPaymentRepository.save(JpaPaymentEntity.fromDomain(payment)).toDomain();
    }
}
