package br.com.gustavoakira.ticketing.core.identity.infrastructure;

import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.infrastructure.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtSecurityTest {
    final Instant now = Instant.parse("2026-09-22T12:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    JwtConfiguration config;
    @BeforeEach void setup() {
        config = new JwtConfiguration(new ClassPathResource("identity/private.pem"),
            new ClassPathResource("identity/public.pem"), "https://identity.test", "ticketing-test", clock);
    }
    @Test void issuesAndValidatesIdentityAndIndependentRoles() throws Exception {
        var issuer = new JwtAccessTokenIssuer(config.jwtEncoder(), clock, "https://identity.test", "ticketing-test");
        var user = new User("Admin", "admin@example.com", Set.of(Role.ADMIN));
        var token = config.jwtDecoder().decode(issuer.issue(user));
        assertThat(token.getSubject()).isEqualTo(user.getId().toString());
        assertThat(token.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(token.getExpiresAt()).isEqualTo(now.plusSeconds(900));
        assertThat(token.getIssuedAt()).isEqualTo(now);
        assertThat(token.getId()).isNotBlank();
        assertThat(token.getClaims()).doesNotContainKeys("password", "email", "refreshToken");
    }
    @Test void rejectsInvalidClaimsAndMissingExpiry() throws Exception {
        for (String field : List.of("iss", "aud", "sub", "exp", "missing-exp", "roles")) {
            var claims = claims();
            switch (field) {
                case "iss" -> claims.issuer("https://wrong.test");
                case "aud" -> claims.audience(List.of("wrong"));
                case "sub" -> claims.subject("not-a-uuid");
                case "exp" -> claims.issuedAt(now.minusSeconds(900)).expiresAt(now);
                case "missing-exp" -> claims.claims(map -> map.remove("exp"));
                case "roles" -> claims.claim("roles", List.of("SUPERUSER"));
            }
            String raw = config.jwtEncoder().encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
            assertThatThrownBy(() -> config.jwtDecoder().decode(raw)).isInstanceOf(JwtException.class);
        }
    }
    @Test void rejectsTamperedSignature() throws Exception {
        String raw = config.jwtEncoder().encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims().build())).getTokenValue();
        String[] parts = raw.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]); signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        assertThatThrownBy(() -> config.jwtDecoder().decode(tampered)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsCorrectlySignedTokenUsingUnapprovedAlgorithm() {
        String raw = config.jwtEncoder().encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS512).build(), claims().build())).getTokenValue();
        assertThatThrownBy(() -> config.jwtDecoder().decode(raw)).isInstanceOf(JwtException.class);
    }
    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer("https://identity.test").audience(List.of("ticketing-test"))
            .subject(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(900)).id(UUID.randomUUID().toString())
            .claim("roles", List.of("CUSTOMER"));
    }
}
