package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import br.com.gustavoakira.ticketing.core.event.domain.SeatStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record SeatResult(UUID id, UUID eventId, String section, String row, String number,
                         BigDecimal price, String currency, SeatStatus status) {
    static SeatResult from(Seat seat) {
        return new SeatResult(seat.getId(), seat.getEventId(), seat.getSection(), seat.getRow(),
                seat.getNumber(), seat.getPrice(), seat.getCurrency(), seat.getStatus());
    }
}
