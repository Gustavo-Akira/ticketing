package br.com.gustavoakira.ticketing.core.identity.domain;
import java.time.Instant;
import java.util.UUID;
public record RefreshSession(UUID id, UUID userId, Instant createdAt, Instant expiresAt, Instant revokedAt) {
    public boolean activeAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }
}
