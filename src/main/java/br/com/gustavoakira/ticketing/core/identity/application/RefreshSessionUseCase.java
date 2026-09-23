package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshSessionUseCase {
    private final RefreshSessionRepository sessions;
    private final RefreshTokenGenerator tokens;
    private final Clock clock;
    public RefreshSessionUseCase(RefreshSessionRepository sessions, RefreshTokenGenerator tokens, Clock clock) {
        this.sessions = sessions; this.tokens = tokens; this.clock = clock;
    }
    @Transactional
    public RefreshResult execute(String rawToken) {
        String hash = tokens.digest(rawToken);
        var found = sessions.findToken(hash);
        if (found.isEmpty()) return RefreshResult.rejected();
        var locked = sessions.lockSession(found.get().sessionId());
        if (locked.isEmpty()) return RefreshResult.rejected();
        var session = locked.get();
        var now = clock.instant();
        if (!session.activeAt(now)) return RefreshResult.rejected();
        // Re-read AFTER the lock: another request may have consumed it while we waited.
        var current = sessions.findToken(hash);
        if (current.isEmpty()) return RefreshResult.rejected();
        if (current.get().consumedAt() != null) {
            sessions.revoke(session.id(), now);
            // Returning normally commits revocation; the HTTP boundary emits 401 afterwards.
            return RefreshResult.rejected();
        }
        String successor = tokens.generate();
        sessions.consume(current.get().id(), now);
        sessions.insertToken(UuidCreator.getTimeOrderedEpoch(), session.id(), tokens.digest(successor));
        return new RefreshResult(session.userId(), successor, session.expiresAt());
    }
}
