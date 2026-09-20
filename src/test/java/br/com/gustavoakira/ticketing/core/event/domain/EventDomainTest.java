package br.com.gustavoakira.ticketing.core.event.domain;

import br.com.gustavoakira.ticketing.core.event.application.EventNotDraftException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class EventDomainTest {
    private static final Instant START = Instant.parse("2027-01-10T20:00:00Z");

    @Test
    void newEventStartsAsDraftWithItsOwnIdentity() {
        var event = new Event("Concert", START);
        assertThat(event.getId()).isNotNull();
        assertThat(event.getId().version()).isEqualTo(7);
        assertThat(event.getId().variant()).isEqualTo(2);
        assertThat(event.getId()).isNotEqualTo(new Event("Concert", START).getId());
        assertThat(event.getName()).isEqualTo("Concert");
        assertThat(event.getStartsAt()).isEqualTo(START);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingName(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Event(name, START));
    }

    @Test
    void enforcesNameLengthAndRequiredStart() {
        assertThat(new Event("a".repeat(255), START).getName()).hasSize(255);
        assertThatIllegalArgumentException().isThrownBy(() -> new Event("a".repeat(256), START));
        assertThatIllegalArgumentException().isThrownBy(() -> new Event("Concert", null));
    }

    @Test
    void draftCanBecomeAvailableWithoutChangingItsIdentityOrDetails() {
        var event = new Event("Concert", START);
        var id = event.getId();
        event.changeStatusToAvailable();
        assertThat(event.getStatus()).isEqualTo(EventStatus.AVAILABLE);
        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getName()).isEqualTo("Concert");
        assertThat(event.getStartsAt()).isEqualTo(START);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = "DRAFT",
            mode = EnumSource.Mode.EXCLUDE)
    void nonDraftCannotBecomeAvailable(EventStatus status) {
        var event = Event.restore(UUID.randomUUID(), "Concert", START, status, START, START);
        assertThatThrownBy(event::changeStatusToAvailable)
                .isInstanceOf(EventNotDraftException.class);
        assertThat(event.getStatus()).isEqualTo(status);
    }
}
