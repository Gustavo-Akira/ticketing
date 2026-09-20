package br.com.gustavoakira.ticketing.core.event.infrastructure.persistence;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import static org.hibernate.generator.EventType.INSERT;

@Entity
@Table(name = "events")
public class EventJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Generated(event = INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = INSERT)
    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected EventJpaEntity() {}

    static EventJpaEntity fromDomain(Event model) {
        var entity = new EventJpaEntity();
        entity.id = model.getId();
        entity.name = model.getName();
        entity.startsAt = model.getStartsAt();
        entity.status = model.getStatus();
        entity.createdAt = model.getCreatedAt();
        entity.updatedAt = model.getUpdatedAt();
        return entity;
    }

    Event toDomain() {
        return Event.restore(id, name, startsAt, status, createdAt, updatedAt);
    }
}
