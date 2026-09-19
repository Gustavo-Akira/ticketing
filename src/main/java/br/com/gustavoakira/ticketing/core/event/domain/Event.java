package br.com.gustavoakira.ticketing.core.event.domain;

import com.github.f4b6a3.uuid.UuidCreator;
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
public class Event {
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

    protected Event() {}

    public Event(String name, Instant startsAt) {
        var details = new EventDetails(name, startsAt);
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.name = details.name();
        this.startsAt = details.startsAt();
        this.status = EventStatus.DRAFT;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getStartsAt() { return startsAt; }
    public EventStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
