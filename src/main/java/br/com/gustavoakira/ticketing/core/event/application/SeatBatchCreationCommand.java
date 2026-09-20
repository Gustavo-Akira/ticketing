package br.com.gustavoakira.ticketing.core.event.application;

import java.math.BigDecimal;
import java.util.List;

public record SeatBatchCreationCommand(List<SeatSectionConfiguration> sections) {
    public record SeatSectionConfiguration(String name, int rowCount, int seatPerRow, BigDecimal price, String currency) {

    }
}

