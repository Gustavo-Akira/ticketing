package br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence;

import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reserved_seats", uniqueConstraints = @UniqueConstraint(
        name = "uk_reserved_seats_reservation_seat", columnNames = {"reservation_id", "seat_id"}))
public class JpaReservedSeatEntity {
    @Id
    private UUID id;
    @Column(name = "seat_id", nullable = false)
    private UUID seatId;
    private BigDecimal price;


    public JpaReservedSeatEntity() {

    }

    public static JpaReservedSeatEntity fromDomain(ReservedSeat domain) {
        JpaReservedSeatEntity entity = new JpaReservedSeatEntity();
        entity.setSeatId(domain.getId());
        entity.id = UuidCreator.getTimeOrderedEpoch();
        entity.setPrice(domain.getPrice());
        return entity;
    }

    public ReservedSeat toDomain() {
        return new ReservedSeat(seatId, price);
    }

    public UUID getSeatId() {
        return seatId;
    }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
