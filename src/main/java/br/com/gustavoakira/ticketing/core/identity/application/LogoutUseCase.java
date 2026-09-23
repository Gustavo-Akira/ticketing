package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class LogoutUseCase {
    private final RefreshSessionRepository sessions;
    private final RefreshTokenGenerator tokens;
    private final Clock clock;
    public LogoutUseCase(RefreshSessionRepository sessions, RefreshTokenGenerator tokens, Clock clock) {
        this.sessions = sessions; this.tokens = tokens; this.clock = clock;
    }
    @Transactional public void execute(String rawToken) {
        sessions.findToken(tokens.digest(rawToken)).ifPresent(token ->
            sessions.lockSession(token.sessionId()).ifPresent(session -> sessions.revoke(session.id(), clock.instant())));
    }
}
