package br.com.gustavoakira.ticketing.core.identity.presentation.cli;

import br.com.gustavoakira.ticketing.core.identity.application.CreateAccountUseCase;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import br.com.gustavoakira.ticketing.core.identity.infrastructure.security.BCryptPasswordHasher;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CreateAdminCommandTest {
    final Map<UUID, User> accounts = new HashMap<>();
    final Map<UUID, String> hashes = new HashMap<>();
    final UserRepository users = new UserRepository() {
        public User save(User user) { accounts.put(user.getId(), user); return user; }
        public Optional<User> findById(UUID id) { return Optional.ofNullable(accounts.get(id)); }
        public Optional<User> findByEmail(String email) { return accounts.values().stream().filter(u -> u.getEmail().equals(email)).findFirst(); }
    };
    final CredentialRepository credentials = new CredentialRepository() {
        public void save(UUID id, String hash) { hashes.put(id, hash); }
        public Optional<String> findHash(UUID id) { return Optional.ofNullable(hashes.get(id)); }
    };
    CreateAccountUseCase useCase() { return new CreateAccountUseCase(users, credentials, new BCryptPasswordHasher()); }
    String[] args() { return new String[] {"identity", "create-admin", "--name", "Admin", "--email", "admin@example.com"}; }
    @Test void createsOnlyAdminAndWipesBothPasswordBuffers() {
        char[] password = "a valid password".toCharArray(), confirmation = password.clone();
        var inputs = new ArrayDeque<char[]>(); inputs.add(password); inputs.add(confirmation);
        int result = new CreateAdminCommand(useCase(), prompt -> inputs.remove()).execute(args());
        assertThat(result).isZero();
        assertThat(accounts.values()).singleElement().satisfies(u -> assertThat(u.getRoles()).containsExactly(Role.ADMIN));
        assertThat(password).containsOnly('\0'); assertThat(confirmation).containsOnly('\0');
        assertThat(hashes.values()).singleElement().asString().startsWith("{bcrypt}");
    }
    @Test void mismatchWipesBuffersAndDoesNotWrite() {
        char[] password = "a valid password".toCharArray(), confirmation = "another password".toCharArray();
        var inputs = new ArrayDeque<char[]>(); inputs.add(password); inputs.add(confirmation);
        assertThat(new CreateAdminCommand(useCase(), prompt -> inputs.remove()).execute(args())).isNotZero();
        assertThat(accounts).isEmpty(); assertThat(password).containsOnly('\0'); assertThat(confirmation).containsOnly('\0');
    }
    @Test void rejectsUnknownDuplicateAndMissingArgumentsBeforeReadingPassword() {
        var command = new CreateAdminCommand(useCase(), prompt -> { throw new AssertionError("should not request password"); });
        for (String[] bad : List.of(new String[]{"identity", "create-admin"},
            new String[]{"identity", "create-admin", "--name", "A", "--email", "a@b", "--password", "secret"},
            new String[]{"identity", "create-admin", "--name", "A", "--name", "B"})) {
            assertThat(command.execute(bad)).isNotZero();
        }
        assertThat(accounts).isEmpty();
    }
    @Test void unavailableConsoleDoesNotWrite() {
        var command = new CreateAdminCommand(useCase(), prompt -> { throw new IllegalStateException("Interactive console required"); });
        assertThat(command.execute(args())).isNotZero(); assertThat(accounts).isEmpty();
    }
    @Test void eofDoesNotWrite() {
        assertThat(new CreateAdminCommand(useCase(), prompt -> null).execute(args())).isNotZero();
        assertThat(accounts).isEmpty();
    }
}
