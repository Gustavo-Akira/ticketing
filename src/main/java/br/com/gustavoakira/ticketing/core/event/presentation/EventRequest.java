package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.domain.EventDetails;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public record EventRequest(String name, Instant startsAt) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EventRequest from(JsonNode node) {
        if (node == null || !node.isObject() || node.has("ownerId")) {
            throw new IllegalArgumentException("Event owner cannot be supplied in the request");
        }
        var name = node.get("name");
        var startsAt = node.get("startsAt");
        if (name == null || !name.isTextual() || startsAt == null || !startsAt.isTextual()) {
            throw new IllegalArgumentException("name and startsAt must be strings");
        }
        try {
            return new EventRequest(name.asText(), Instant.parse(startsAt.asText()));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("startsAt must be an ISO-8601 instant", exception);
        }
    }

    EventDetails toDetails() {
        return new EventDetails(name, startsAt);
    }
}
