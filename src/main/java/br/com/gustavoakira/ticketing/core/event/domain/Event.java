package br.com.gustavoakira.ticketing.core.event.domain;

import br.com.gustavoakira.ticketing.core.event.application.EventNotDraftException;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.UUID;

public class Event {
    private UUID id;
    private String name;
    private Instant startsAt;
    private EventStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Event() {}

    public Event(String name, Instant startsAt) {
        var details = new EventDetails(name, startsAt);
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.name = details.name();
        this.startsAt = details.startsAt();
        this.status = EventStatus.DRAFT;
    }

    /** Reconstitutes persisted state without generating a new identity or resetting status. */
    public static Event restore(UUID id, String name, Instant startsAt, EventStatus status,
                                Instant createdAt, Instant updatedAt) {
        var details = new EventDetails(name, startsAt);
        var event = new Event();
        event.id = Fields.required(id, "id");
        event.name = details.name();
        event.startsAt = details.startsAt();
        event.status = Fields.required(status, "status");
        event.createdAt = Fields.required(createdAt, "createdAt");
        event.updatedAt = Fields.required(updatedAt, "updatedAt");
        return event;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getStartsAt() { return startsAt; }
    public EventStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void changeStatusToAvailable() {
        if(status != EventStatus.DRAFT){
            throw new EventNotDraftException("Cannot change status to available event when event is not in DRAFT");
        }
        this.status = EventStatus.AVAILABLE;
    }
}
