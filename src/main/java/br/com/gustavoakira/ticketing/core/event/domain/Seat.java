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
import java.math.RoundingMode;
import java.util.UUID;
import java.util.Currency;

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
        this.section = Fields.text(section, "section", 100);
        this.row = Fields.text(row, "row", 50);
        this.number = Fields.text(number, "number", 20);
        this.price = validatedPrice(price);
        this.currency = Currency.getInstance(Fields.required(currency, "currency")).getCurrencyCode();
        this.status = SeatStatus.AVAILABLE;
    }

    private static BigDecimal validatedPrice(BigDecimal price) {
        Fields.required(price, "price");
        if (price.signum() < 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("price must be between 0 and 9999999999.99");
        }
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("price must have at most two decimal places", exception);
        }
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
