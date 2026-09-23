package br.com.gustavoakira.ticketing.core.identity.infrastructure.persistence;
import br.com.gustavoakira.ticketing.core.identity.port.RoleGrantRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcRoleGrantRepository implements RoleGrantRepository {
    private final JdbcTemplate jdbc;
    public JdbcRoleGrantRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean grantOrganizer(UUID userId) {
        if (jdbc.query("select id from users where id = ? for update", (rs, n) -> rs.getObject(1, UUID.class), userId).isEmpty()) return false;
        int added = jdbc.update("insert into user_roles(user_id, role) values (?, 'ORGANIZER') on conflict do nothing", userId);
        if (added == 1) jdbc.update("update users set updated_at = statement_timestamp() where id = ?", userId);
        return true;
    }
}
