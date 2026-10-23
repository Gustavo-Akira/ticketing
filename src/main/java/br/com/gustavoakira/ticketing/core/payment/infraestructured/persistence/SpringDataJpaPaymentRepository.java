package br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataJpaPaymentRepository extends JpaRepository<JpaPaymentEntity, UUID> {
    List<JpaPaymentEntity> findAllByReservationSnapshotId(UUID reservationId);
}
