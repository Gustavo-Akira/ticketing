package br.com.gustavoakira.ticketing.core.identity.presentation.cli;

import br.com.gustavoakira.ticketing.core.identity.application.CreateAccountUseCase;
import br.com.gustavoakira.ticketing.core.CoreApplication;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "identity.jwt.private-key=file:/nonexistent-private.pem", "identity.jwt.public-key=file:/nonexistent-public.pem"})
@Testcontainers
class AdminCommandIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired ApplicationContext context;
    @Autowired CreateAccountUseCase create;
    @Autowired UserRepository users;
    @Autowired CredentialRepository credentials;
    @Autowired JdbcTemplate jdbc;
    @Test void runsWithoutWebOrJwtAndDuplicateEmailLeavesAccountUntouched() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeansOfType(JwtEncoder.class)).isEmpty();
        assertThat(context.getBeansOfType(AccessTokenIssuer.class)).isEmpty();
        String[] args = {"identity", "create-admin", "--name", "Admin", "--email", "admin@example.com"};
        var command = new CreateAdminCommand(create, prompt -> "a valid password".toCharArray());
        assertThat(command.execute(args)).isZero();
        var user = users.findByEmail("admin@example.com").orElseThrow();
        assertThat(user.getRoles()).containsExactly(Role.ADMIN);
        var hash = credentials.findHash(user.getId());
        assertThat(command.execute(args)).isNotZero();
        assertThat(credentials.findHash(user.getId())).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }
    @Test void actualBootstrapSelectsNonWebModeAndReturnsCommandFailure() {
        assertThat(System.console()).isNull();
        var properties = Map.of("spring.datasource.url", POSTGRES.getJdbcUrl(),
            "spring.datasource.username", POSTGRES.getUsername(), "spring.datasource.password", POSTGRES.getPassword(),
            "identity.jwt.private-key", "file:/nonexistent.pem", "identity.jwt.public-key", "file:/nonexistent.pem");
        var previous = new HashMap<String, String>();
        properties.forEach((key, value) -> previous.put(key, System.setProperty(key, value)));
        try {
            assertThat(CoreApplication.runAdmin(new String[]{"identity", "create-admin", "--name", "Bootstrap", "--email", "bootstrap@example.com"}))
                .isEqualTo(2);
            assertThat(users.findByEmail("bootstrap@example.com")).isEmpty();
        } finally {
            previous.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        }
    }
}
