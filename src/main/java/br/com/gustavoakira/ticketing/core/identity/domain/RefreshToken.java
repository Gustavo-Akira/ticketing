package br.com.gustavoakira.ticketing.core.identity.domain;
import java.time.Instant;
import java.util.UUID;
public record RefreshToken(UUID id, UUID sessionId, String tokenHash, Instant consumedAt) {}
