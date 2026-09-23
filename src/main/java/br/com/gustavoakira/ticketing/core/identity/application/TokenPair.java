package br.com.gustavoakira.ticketing.core.identity.application;
import java.time.Instant;
public record TokenPair(String accessToken, String tokenType, long expiresIn, String refreshToken, Instant refreshExpiresAt) {
    @Override public String toString() { return "TokenPair[REDACTED]"; }
}
