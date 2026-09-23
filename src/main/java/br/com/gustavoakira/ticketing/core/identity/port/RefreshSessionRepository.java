package br.com.gustavoakira.ticketing.core.identity.port;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import java.time.Instant;
import java.util.*;
public interface RefreshSessionRepository {
    void createSession(UUID id, UUID userId, Instant expiresAt);
    void insertToken(UUID id, UUID sessionId, String hash);
    Optional<RefreshToken> findToken(String hash);
    Optional<RefreshSession> lockSession(UUID id);
    void consume(UUID tokenId, Instant now);
    void revoke(UUID sessionId, Instant now);
}
