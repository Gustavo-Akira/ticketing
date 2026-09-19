package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.domain.SeatDetails;
import java.math.BigDecimal;

public record SeatRequest(String section, String row, String number, BigDecimal price, String currency) {
    SeatDetails toDetails() {
        return new SeatDetails(section, row, number, price, currency);
    }
}
