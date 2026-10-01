package br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence;

import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservationStatus;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hibernate.generator.EventType.INSERT;

@Entity
@Table(name = "reservations")
public class JpaReservationEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID eventId;
    @Column(nullable = false)
    private UUID customerId;
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(nullable = false, name = "reservation_id")
    private List<JpaReservedSeatEntity> seats;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    @Generated(event = INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at",nullable = false)
    private Instant expiresAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public List<JpaReservedSeatEntity> getSeats() {
        return seats;
    }

    public void setSeats(List<JpaReservedSeatEntity> seats) {
        this.seats = seats;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public static JpaReservationEntity fromDomain(Reservation reservation) {
        JpaReservationEntity reservationJpaEntity = new JpaReservationEntity();
        reservationJpaEntity.setId(reservation.getId());
        reservationJpaEntity.setSeats(reservation.getSeats().stream().map(JpaReservedSeatEntity::fromDomain).toList());
        reservationJpaEntity.setEventId(reservation.getEventId());
        reservationJpaEntity.setCustomerId(reservation.getCustomerId());
        reservationJpaEntity.setCreatedAt(reservation.getCreatedAt());
        reservationJpaEntity.setStatus(reservation.getStatus());
        reservationJpaEntity.setExpiresAt(reservation.getExpiresAt());
        return reservationJpaEntity;
    }

    public Reservation toDomain(){
        List<ReservedSeat> reservedSeats = this.seats.stream().map(JpaReservedSeatEntity::toDomain).toList();
        return Reservation.restore(id,eventId,customerId,reservedSeats,status,createdAt,expiresAt);
    }
}
