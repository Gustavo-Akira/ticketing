package br.com.gustavoakira.ticketing.core.identity.infrastructure.security;

import br.com.gustavoakira.ticketing.core.identity.domain.Role;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@ConditionalOnWebApplication
public class JwtConfiguration {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final Clock clock;
    public JwtConfiguration(@Value("${identity.jwt.private-key}") Resource privateResource,
            @Value("${identity.jwt.public-key}") Resource publicResource,
            @Value("${identity.jwt.issuer}") String issuer, @Value("${identity.jwt.audience}") String audience,
            Clock clock) {
        try (var priv = privateResource.getInputStream(); var pub = publicResource.getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(priv);
            publicKey = RsaKeyConverters.x509().convert(pub);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Cannot load identity JWT keys", failure);
        }
        if (privateKey == null || publicKey == null || publicKey.getModulus().bitLength() < 2048
                || !privateKey.getModulus().equals(publicKey.getModulus()) || issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("Valid matching RSA keys, issuer and audience are required");
        }
        this.issuer = issuer; this.audience = audience; this.clock = clock;
    }
    @Bean public JwtEncoder jwtEncoder() {
        var key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("identity").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
    @Bean public JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer), this::validateClaims));
        return decoder;
    }
    private OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        try {
            String subject = jwt.getSubject();
            if (subject == null || !UUID.fromString(subject).toString().equals(subject)
                    || jwt.getExpiresAt() == null || !clock.instant().isBefore(jwt.getExpiresAt())
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(clock.instant())
                    || jwt.getId() == null || jwt.getId().isBlank()
                    || jwt.getAudience() == null || !jwt.getAudience().contains(audience)) return invalid();
            var roles = jwt.getClaimAsStringList("roles");
            if (roles == null || roles.isEmpty()) return invalid();
            for (String role : roles) Role.valueOf(role);
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException | NullPointerException failure) {
            return invalid();
        }
    }
    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
    }
}
