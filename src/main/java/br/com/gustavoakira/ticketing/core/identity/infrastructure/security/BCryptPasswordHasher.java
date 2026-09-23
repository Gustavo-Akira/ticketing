package br.com.gustavoakira.ticketing.core.identity.infrastructure.security;
import br.com.gustavoakira.ticketing.core.identity.domain.PasswordPolicy;
import br.com.gustavoakira.ticketing.core.identity.port.PasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    @Override public String hash(String password) {
        PasswordPolicy.validate(password);
        return "{bcrypt}" + encoder.encode(password);
    }
    @Override public boolean matches(String password, String encoded) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
                || encoded == null || !encoded.startsWith("{bcrypt}")) return false;
        return encoder.matches(password, encoded.substring(8));
    }
}
