package br.com.gustavoakira.ticketing.core.identity.infrastructure.persistence;
import br.com.gustavoakira.ticketing.core.identity.port.CredentialRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcCredentialRepository implements CredentialRepository {
    private final JdbcTemplate jdbc;
    public JdbcCredentialRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void save(UUID userId, String passwordHash) {
        jdbc.update("insert into user_credentials(user_id, password_hash) values (?, ?)", userId, passwordHash);
    }
    @Override public Optional<String> findHash(UUID userId) {
        return jdbc.query("select password_hash from user_credentials where user_id = ?",
                (rs, n) -> rs.getString(1), userId).stream().findFirst();
    }
}
