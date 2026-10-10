package br.com.gustavoakira.ticketing.core.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class ReservationSnapshot {
    private final UUID id;
    private final BigDecimal amount;
    private final UUID customerId;
    private final String currency;

    public ReservationSnapshot(UUID id, BigDecimal amount, UUID customerId, String currency) {
        this.id = id;
        this.amount = amount;
        this.customerId = customerId;
        this.currency = currency;
        validate();
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }

    private void validate(){
        if(this.amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}
