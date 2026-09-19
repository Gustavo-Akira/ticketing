package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.domain.EventDetails;
import java.time.Instant;

public record EventRequest(String name, Instant startsAt) {
    EventDetails toDetails() {
        return new EventDetails(name, startsAt);
    }
}
