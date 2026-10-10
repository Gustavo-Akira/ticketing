package br.com.gustavoakira.ticketing.core.payment.domain;

public enum PaymentStatus {
    UNKNOWN,
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    PROCESSING,
}
