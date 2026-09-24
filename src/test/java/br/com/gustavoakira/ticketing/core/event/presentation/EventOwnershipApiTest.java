package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.identity.application.CreateAccountUseCase;
import br.com.gustavoakira.ticketing.core.identity.domain.Role;
import br.com.gustavoakira.ticketing.core.identity.port.AccessTokenIssuer;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Testcontainers
class EventOwnershipApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateAccountUseCase accounts;
    @Autowired AccessTokenIssuer issuer;
    MockMvc mvc;
    UUID ownerId, eventId, seatId;
    String ownerToken, otherToken;
    static final String EVENT = "{\"name\":\"Concert\",\"startsAt\":\"2027-01-10T20:00:00Z\"}";
    static final String SEAT = "{\"section\":\"Floor\",\"row\":\"A\",\"number\":\"1\",\"price\":150,\"currency\":\"BRL\",\"expectedVersion\":0}";
    static final String BATCH = "{\"sections\":[{\"name\":\"VIP\",\"rowCount\":1,\"seatPerRow\":1,\"price\":100,\"currency\":\"BRL\"}]}";

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from seats");
        jdbc.update("delete from events");
        jdbc.update("delete from users");
        var owner = accounts.execute("Owner", "owner@example.com", "a valid password", Set.of(Role.ORGANIZER));
        ownerId = owner.getId();
        ownerToken = issuer.issue(owner);
        otherToken = issuer.issue(accounts.execute("Other", "other@example.com", "a valid password", Set.of(Role.ORGANIZER, Role.ADMIN)));
        String location = mvc.perform(auth(post("/events").content(EVENT), ownerToken))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        eventId = UUID.fromString(location.substring("/events/".length()));
        seatId = UUID.randomUUID();
        jdbc.update("insert into seats(id,event_id,section,seat_row,seat_number,price,currency) values (?,?,'Floor','A','1',100,'BRL')", seatId, eventId);
    }

    @Test void creationBindsVerifiedSubjectAndReadsExposeOwner() throws Exception {
        assertThat(jdbc.queryForObject("select owner_id from events where id = ?", UUID.class, eventId)).isEqualTo(ownerId);
        mvc.perform(auth(get("/events/{id}", eventId), otherToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(ownerId.toString()));
        mvc.perform(auth(get("/events"), otherToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].ownerId").value(ownerId.toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"update", "publish", "batch", "seat"})
    void otherOrganizerCannotChangeAnything(String operation) throws Exception {
        var beforeEvent = jdbc.queryForMap("select * from events where id = ?", eventId);
        var beforeSeats = jdbc.queryForList("select * from seats order by id");
        mvc.perform(auth(operation(operation), otherToken)).andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(jdbc.queryForMap("select * from events where id = ?", eventId)).isEqualTo(beforeEvent);
        assertThat(jdbc.queryForList("select * from seats order by id")).isEqualTo(beforeSeats);
    }

    @ParameterizedTest @ValueSource(strings = {"update", "publish", "batch", "seat"})
    void ownerCanManageEvent(String operation) throws Exception {
        mvc.perform(auth(operation(operation), ownerToken)).andExpect(status().is(operation.equals("publish") ? 204 : 200));
    }

    @ParameterizedTest @ValueSource(strings = {"null", "\"00000000-0000-4000-8000-000000000001\""})
    void ownerCannotBeSuppliedOrReplaced(String value) throws Exception {
        var before = jdbc.queryForMap("select * from events where id = ?", eventId);
        String body = EVENT.substring(0, EVENT.length() - 1) + ",\"ownerId\":" + value + "}";
        mvc.perform(auth(post("/events").content(body), ownerToken)).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/events/{id}", eventId).content(body), ownerToken)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select * from events where id = ?", eventId)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"update", "publish", "batch", "seat"})
    void legacyIsReadableButCannotBeManaged(String operation) throws Exception {
        jdbc.update("update events set owner_id = null where id = ?", eventId);
        mvc.perform(auth(get("/events/{id}", eventId), ownerToken)).andExpect(status().isOk());
        mvc.perform(auth(operation(operation), ownerToken)).andExpect(status().isForbidden());
    }

    @Test void ownershipPrecedesSeatAndStatusConflicts() throws Exception {
        jdbc.update("update seats set version = 1 where id = ?", seatId);
        mvc.perform(auth(operation("seat"), otherToken)).andExpect(status().isForbidden());
        jdbc.update("delete from seats");
        mvc.perform(auth(operation("publish"), otherToken)).andExpect(status().isForbidden());
        jdbc.update("update events set status = 'AVAILABLE' where id = ?", eventId);
        mvc.perform(auth(operation("batch"), otherToken)).andExpect(status().isForbidden());
    }

    @Test void seatFromAnotherEventCannotBeAddressedUnderOwnedEvent() throws Exception {
        UUID foreignEvent = UUID.randomUUID();
        jdbc.update("insert into events(id,name,starts_at) values (?,'Legacy',now())", foreignEvent);
        jdbc.update("update seats set event_id = ? where id = ?", foreignEvent, seatId);
        var before = jdbc.queryForMap("select * from seats where id = ?", seatId);
        mvc.perform(auth(operation("seat"), ownerToken)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForMap("select * from seats where id = ?", seatId)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"update", "publish", "batch", "seat"})
    void missingEventReturns404AndMissingAuthentication401(String operation) throws Exception {
        eventId = UUID.randomUUID();
        mvc.perform(operation(operation).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(auth(operation(operation), ownerToken)).andExpect(status().isNotFound());
    }

    MockHttpServletRequestBuilder operation(String name) {
        return switch (name) {
            case "update" -> put("/events/{id}", eventId).content(EVENT);
            case "publish" -> patch("/events/{id}/status/available", eventId);
            case "batch" -> post("/events/{id}/seats/create-seats", eventId).content(BATCH);
            case "seat" -> put("/events/{eventId}/seats/{id}", eventId, seatId).content(SEAT);
            default -> throw new IllegalArgumentException(name);
        };
    }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
