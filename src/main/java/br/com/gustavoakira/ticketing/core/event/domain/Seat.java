package br.com.gustavoakira.ticketing.core.event.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.util.UUID;

public class Seat {
    private UUID id;
    private UUID eventId;
    private String section;
    private String row;
    private String number;
    private BigDecimal price;
    private String currency;
    private SeatStatus status;
    private Long version;

    private Seat() {}

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

    /** Reconstitutes persisted state, including the version used for optimistic concurrency. */
    public static Seat restore(UUID id, UUID eventId, String section, String row, String number,
                               BigDecimal price, String currency, SeatStatus status, Long version) {
        var seat = new Seat();
        seat.id = Fields.required(id, "id");
        seat.eventId = Fields.required(eventId, "eventId");
        seat.applyDetails(new SeatDetails(section, row, number, price, currency));
        seat.status = Fields.required(status, "status");
        seat.version = Fields.required(version, "version");
        return seat;
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
