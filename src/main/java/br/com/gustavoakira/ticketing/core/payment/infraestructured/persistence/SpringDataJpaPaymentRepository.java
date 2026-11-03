package br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataJpaPaymentRepository extends JpaRepository<JpaPaymentEntity, UUID> {
    List<JpaPaymentEntity> findAllByReservationSnapshotId(UUID reservationId);
    @Query(value = "SELECT e FROM  JpaPaymentEntity e WHERE e.reservationSnapshot.id = :reservationId AND e.idempotencyKey=:idempotencyKey")
    Optional<JpaPaymentEntity> findByIdempotencyKeyAndReservationId(String idempotencyKey,UUID reservationId);
}
