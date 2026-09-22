package br.com.gustavoakira.ticketing.core.identity.port;

import br.com.gustavoakira.ticketing.core.identity.domain.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    /** Saves and returns the persisted snapshot, including generated audit timestamps. */
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
}
