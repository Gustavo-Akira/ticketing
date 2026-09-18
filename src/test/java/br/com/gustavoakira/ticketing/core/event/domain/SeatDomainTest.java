package br.com.gustavoakira.ticketing.core.event.domain;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SeatDomainTest {
    private static final UUID EVENT = UUID.randomUUID();

    @Test
    void newSeatIsAvailableAndPreservesItsLocationAndPrice() {
        var seat = new Seat(EVENT, "Floor", "A", "15", new BigDecimal("120.50"));
        assertThat(seat.getId()).isNotNull();
        assertThat(seat.getEventId()).isEqualTo(EVENT);
        assertThat(seat.getSection()).isEqualTo("Floor");
        assertThat(seat.getRow()).isEqualTo("A");
        assertThat(seat.getNumber()).isEqualTo("15");
        assertThat(seat.getPrice()).isEqualByComparingTo("120.50");
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getVersion()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingLocation(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, value, "A", "1", BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "Floor", value, "1", BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "Floor", "A", value, BigDecimal.ONE));
    }

    @Test
    void enforcesLocationLength() {
        assertThat(new Seat(EVENT, "s".repeat(100), "r".repeat(50), "n".repeat(20), BigDecimal.ZERO).getPrice()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "s".repeat(101), "A", "1", BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "S", "r".repeat(51), "1", BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "S", "A", "n".repeat(21), BigDecimal.ONE));
    }

    @Test
    void requiresEventAndPrice() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(null, "S", "A", "1", BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "S", "A", "1", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.001", "10000000000.00"})
    void rejectsInvalidMoneyWithoutSilentlyRounding(String price) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Seat(EVENT, "S", "A", "1", new BigDecimal(price)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9999999999.99", "1.000"})
    void acceptsExactMoneyWithinDatabaseRange(String price) {
        assertThat(new Seat(EVENT, "S", "A", "1", new BigDecimal(price)).getPrice()).isEqualByComparingTo(price);
    }
}
