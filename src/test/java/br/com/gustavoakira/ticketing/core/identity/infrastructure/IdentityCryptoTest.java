package br.com.gustavoakira.ticketing.core.identity.infrastructure;

import br.com.gustavoakira.ticketing.core.identity.infrastructure.security.BCryptPasswordHasher;
import br.com.gustavoakira.ticketing.core.identity.infrastructure.security.SecureRefreshTokenGenerator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentityCryptoTest {
    @Test void hashesWithSaltAndPreservesWhitespace() {
        var hasher = new BCryptPasswordHasher();
        var hash = hasher.hash("  password  ");
        assertThat(hash).startsWith("{bcrypt}$2a$12$");
        assertThat(hasher.hash("  password  ")).isNotEqualTo(hash);
        assertThat(hasher.matches("  password  ", hash)).isTrue();
        assertThat(hasher.matches("password", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
        assertThat(hasher.matches("x".repeat(73), hash)).isFalse();
    }
    @Test void generatesOpaqueTokensAndDigestsWithoutKeepingSecrets() {
        var generator = new SecureRefreshTokenGenerator();
        var token = generator.generate();
        assertThat(token).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(generator.generate());
        assertThat(generator.digest("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(generator.digest(token)).hasSize(64).isNotEqualTo(token);
    }
}
