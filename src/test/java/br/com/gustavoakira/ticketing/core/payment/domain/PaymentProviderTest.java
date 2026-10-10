package br.com.gustavoakira.ticketing.core.payment.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentProviderTest {
    @Test
    void snapshotsMetadataSoCallerChangesCannotRewriteProviderResponse() {
        var metadata = new HashMap<>(Map.of("result", "captured"));
        var provider = new PaymentProvider("reference", PaymentProviderName.STRIPE, metadata);

        metadata.put("result", "declined");
        metadata.put("other", "value");

        assertEquals(Map.of("result", "captured"), provider.getMetadata());
    }

    @Test
    void preventsMutationThroughReturnedMetadata() {
        var provider = new PaymentProvider("reference", PaymentProviderName.STRIPE,
                Map.of("result", "captured"));

        assertThrows(UnsupportedOperationException.class,
                () -> provider.getMetadata().put("result", "declined"));

        assertEquals(Map.of("result", "captured"), provider.getMetadata());
    }
}
