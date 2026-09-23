package br.com.gustavoakira.ticketing.core.identity.application;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import java.time.Instant;
import java.util.*;
public record UserResult(UUID id, String name, String email, Set<Role> roles, Instant createdAt, Instant updatedAt) {
    public static UserResult from(User user) {
        return new UserResult(user.getId(), user.getName(), user.getEmail(), user.getRoles(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
