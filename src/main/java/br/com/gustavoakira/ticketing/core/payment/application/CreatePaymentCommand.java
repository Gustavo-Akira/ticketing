package br.com.gustavoakira.ticketing.core.payment.application;

import br.com.gustavoakira.ticketing.core.payment.domain.PaymentType;

import java.util.UUID;

public record CreatePaymentCommand(UUID reservationId, String idempotencyKey, PaymentType paymentType) {
}
