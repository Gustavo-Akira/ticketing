package br.com.gustavoakira.ticketing.core.event.presentation;

import static br.com.gustavoakira.ticketing.core.event.support.OrganizerFixture.*;
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
class EventApiTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    static final String VALID = """
            {"name":"Concert","startsAt":"2027-01-10T20:00:00Z"}
            """;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from seats");
        jdbc.update("delete from events");
        br.com.gustavoakira.ticketing.core.event.support.OrganizerFixture.seed(jdbc);
    }

    @Test
    void creationReturnsLocationAndPersistsDraftWithDatabaseTimestamps() throws Exception {
        var response = mvc.perform(post("/events").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Concert"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse();
        String location = response.getHeader("Location");
        assertThat(location).startsWith("/events/");
        UUID id = UUID.fromString(location.substring("/events/".length()));
        assertThat(id.version()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select name from events where id = ?", String.class, id)).isEqualTo("Concert");
        mvc.perform(get(location).with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(content().json(response.getContentAsString()));
    }

    @Test
    void updateChangesOnlyEditableFieldsAndSetsAuditExplicitly() throws Exception {
        UUID id = seed("Before");
        jdbc.update("update events set status = 'AVAILABLE', updated_at = '2000-01-01T00:00:00Z' where id = ?", id);
        var before = jdbc.queryForMap("select * from events where id = ?", id);
        mvc.perform(put("/events/{id}", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Concert"))
                .andExpect(jsonPath("$.startsAt").value("2027-01-10T20:00:00Z"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        var after = jdbc.queryForMap("select * from events where id = ?", id);
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
        assertThat(after.get("name")).isEqualTo("Concert");
        mvc.perform(get("/events/{id}", id).with(user("reader")))
                .andExpect(jsonPath("$.name").value("Concert"));
    }

    @Test
    void listingIsPaginatedAndStableWithEmptyPages() throws Exception {
        UUID first = seed("First");
        UUID second = seed("Second");
        var ordered = jdbc.queryForList("select id from events order by id", UUID.class);
        assertThat(ordered).containsExactlyInAnyOrder(first, second);
        mvc.perform(get("/events?page=0&size=1").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ordered.getFirst().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/events?page=1&size=1").with(user("reader")))
                .andExpect(jsonPath("$.content[0].id").value(ordered.getLast().toString()));
        mvc.perform(get("/events?page=2&size=1").with(user("reader")))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void emptyListingUsesDefaults() throws Exception {
        mvc.perform(get("/events").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void missingEventsReturn404ForReadAndUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/events/{id}", id).with(user("reader")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/events/{id}", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertThat(jdbc.queryForObject("select count(*) from events", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"name\":\" \"}", "{\"name\":\"Concert\"}",
            "{\"startsAt\":\"2027-01-10T20:00:00Z\"}",
            "{\"name\":\"Concert\",\"startsAt\":\"invalid\"}", "null", "{"
    })
    void invalidBodyReturns400WithoutWriting(String body) throws Exception {
        UUID id = seed("Unchanged");
        mvc.perform(post("/events").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/events/{id}", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(jdbc.queryForObject("select name from events where id = ?", String.class, id)).isEqualTo("Unchanged");
        assertThat(jdbc.queryForObject("select count(*) from events", Long.class)).isEqualTo(1);
    }

    @Test
    void oversizedNameIsRejected() throws Exception {
        String body = VALID.replace("Concert", "a".repeat(256));
        mvc.perform(post("/events").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-1", "size=101", "page=x", "size=x", "page=2147483647&size=100"})
    void invalidPaginationReturns400(String query) throws Exception {
        mvc.perform(get("/events?" + query).with(user("reader")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mvc.perform(get("/events/not-a-uuid").with(user("reader")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void authenticationAndOrganizerRoleAreRequired() throws Exception {
        mvc.perform(get("/events").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(post("/events").with(user("editor"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    private UUID seed(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into events(id, name, starts_at, status) values (?, ?, '2027-01-01T00:00:00Z', 'DRAFT')", id, name);
        jdbc.update("update events set owner_id = ? where id = ?", OWNER, id);
        return id;
    }

    @Test
    void makingDraftAvailableReturns204AndPersistsStatus() throws Exception {
        UUID id = seed("Concert");
        seedSeat(id);
        jdbc.update("update events set updated_at = '2000-01-01T00:00:00Z' where id = ?", id);
        var before = jdbc.queryForMap("select * from events where id = ?", id);
        mvc.perform(patch("/events/{id}/status/available", id).with(organizer()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, id))
                .isEqualTo("AVAILABLE");
        var after = jdbc.queryForMap("select * from events where id = ?", id);
        assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("starts_at")).isEqualTo(before.get("starts_at"));
        mvc.perform(get("/events/{id}", id).with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.name").value("Concert"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "SALES_CLOSED", "FINISHED", "CANCELLED"})
    void makingNonDraftAvailableReturns400WithoutWriting(String eventStatus) throws Exception {
        UUID id = seed("Concert");
        seedSeat(id);
        jdbc.update("update events set status = ? where id = ?", eventStatus, id);
        mvc.perform(patch("/events/{id}/status/available", id).with(organizer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, id))
                .isEqualTo(eventStatus);
    }

    @Test
    void makingMissingEventAvailableReturns404() throws Exception {
        mvc.perform(patch("/events/{id}/status/available", UUID.randomUUID()).with(organizer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(jdbc.queryForObject("select count(*) from events", Long.class)).isZero();
    }

    @Test
    void statusChangeRejectsMalformedId() throws Exception {
        mvc.perform(patch("/events/not-a-uuid/status/available").with(organizer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void statusChangeRequiresAuthenticationAndOrganizer() throws Exception {
        UUID id = seed("Concert");
        mvc.perform(patch("/events/{id}/status/available", id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/events/{id}/status/available", id).with(user("editor")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, id)).isEqualTo("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eventWithoutSeatsReturns409WithoutWritingEvenWhenAnotherEventHasSeats(boolean otherHasSeats) throws Exception {
        UUID id = seed("Empty concert");
        if (otherHasSeats) {
            seedSeat(seed("Other concert"));
        }
        var before = jdbc.queryForMap("select * from events where id = ?", id);

        mvc.perform(patch("/events/{id}/status/available", id).with(organizer()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Event with id " + id + " has not seat"));

        assertThat(jdbc.queryForMap("select * from events where id = ?", id)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "SALES_CLOSED", "FINISHED", "CANCELLED"})
    void missingSeatsTakePrecedenceOverInvalidEventStatusWithoutWriting(String eventStatus) throws Exception {
        UUID id = seed("Empty concert");
        jdbc.update("update events set status = ? where id = ?", eventStatus, id);
        var before = jdbc.queryForMap("select * from events where id = ?", id);

        mvc.perform(patch("/events/{id}/status/available", id).with(organizer()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Event with id " + id + " has not seat"));

        assertThat(jdbc.queryForMap("select * from events where id = ?", id)).isEqualTo(before);
    }

    private void seedSeat(UUID eventId) {
        jdbc.update("""
                insert into seats(id, event_id, section, seat_row, seat_number, price, currency, status, version)
                values (?, ?, 'Floor', 'A', '1', 120.50, 'BRL', 'AVAILABLE', 0)
                """, UUID.randomUUID(), eventId);
    }
}
