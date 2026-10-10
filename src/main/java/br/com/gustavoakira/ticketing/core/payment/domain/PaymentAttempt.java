package br.com.gustavoakira.ticketing.core.payment.domain;

import com.github.f4b6a3.uuid.UuidCreator;

import java.time.Instant;
import java.util.UUID;

public class PaymentAttempt {
    private UUID id;
    private UUID paymentId;
    private int attemptNumber;

    private PaymentProvider provider;

    private PaymentAttemptStatus status;

    private Instant createdAt;
    private Instant processingUntil;

    private PaymentAttemptError error;

    private PaymentAttempt(){}

    public PaymentAttempt(UUID paymentId, int attemptNumber, PaymentProvider provider, Instant createdAt, Instant processingUntil, PaymentAttemptError error) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.provider = provider;
        this.status = PaymentAttemptStatus.STARTED;
        this.createdAt = createdAt;
        this.processingUntil = processingUntil;
        this.error = error;
        validate();
    }

    public static PaymentAttempt restore(UUID id,UUID paymentId, int attemptNumber, PaymentProvider provider, PaymentAttemptStatus status, Instant createdAt, Instant processingUntil, PaymentAttemptError error){
        PaymentAttempt paymentAttempt = new PaymentAttempt();
        paymentAttempt.id = id;
        paymentAttempt.paymentId = paymentId;
        paymentAttempt.attemptNumber = attemptNumber;
        paymentAttempt.provider = provider;
        paymentAttempt.status = status;
        paymentAttempt.createdAt = createdAt;
        paymentAttempt.processingUntil = processingUntil;
        paymentAttempt.error = error;
        paymentAttempt.validate();
        return paymentAttempt;
    }

    public void complete(){
        if(status == PaymentAttemptStatus.COMPLETED){
            throw new IllegalStateException("Attempt has already been completed");
        }
        status = PaymentAttemptStatus.COMPLETED;
    }

    public void markAsUnknown(){
        if(status != PaymentAttemptStatus.STARTED){
            throw new  IllegalStateException("Only started attempts can be marked as unknown");
        }
        status = PaymentAttemptStatus.UNKNOWN;
    }


    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessingUntil() {
        return processingUntil;
    }

    public PaymentAttemptError getError() {
        return error;
    }

    public void setError(PaymentAttemptError error) {
        this.error = error;
    }

    public void setProvider(PaymentProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider cannot be null");
        }
        if (this.provider == null || this.provider.getProviderName() != provider.getProviderName()) {
            throw new IllegalStateException("An attempt cannot change payment provider");
        }
        String reference = this.provider.getProviderReference();
        if (reference != null && !reference.equals(provider.getProviderReference())) {
            throw new IllegalStateException("An attempt cannot replace or clear its provider reference");
        }
        this.provider = provider;
    }

    private void validate(){
        if(processingUntil.isBefore(createdAt)){
            throw new IllegalStateException("Attempt has already been created");
        }
    }
}
