package br.com.gustavoakira.ticketing.core.identity.infrastructure.persistence;

import br.com.gustavoakira.ticketing.core.identity.domain.User;
import br.com.gustavoakira.ticketing.core.identity.domain.UserDetails;
import br.com.gustavoakira.ticketing.core.identity.port.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaUserRepository implements UserRepository {
    private final SpringDataUserRepository users;

    public JpaUserRepository(SpringDataUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public User save(User user) {
        return users.saveAndFlush(UserJpaEntity.fromDomain(user)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return users.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return users.findByEmail(UserDetails.normalizeEmail(email)).map(UserJpaEntity::toDomain);
    }
}
