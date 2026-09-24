package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EventOwnershipTest {
    static final Instant START = Instant.parse("2027-01-10T20:00:00Z");
    @Test void newEventRequiresOwnerAndPreservesIt() {
        UUID owner = UUID.randomUUID();
        var event = new Event("Concert", START, owner);
        assertThat(event.getOwnerId()).isEqualTo(owner);
        assertThatIllegalArgumentException().isThrownBy(() -> new Event("Concert", START, null));
        assertThatCode(() -> EventOwnership.requireOwner(event, owner)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EventOwnership.requireOwner(event, UUID.randomUUID()))
            .isInstanceOf(EventAccessDeniedException.class);
        assertThatThrownBy(() -> EventOwnership.requireOwner(event, null))
            .isInstanceOf(EventAccessDeniedException.class);
    }
    @Test void legacyCannotBeManagedEvenWithNullActor() {
        var legacy = Event.restore(UUID.randomUUID(), "Legacy", START, EventStatus.DRAFT, START, START, null);
        assertThat(legacy.getOwnerId()).isNull();
        assertThatThrownBy(() -> EventOwnership.requireOwner(legacy, UUID.randomUUID()))
            .isInstanceOf(EventAccessDeniedException.class);
        assertThatThrownBy(() -> EventOwnership.requireOwner(legacy, null))
            .isInstanceOf(EventAccessDeniedException.class);
    }
}
