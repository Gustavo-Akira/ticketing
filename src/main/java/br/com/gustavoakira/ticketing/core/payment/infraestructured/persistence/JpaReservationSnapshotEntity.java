package br.com.gustavoakira.ticketing.core.payment.infraestructured.persistence;

import br.com.gustavoakira.ticketing.core.payment.domain.ReservationSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class JpaReservationSnapshotEntity {
    @Column(name = "reservation_id", nullable = false)
    private UUID id;
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, length = 4)
    private String currency;

    public static JpaReservationSnapshotEntity fromDomain(ReservationSnapshot snapshot) {
        JpaReservationSnapshotEntity entity = new JpaReservationSnapshotEntity();
        entity.id = snapshot.getId();
        entity.amount = snapshot.getAmount();
        entity.customerId = snapshot.getCustomerId();
        entity.currency = snapshot.getCurrency();
        return entity;
    }

    public ReservationSnapshot toDomain() {
        return new ReservationSnapshot(id, amount, customerId, currency);
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }
}
