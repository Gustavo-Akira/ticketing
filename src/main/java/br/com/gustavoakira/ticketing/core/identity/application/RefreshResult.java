package br.com.gustavoakira.ticketing.core.identity.application;
import java.time.Instant;
import java.util.UUID;
public record RefreshResult(UUID userId, String refreshToken, Instant refreshExpiresAt) {
    public boolean accepted() { return userId != null; }
    public static RefreshResult rejected() { return new RefreshResult(null, null, null); }
    @Override public String toString() { return "RefreshResult[accepted=" + accepted() + "]"; }
}
