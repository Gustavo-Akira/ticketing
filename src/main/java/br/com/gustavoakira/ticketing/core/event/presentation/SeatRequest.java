package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.domain.SeatDetails;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SeatRequest(String section, String row, String number, BigDecimal price, String currency,
                          @JsonDeserialize(using = ExpectedVersionDeserializer.class) Long expectedVersion) {
    SeatDetails toDetails() {
        return new SeatDetails(section, row, number, price, currency);
    }
}
