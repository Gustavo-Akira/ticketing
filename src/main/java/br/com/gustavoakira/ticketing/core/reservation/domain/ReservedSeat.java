package br.com.gustavoakira.ticketing.core.reservation.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class ReservedSeat {
    private UUID id;
    private BigDecimal price;

    public ReservedSeat(UUID id, BigDecimal price) {
        if(id == null){
            throw new IllegalArgumentException("Reserved Seat ID cannot be null");
        }
        this.id = id;
        if(price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        this.price = price;
    }

    public UUID getId() {
        return id;
    }
    public BigDecimal getPrice() {
        return price;
    }
}
