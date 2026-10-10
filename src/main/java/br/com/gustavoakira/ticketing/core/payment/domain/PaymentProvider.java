package br.com.gustavoakira.ticketing.core.payment.domain;

import java.util.Map;

public class PaymentProvider {
    private String providerReference;
    private PaymentProviderName providerName;
    private Map<String, String> metadata;

    public PaymentProvider(String providerReference, PaymentProviderName providerName, Map<String, String> metadata) {
        this.providerReference = providerReference;
        this.providerName = providerName;
        this.metadata = Map.copyOf(metadata);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public PaymentProviderName getProviderName() {
        return providerName;
    }

    public String getProviderReference() {
        return providerReference;
    }
}
