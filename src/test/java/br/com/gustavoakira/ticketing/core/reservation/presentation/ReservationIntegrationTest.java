package br.com.gustavoakira.ticketing.core.reservation.presentation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
class ReservationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mvc;
    private UUID customerId;
    private UUID eventId;
    private UUID firstSeat;
    private UUID secondSeat;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        customerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        firstSeat = UUID.randomUUID();
        secondSeat = UUID.randomUUID();
        jdbc.update("insert into users(id, name, email) values (?, 'Customer', ?)", customerId, customerId + "@example.com");
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', statement_timestamp() + interval '1 day', 'AVAILABLE')", eventId);
        insertSeat(firstSeat, "1", new BigDecimal("120.50"));
        insertSeat(secondSeat, "2", new BigDecimal("75.25"));
    }

    @Test
    void createsCompleteReservationWithPersistedPricesAndJwtOwner() throws Exception {
        var response = request(body(eventId, "\"" + firstSeat + "\",\"" + secondSeat + "\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andReturn().getResponse();
        assertThat(response.getHeader("Location")).startsWith("/v1/reservations/");
        UUID id = UUID.fromString(response.getHeader("Location").substring("/v1/reservations/".length()));
        var row = jdbc.queryForMap("select * from reservations where id = ?", id);
        assertThat(row.get("customer_id")).isEqualTo(customerId);
        assertThat(row.get("status")).isEqualTo("ON_HOLD");
        var created = jdbc.queryForObject("select created_at from reservations where id = ?", OffsetDateTime.class, id);
        var expires = jdbc.queryForObject("select expires_at from reservations where id = ?", OffsetDateTime.class, id);
        assertThat(expires).isAfter(created);
        String responseExpiration = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.expiresAt");
        assertThat(expires.toInstant()).isCloseTo(Instant.parse(responseExpiration), within(1, ChronoUnit.MICROS));
        assertThat(jdbc.queryForList("select seat_id from reserved_seats where reservation_id = ?", UUID.class, id))
                .containsExactlyInAnyOrder(firstSeat, secondSeat);
        assertThat(jdbc.queryForObject("select price from reserved_seats where reservation_id = ? and seat_id = ?", BigDecimal.class, id, firstSeat))
                .isEqualByComparingTo("120.50");
        assertThat(jdbc.queryForObject("select price from reserved_seats where reservation_id = ? and seat_id = ?", BigDecimal.class, id, secondSeat))
                .isEqualByComparingTo("75.25");
        assertThat(jdbc.queryForList("select currency from reserved_seats where reservation_id = ?", String.class, id))
                .containsExactly("BRL", "BRL");
        assertSeat(firstSeat, "RESERVED", 1);
        assertSeat(secondSeat, "RESERVED", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVED", "SOLD"})
    void seatConflictReturns409AndRollsBackAllAcquisitions(String state) throws Exception {
        jdbc.update("update seats set status = ? where id = ?", state, secondSeat);
        request(body(eventId, "\"" + firstSeat + "\",\"" + secondSeat + "\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertSeat(firstSeat, "AVAILABLE", 0);
        assertSeat(secondSeat, state, 0);
        assertNoReservation();
    }

    @Test
    void missingEventReturns404WithoutWrites() throws Exception {
        request(body(UUID.randomUUID(), "\"" + firstSeat + "\""))
                .andExpect(status().isNotFound());
        assertSeat(firstSeat, "AVAILABLE", 0);
        assertNoReservation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SALES_CLOSED", "FINISHED", "CANCELLED"})
    void unavailableEventReturns409WithoutWrites(String state) throws Exception {
        jdbc.update("update events set status = ? where id = ?", state, eventId);
        request(body(eventId, "\"" + firstSeat + "\""))
                .andExpect(status().isConflict());
        assertSeat(firstSeat, "AVAILABLE", 0);
        assertNoReservation();
    }

    @Test
    void nullSeatReturns400WithoutAcquiringOtherSeats() throws Exception {
        request(body(eventId, "\"" + firstSeat + "\",null"))
                .andExpect(status().isBadRequest());
        assertSeat(firstSeat, "AVAILABLE", 0);
        assertNoReservation();
    }

    private org.springframework.test.web.servlet.ResultActions request(String body) throws Exception {
        return mvc.perform(post("/v1/reservations")
                .with(jwt().jwt(j -> j.subject(customerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(UUID event, String seatIds) {
        return "{\"eventId\":\"" + event + "\",\"seatIds\":[" + seatIds + "]}";
    }

    private void insertSeat(UUID id, String number, BigDecimal price) {
        jdbc.update("insert into seats(id, event_id, section, seat_row, seat_number, price, currency) values (?, ?, 'Floor', 'A', ?, ?, 'BRL')",
                id, eventId, number, price);
    }

    private void assertSeat(UUID id, String state, long version) {
        var row = jdbc.queryForMap("select status, version from seats where id = ?", id);
        assertThat(row.get("status")).isEqualTo(state);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(version);
    }

    private void assertNoReservation() {
        assertThat(jdbc.queryForObject("select count(*) from reservations where customer_id = ?", Long.class, customerId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reserved_seats where seat_id in (?, ?)", Long.class, firstSeat, secondSeat)).isZero();
    }
}
