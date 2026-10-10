package br.com.gustavoakira.ticketing.core.payment.domain;

import com.github.f4b6a3.uuid.UuidCreator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private UUID id;
    private ReservationSnapshot reservationSnapshot;
    private PaymentStatus status;
    private PaymentType type;
    private String idempotencyKey;
    private Instant expiresAt;
    private Instant createdAt;
    private static final Duration PAYMENT_DURATION = Duration.ofMinutes(10);

    private Payment(){}

    public Payment(ReservationSnapshot reservationSnapshot, PaymentType type, String idempotencyKey,Instant createdAt) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.reservationSnapshot = reservationSnapshot;
        this.status = PaymentStatus.PENDING;
        this.type = type;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = createdAt.plus(PAYMENT_DURATION);
        this.createdAt = createdAt;
        validate();
    }

    public static Payment restore(UUID id, ReservationSnapshot reservationSnapshot, PaymentStatus status, PaymentType type, String idempotencyKey,Instant createdAt, Instant expiresAt) {
        Payment payment = new Payment();
        payment.id = id;
        payment.reservationSnapshot = reservationSnapshot;
        payment.status = status;
        payment.type = type;
        payment.idempotencyKey = idempotencyKey;
        payment.expiresAt = expiresAt;
        payment.createdAt = createdAt;
        payment.validate();
        return payment;
    }

    public UUID getId() {
        return id;
    }

    public ReservationSnapshot getReservationSnapshot() {
        return reservationSnapshot;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentType getType() {
        return type;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void cancel() {
        if(status != PaymentStatus.PENDING){
            throw new IllegalStateException("Only PENDING payments can be cancelled");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public void approve() {
        if(status != PaymentStatus.PROCESSING && status != PaymentStatus.UNKNOWN){
            throw new IllegalStateException("Payment is not processing or unknown");
        }
        this.status = PaymentStatus.APPROVED;
    }

    public void reject() {
        if(status != PaymentStatus.PROCESSING && status != PaymentStatus.UNKNOWN){
            throw new IllegalStateException("Payment is not processing or unknown");
        }
        this.status = PaymentStatus.REJECTED;
    }

    public void markAsProcessing(Instant now) {
        if(status != PaymentStatus.PENDING){
            throw new IllegalStateException("Payment is not pending");
        }

        if(!now.isBefore(this.expiresAt)){
            throw new IllegalStateException("Payment expired");
        }
        this.status = PaymentStatus.PROCESSING;
    }

    public void expire(Instant now) {
        if (now.isBefore(expiresAt)) {
            throw new IllegalStateException("Payment has not expired");
        }

        if (status == PaymentStatus.PENDING) {
            this.status = PaymentStatus.EXPIRED;
            return;
        }

        if (status == PaymentStatus.PROCESSING) {
            this.status = PaymentStatus.UNKNOWN;
            return;
        }

        throw new IllegalStateException(
                "Payment cannot expire from status " + status
        );
    }
    public void markAsUnknown() {
        if(status != PaymentStatus.PROCESSING){
            throw new IllegalStateException("Payment is not processing");
        }
        this.status = PaymentStatus.UNKNOWN;
    }

    private void validate(){
        if(this.reservationSnapshot == null){
            throw new IllegalStateException("Reservation Snapshot cannot be null");
        }
        if (!this.expiresAt.isAfter(this.createdAt)) {
            throw new IllegalStateException(
                    "ExpiresAt must be after createdAt"
            );
        }
    }
}
