package br.com.gustavoakira.ticketing.core.identity.infrastructure.persistence;

import br.com.gustavoakira.ticketing.core.identity.domain.Role;
import br.com.gustavoakira.ticketing.core.identity.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import static org.hibernate.generator.EventType.INSERT;

@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @ElementCollection
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = new HashSet<>();

    @Generated(event = INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = INSERT)
    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected UserJpaEntity() {}

    static UserJpaEntity fromDomain(User model) {
        var entity = new UserJpaEntity();
        entity.id = model.getId();
        entity.name = model.getName();
        entity.email = model.getEmail();
        entity.roles = new HashSet<>(model.getRoles());
        entity.createdAt = model.getCreatedAt();
        entity.updatedAt = model.getUpdatedAt();
        return entity;
    }

    void preserveAuditFrom(UserJpaEntity persisted) {
        this.createdAt = persisted.createdAt;
        this.updatedAt = persisted.updatedAt;
    }

    User toDomain() {
        return User.restore(id, name, email, roles, createdAt, updatedAt);
    }
}
