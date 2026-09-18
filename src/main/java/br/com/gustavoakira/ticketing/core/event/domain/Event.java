package br.com.gustavoakira.ticketing.core.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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

    protected Event() {}

    public Event(String name, Instant startsAt) {
        this.id = UUID.randomUUID();
        this.name = Fields.text(name, "name", 255);
        this.startsAt = Fields.required(startsAt, "startsAt");
        this.status = EventStatus.DRAFT;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getStartsAt() { return startsAt; }
    public EventStatus getStatus() { return status; }
}
