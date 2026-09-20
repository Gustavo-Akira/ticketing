package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.SeatBatchCreationCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record SeatBatchCreationRequest(@NotEmpty @Valid List< SeatSectionConfiguration> sections) {
    public record SeatSectionConfiguration(
            @NotBlank String name,
            @NotNull @Positive Integer rowCount,
            @NotNull @Positive Integer seatPerRow,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotBlank @Pattern(regexp = "IDR|USD|EUR|BRL", message = "Invalid currency code") String currency) {
        public SeatBatchCreationCommand.SeatSectionConfiguration toCommand() {
            return new SeatBatchCreationCommand.SeatSectionConfiguration(
              name,
              rowCount,
              seatPerRow,
              price,
              currency
            );
        }
    }
    public SeatBatchCreationCommand toCommand() {
        return new SeatBatchCreationCommand(sections().stream().map(SeatSectionConfiguration::toCommand).toList());
    }
}

