package br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence;

import br.com.gustavoakira.ticketing.core.payment.domain.PaymentStatus;
import br.com.gustavoakira.ticketing.core.payment.domain.PaymentType;
import br.com.gustavoakira.ticketing.core.payment.domain.Payment;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class JpaPaymentEntity {
    @Id
    private UUID id;

    @Embedded
    private JpaReservationSnapshotEntity reservationSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static JpaPaymentEntity fromDomain(Payment payment) {
        JpaPaymentEntity entity = new JpaPaymentEntity();
        entity.id = payment.getId();
        entity.reservationSnapshot = JpaReservationSnapshotEntity.fromDomain(payment.getReservationSnapshot());
        entity.status = payment.getStatus();
        entity.type = payment.getType();
        entity.idempotencyKey = payment.getIdempotencyKey();
        entity.expiresAt = payment.getExpiresAt();
        entity.createdAt = payment.getCreatedAt();
        return entity;
    }

    public Payment toDomain() {
        return Payment.restore(id, reservationSnapshot.toDomain(), status, type,
                idempotencyKey, createdAt, expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public JpaReservationSnapshotEntity getReservationSnapshot() {
        return reservationSnapshot;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentType getType() {
        return type;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
