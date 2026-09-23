package br.com.gustavoakira.ticketing.core.identity.infrastructure.persistence;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.RefreshSessionRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcRefreshSessionRepository implements RefreshSessionRepository {
    private final JdbcTemplate jdbc;
    public JdbcRefreshSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void createSession(UUID id, UUID userId, Instant expiresAt) {
        jdbc.update("insert into refresh_sessions(id, user_id, expires_at) values (?, ?, ?)", id, userId, Timestamp.from(expiresAt));
    }
    @Override public void insertToken(UUID id, UUID sessionId, String hash) {
        jdbc.update("insert into refresh_tokens(id, session_id, token_hash) values (?, ?, ?)", id, sessionId, hash);
    }
    @Override public Optional<RefreshToken> findToken(String hash) {
        return jdbc.query("select id, session_id, token_hash, consumed_at from refresh_tokens where token_hash = ?",
                (rs, n) -> new RefreshToken(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class),
                    rs.getString("token_hash"), instant(rs, "consumed_at")), hash).stream().findFirst();
    }
    @Override public Optional<RefreshSession> lockSession(UUID id) {
        return jdbc.query("select * from refresh_sessions where id = ? for update",
                (rs, n) -> new RefreshSession(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    instant(rs, "created_at"), instant(rs, "expires_at"), instant(rs, "revoked_at")), id).stream().findFirst();
    }
    @Override public void consume(UUID tokenId, Instant now) {
        jdbc.update("update refresh_tokens set consumed_at = ? where id = ?", Timestamp.from(now), tokenId);
    }
    @Override public void revoke(UUID sessionId, Instant now) {
        jdbc.update("update refresh_sessions set revoked_at = ? where id = ? and revoked_at is null", Timestamp.from(now), sessionId);
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
