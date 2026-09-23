package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.domain.Role;
import java.util.Set;
import org.springframework.stereotype.Service;
@Service
public class RegisterUserUseCase {
    private final CreateAccountUseCase accounts;
    public RegisterUserUseCase(CreateAccountUseCase accounts) { this.accounts = accounts; }
    public UserResult execute(String name, String email, String password) {
        return UserResult.from(accounts.execute(name, email, password, Set.of(Role.CUSTOMER)));
    }
}
