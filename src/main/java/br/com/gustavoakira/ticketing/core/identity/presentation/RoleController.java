package br.com.gustavoakira.ticketing.core.identity.presentation;
import br.com.gustavoakira.ticketing.core.identity.application.GrantOrganizerUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class RoleController {
    private final GrantOrganizerUseCase grant;
    public RoleController(GrantOrganizerUseCase grant) { this.grant = grant; }
    @PutMapping("/users/{id}/roles/organizer") public ResponseEntity<Void> grant(@PathVariable UUID id) {
        grant.execute(id);
        return ResponseEntity.noContent().build();
    }
}
