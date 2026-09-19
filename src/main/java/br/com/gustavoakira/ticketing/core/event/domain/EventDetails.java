package br.com.gustavoakira.ticketing.core.event.domain;

import java.time.Instant;

public record EventDetails(String name, Instant startsAt) {
    public EventDetails {
        name = Fields.text(name, "name", 255);
        startsAt = Fields.required(startsAt, "startsAt");
    }
}
