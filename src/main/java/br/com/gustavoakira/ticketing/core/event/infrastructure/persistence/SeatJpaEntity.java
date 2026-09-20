package br.com.gustavoakira.ticketing.core.event.infrastructure.persistence;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import br.com.gustavoakira.ticketing.core.event.domain.SeatStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "seats")
public class SeatJpaEntity {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String section;

    @Column(name = "seat_row", nullable = false, length = 50)
    private String row;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String number;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    protected SeatJpaEntity() {}

    static SeatJpaEntity fromDomain(Seat model) {
        var entity = new SeatJpaEntity();
        entity.id = model.getId();
        entity.eventId = model.getEventId();
        entity.section = model.getSection();
        entity.row = model.getRow();
        entity.number = model.getNumber();
        entity.price = model.getPrice();
        entity.currency = model.getCurrency();
        entity.status = model.getStatus();
        entity.version = model.getVersion();
        return entity;
    }

    Seat toDomain() {
        return Seat.restore(id, eventId, section, row, number, price, currency, status, version);
    }
}
