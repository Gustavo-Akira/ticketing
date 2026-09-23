package br.com.gustavoakira.ticketing.core.identity.infrastructure.security;
import br.com.gustavoakira.ticketing.core.identity.domain.User;
import br.com.gustavoakira.ticketing.core.identity.port.AccessTokenIssuer;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Component
@ConditionalOnWebApplication
public class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    public JwtAccessTokenIssuer(JwtEncoder encoder, Clock clock, @Value("${identity.jwt.issuer}") String issuer,
            @Value("${identity.jwt.audience}") String audience) {
        this.encoder = encoder; this.clock = clock; this.issuer = issuer; this.audience = audience;
    }
    @Override public String issue(User user) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).id(UUID.randomUUID().toString())
                .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
