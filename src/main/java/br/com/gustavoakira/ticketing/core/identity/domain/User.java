package br.com.gustavoakira.ticketing.core.identity.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class User {
    private final UUID id;
    private final UserDetails details;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(String name, String email, Set<Role> roles) {
        this(UuidCreator.getTimeOrderedEpoch(), new UserDetails(name, email, roles), null, null);
    }

    private User(UUID id, UserDetails details, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.details = details;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Reconstitutes persisted state without generating a new identity or audit timestamps. */
    public static User restore(UUID id, String name, String email, Set<Role> roles,
                               Instant createdAt, Instant updatedAt) {
        if (id == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("id, createdAt and updatedAt are required");
        }
        return new User(id, new UserDetails(name, email, roles), createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public String getName() { return details.name(); }
    public String getEmail() { return details.email(); }
    public Set<Role> getRoles() { return details.roles(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
