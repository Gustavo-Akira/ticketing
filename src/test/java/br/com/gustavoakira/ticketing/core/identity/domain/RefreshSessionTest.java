package br.com.gustavoakira.ticketing.core.identity.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RefreshSessionTest {
    @Test void rejectsExpiredAndRevokedSessionsAtExactBoundary() {
        var end = Instant.parse("2026-10-01T00:00:00Z");
        var session = new RefreshSession(UUID.randomUUID(), UUID.randomUUID(), end.minusSeconds(604800), end, null);
        assertThat(session.activeAt(end.minusNanos(1))).isTrue();
        assertThat(session.activeAt(end)).isFalse();
        assertThat(session.activeAt(end.plusSeconds(1))).isFalse();
        var revoked = new RefreshSession(session.id(), session.userId(), session.createdAt(), end, end.minusSeconds(1));
        assertThat(revoked.activeAt(end.minusSeconds(1))).isFalse();
    }
}
