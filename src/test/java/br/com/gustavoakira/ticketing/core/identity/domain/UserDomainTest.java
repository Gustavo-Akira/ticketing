package br.com.gustavoakira.ticketing.core.identity.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class UserDomainTest {
    @Test
    void createsIdentityWithNormalizedEmailAndDefensivelyCopiedRoles() {
        var roles = EnumSet.of(Role.CUSTOMER, Role.ORGANIZER);
        var user = new User("Ana", " ANA@Example.COM ", roles);
        roles.clear();
        assertThat(user.getId().version()).isEqualTo(7);
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.ORGANIZER);
        assertThatThrownBy(() -> user.getRoles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void restoresIdentityAndAuditWithoutAddingImplicitRoles() {
        var id = UUID.randomUUID();
        var created = Instant.parse("2026-01-01T00:00:00Z");
        var updated = created.plusSeconds(10);
        var user = User.restore(id, "Ana", "ana@example.com", Set.of(Role.ADMIN), created, updated);
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getRoles()).containsExactly(Role.ADMIN);
        assertThat(user.getCreatedAt()).isEqualTo(created);
        assertThat(user.getUpdatedAt()).isEqualTo(updated);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingName(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> new User(name, "a@b", Set.of(Role.CUSTOMER)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a", "@b", "a@", "a@@b", "a b@c", "a@b c", "a\tb@c", "a\u2003b@c"})
    void rejectsInvalidEmail(String email) {
        assertThatIllegalArgumentException().isThrownBy(() -> new User("Ana", email, Set.of(Role.CUSTOMER)));
    }

    @Test
    void enforcesLengthLimitsAfterEmailNormalization() {
        var email = "a".repeat(252) + "@b";
        assertThat(new User("a".repeat(255), " " + email + " ", Set.of(Role.CUSTOMER)).getEmail()).hasSize(254);
        assertThatIllegalArgumentException().isThrownBy(() -> new User("a".repeat(256), "a@b", Set.of(Role.CUSTOMER)));
        assertThatIllegalArgumentException().isThrownBy(() -> new User("Ana", "a" + email, Set.of(Role.CUSTOMER)));
    }

    @Test
    void rejectsMissingOrNullRoles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new User("Ana", "a@b", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new User("Ana", "a@b", Set.of()));
        var roles = new HashSet<Role>();
        roles.add(Role.CUSTOMER);
        roles.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> new User("Ana", "a@b", roles));
    }

    @Test
    void requiresPersistedIdentityAndAuditOnRestore() {
        var now = Instant.now();
        var id = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> User.restore(null, "Ana", "a@b", Set.of(Role.CUSTOMER), now, now));
        assertThatIllegalArgumentException().isThrownBy(() -> User.restore(id, "Ana", "a@b", Set.of(Role.CUSTOMER), null, now));
        assertThatIllegalArgumentException().isThrownBy(() -> User.restore(id, "Ana", "a@b", Set.of(Role.CUSTOMER), now, null));
        assertThatIllegalArgumentException().isThrownBy(() -> User.restore(id, "Ana", "invalid", Set.of(Role.CUSTOMER), now, now));
    }

    @Test
    void normalizationIsIndependentOfJvmLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(new User("Iris", "IRIS@EXAMPLE.COM", Set.of(Role.CUSTOMER)).getEmail()).isEqualTo("iris@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
