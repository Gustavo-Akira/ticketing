package br.com.gustavoakira.ticketing.core.event.domain;

import com.github.f4b6a3.uuid.UuidCreator;
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
public class Seat {
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

    protected Seat() {}

    public Seat(UUID eventId, String section, String row, String number, BigDecimal price, String currency) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.eventId = Fields.required(eventId, "eventId");
        applyDetails(new SeatDetails(section, row, number, price, currency));
        this.status = SeatStatus.AVAILABLE;
    }

    public void updateDetails(SeatDetails details, EventStatus eventStatus) {
        Fields.required(details, "details");
        Fields.required(eventStatus, "eventStatus");
        if (eventStatus != EventStatus.DRAFT
                && (!section.equals(details.section()) || !row.equals(details.row())
                || !number.equals(details.number()))) {
            throw new SeatLocationChangeException();
        }
        applyDetails(details);
    }

    private void applyDetails(SeatDetails details) {
        this.section = details.section();
        this.row = details.row();
        this.number = details.number();
        this.price = details.price();
        this.currency = details.currency();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getSection() { return section; }
    public String getRow() { return row; }
    public String getNumber() { return number; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public SeatStatus getStatus() { return status; }
    public Long getVersion() { return version; }
}
