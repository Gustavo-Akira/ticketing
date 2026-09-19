package br.com.gustavoakira.ticketing.core.event.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record SeatDetails(String section, String row, String number, BigDecimal price, String currency) {
    public SeatDetails {
        section = Fields.text(section, "section", 100);
        row = Fields.text(row, "row", 50);
        number = Fields.text(number, "number", 20);
        Fields.required(price, "price");
        if (price.signum() < 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("price must be between 0 and 9999999999.99");
        }
        try {
            price = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("price must have at most two decimal places", exception);
        }
        currency = Currency.getInstance(Fields.required(currency, "currency")).getCurrencyCode();
    }
}
