package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class CreateAccountUseCase {
    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwords;
    public CreateAccountUseCase(UserRepository users, CredentialRepository credentials, PasswordHasher passwords) {
        this.users = users; this.credentials = credentials; this.passwords = passwords;
    }
    @Transactional public User execute(String name, String email, String password, Set<Role> roles) {
        var user = new User(name, email, roles);
        String hash = passwords.hash(password);
        try {
            var saved = users.save(user);
            credentials.save(saved.getId(), hash);
            return saved;
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "uk_users_email".equals(constraint.getConstraintName())) {
                    throw new DuplicateEmailException();
                }
            }
            throw failure;
        }
    }
}
