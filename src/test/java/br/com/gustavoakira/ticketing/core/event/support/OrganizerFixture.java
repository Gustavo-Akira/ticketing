package br.com.gustavoakira.ticketing.core.event.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class OrganizerFixture {
    public static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private OrganizerFixture() {}

    public static void seed(JdbcTemplate jdbc) {
        jdbc.update("insert into users(id,name,email) values (?, 'Organizer', 'fixture@example.com') on conflict (id) do nothing", OWNER);
        jdbc.update("insert into user_roles(user_id,role) values (?, 'ORGANIZER') on conflict do nothing", OWNER);
    }

    public static RequestPostProcessor organizer() {
        return jwt().jwt(token -> token.subject(OWNER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    }
}
