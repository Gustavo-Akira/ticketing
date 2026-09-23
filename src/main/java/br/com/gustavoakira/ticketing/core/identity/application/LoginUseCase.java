package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@Service
@ConditionalOnWebApplication
public class LoginUseCase {
    // Valid BCrypt cost-12 hash; never a real account credential.
    private static final String DUMMY = "{bcrypt}$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwords;
    private final RefreshSessionRepository sessions;
    private final RefreshTokenGenerator tokens;
    private final AccessTokenIssuer issuer;
    private final Clock clock;
    public LoginUseCase(UserRepository users, CredentialRepository credentials, PasswordHasher passwords,
            RefreshSessionRepository sessions, RefreshTokenGenerator tokens, AccessTokenIssuer issuer, Clock clock) {
        this.users = users; this.credentials = credentials; this.passwords = passwords;
        this.sessions = sessions; this.tokens = tokens; this.issuer = issuer; this.clock = clock;
    }
    @Transactional public TokenPair execute(String email, String password) {
        String normalized;
        try { normalized = UserDetails.normalizeEmail(email); }
        catch (IllegalArgumentException invalid) { passwords.matches(password, DUMMY); throw new InvalidCredentialsException(); }
        var user = users.findByEmail(normalized);
        var hash = user.flatMap(u -> credentials.findHash(u.getId()));
        boolean matches = passwords.matches(password, hash.orElse(DUMMY));
        if (!matches || hash.isEmpty()) throw new InvalidCredentialsException();
        var account = user.orElseThrow(InvalidCredentialsException::new);
        var sessionId = UuidCreator.getTimeOrderedEpoch();
        var expiresAt = clock.instant().plus(Duration.ofDays(7));
        String raw = tokens.generate();
        sessions.createSession(sessionId, account.getId(), expiresAt);
        sessions.insertToken(UuidCreator.getTimeOrderedEpoch(), sessionId, tokens.digest(raw));
        return new TokenPair(issuer.issue(account), "Bearer", 900, raw, expiresAt);
    }
}
