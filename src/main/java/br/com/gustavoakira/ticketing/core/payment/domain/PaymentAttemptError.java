package br.com.gustavoakira.ticketing.core.payment.domain;

public class PaymentAttemptError {
    private String message;
    private String code;
    public PaymentAttemptError(String message, String code) {
        this.message = message;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
