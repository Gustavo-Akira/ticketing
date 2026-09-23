package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.port.RoleGrantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GrantOrganizerUseCase {
    private final RoleGrantRepository roles;
    public GrantOrganizerUseCase(RoleGrantRepository roles) { this.roles = roles; }
    @Transactional public void execute(UUID userId) {
        if (!roles.grantOrganizer(userId)) throw new UserNotFoundException();
    }
}
