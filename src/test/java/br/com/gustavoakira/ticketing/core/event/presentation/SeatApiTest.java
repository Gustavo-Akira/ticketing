package br.com.gustavoakira.ticketing.core.event.presentation;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
class SeatApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    UUID eventId;
    UUID seatId;
    static final String BODY = """
            {"section":"Floor","row":"A","number":"1","price":150.50,"currency":"USD"}
            """;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from seats");
        jdbc.update("delete from events");
        eventId = event();
        seatId = seat(eventId, "1");
    }

    @Test
    void readsSeatAndListsOnlyItsEventWithStablePagination() throws Exception {
        seat(event(), "1");
        UUID second = seat(eventId, "2");
        var ordered = jdbc.queryForList("select id from seats where event_id = ? order by id", UUID.class, eventId);
        mvc.perform(get(path()).with(user("reader")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(seatId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.section").value("Floor")).andExpect(jsonPath("$.row").value("A"))
                .andExpect(jsonPath("$.number").value("1")).andExpect(jsonPath("$.price").value(100))
                .andExpect(jsonPath("$.currency").value("BRL")).andExpect(jsonPath("$.status").value("AVAILABLE"));
        for (int page = 0; page < 2; page++) {
            mvc.perform(get(base() + "?page=" + page + "&size=1").with(user("reader")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(ordered.get(page).toString()))
                    .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        }
        mvc.perform(get(base() + "?page=2&size=1").with(user("reader")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/events/" + event() + "/seats").with(user("reader")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void draftAllowsLocationUpdateAndPreservesIdentityAndStatus() throws Exception {
        jdbc.update("update seats set status = 'RESERVED' where id = ?", seatId);
        update(BODY.replace("Floor", "Balcony").replace("\"A\"", "\"B\"").replace("\"1\"", "\"9\""), 200);
        mvc.perform(get(path()).with(user("reader")))
                .andExpect(jsonPath("$.section").value("Balcony")).andExpect(jsonPath("$.row").value("B"))
                .andExpect(jsonPath("$.number").value("9")).andExpect(jsonPath("$.price").value(150.50))
                .andExpect(jsonPath("$.currency").value("USD")).andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));
        assertThat(jdbc.queryForObject("select version from seats where id = ?", Long.class, seatId)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "SALES_CLOSED", "FINISHED", "CANCELLED"})
    void nonDraftRejectsEachLocationChangeAtomicallyButAllowsPriceAndCurrency(String eventStatus) throws Exception {
        jdbc.update("update events set status = ? where id = ?", eventStatus, eventId);
        var before = jdbc.queryForMap("select * from seats where id = ?", seatId);
        for (String body : new String[]{BODY.replace("Floor", "Balcony"), BODY.replace("\"A\"", "\"B\""), BODY.replace("\"1\"", "\"2\"")}) {
            update(body, 409);
            assertThat(jdbc.queryForMap("select * from seats where id = ?", seatId)).isEqualTo(before);
        }
        update(BODY, 200);
        assertThat(jdbc.queryForObject("select currency from seats where id = ?", String.class, seatId)).isEqualTo("USD");
    }

    @Test
    void missingAndWrongEventSeatsReturn404() throws Exception {
        for (UUID id : new UUID[]{event(), UUID.randomUUID()}) {
            mvc.perform(get("/events/" + id + "/seats/" + seatId).with(user("reader")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
            mvc.perform(put("/events/" + id + "/seats/" + seatId).with(user("editor")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        }
        seatId = UUID.randomUUID();
        mvc.perform(get(path()).with(user("reader"))).andExpect(status().isNotFound());
        update(BODY, 404);
        mvc.perform(get("/events/" + UUID.randomUUID() + "/seats").with(user("reader")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateLocationReturnsConflictWithoutChangingSeat() throws Exception {
        seat(eventId, "2");
        update(BODY.replace("\"1\"", "\"2\""), 409);
        assertThat(jdbc.queryForObject("select currency from seats where id = ?", String.class, seatId)).isEqualTo("BRL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"section\":\"Floor\"}"})
    void invalidBodyReturns400(String body) throws Exception { update(body, 400); }

    @Test
    void rejectsInvalidFieldsWithoutWriting() throws Exception {
        var before = jdbc.queryForMap("select * from seats where id = ?", seatId);
        for (String body : new String[]{BODY.replace("Floor", " "), BODY.replace("Floor", "s".repeat(101)),
                BODY.replace("\"A\"", "null"), BODY.replace("\"1\"", "\"\""),
                BODY.replace("150.50", "-1"), BODY.replace("150.50", "1.001"),
                BODY.replace("150.50", "10000000000"), BODY.replace("150.50", "null"),
                BODY.replace("USD", "ZZZ"), BODY.replace("\"USD\"", "null")}) {
            update(body, 400);
            assertThat(jdbc.queryForMap("select * from seats where id = ?", seatId)).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-1", "size=101", "page=x", "size=x", "page=2147483647&size=100"})
    void invalidPaginationReturns400(String query) throws Exception {
        mvc.perform(get(base() + "?" + query).with(user("reader")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void securityAndMalformedIdsAndUnsupportedCreation() throws Exception {
        mvc.perform(get(base()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(put(path()).with(user("editor")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(get(base() + "/bad-id").with(user("reader"))).andExpect(status().isBadRequest());
        mvc.perform(post(base()).with(user("editor")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isMethodNotAllowed());
    }

    private void update(String body, int expected) throws Exception {
        mvc.perform(put(path()).with(user("editor")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }
    private String base() { return "/events/" + eventId + "/seats"; }
    private String path() { return base() + "/" + seatId; }
    private UUID event() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', '2027-01-01T00:00:00Z', 'DRAFT')", id);
        return id;
    }
    private UUID seat(UUID event, String number) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into seats(id, event_id, section, seat_row, seat_number, price, currency, status, version) values (?, ?, 'Floor', 'A', ?, 100, 'BRL', 'AVAILABLE', 0)", id, event, number);
        return id;
    }
}
