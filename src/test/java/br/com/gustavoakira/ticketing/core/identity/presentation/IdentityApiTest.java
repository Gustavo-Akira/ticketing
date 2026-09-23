package br.com.gustavoakira.ticketing.core.identity.presentation;

import br.com.gustavoakira.ticketing.core.identity.application.*;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Testcontainers
class IdentityApiTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateAccountUseCase create;
    @Autowired AccessTokenIssuer issuer;
    @Autowired UserRepository users;
    MockMvc mvc;
    final JsonMapper json = new JsonMapper();
    static final String REGISTER = "{\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"a valid password\"}";
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); jdbc.update("delete from users"); }
    @Test void registerLoginRefreshReplayAndLogout() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ana@example.com\",\"password\":\"a valid password\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String access = field(login, "accessToken"), original = field(login, "refreshToken");
        mvc.perform(get("/events").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        var rotated = tokenCall("refresh", original).andExpect(status().isOk()).andReturn();
        tokenCall("refresh", original).andExpect(status().isUnauthorized());
        tokenCall("refresh", field(rotated, "refreshToken")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from refresh_sessions where revoked_at is not null", Integer.class)).isEqualTo(1);
        tokenCall("logout", original).andExpect(status().isNoContent());
        tokenCall("logout", "A".repeat(43)).andExpect(status().isNoContent());
        mvc.perform(get("/events").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
    }
    @Test void grantRequiresAdminAndOnlyNewTokensReceiveRole() throws Exception {
        var customer = create.execute("Ana", "ana@example.com", "a valid password", Set.of(Role.CUSTOMER));
        var admin = create.execute("Admin", "admin@example.com", "a valid password", Set.of(Role.ADMIN));
        String customerToken = issuer.issue(customer), adminToken = issuer.issue(admin);
        var session = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ana@example.com\",\"password\":\"a valid password\"}"))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(put("/users/{id}/roles/organizer", customer.getId()).header("Authorization", "Bearer " + customerToken)).andExpect(status().isForbidden());
        mvc.perform(put("/users/{id}/roles/organizer", customer.getId()).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
        mvc.perform(put("/users/{id}/roles/organizer", UUID.randomUUID()).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
        var body = "{\"name\":\"Concert\",\"startsAt\":\"2027-01-01T00:00:00Z\"}";
        mvc.perform(post("/events").header("Authorization", "Bearer " + customerToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/events").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        var renewed = tokenCall("refresh", field(session, "refreshToken")).andExpect(status().isOk()).andReturn();
        String fresh = field(renewed, "accessToken");
        mvc.perform(post("/events").header("Authorization", "Bearer " + fresh).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }
    @ParameterizedTest @ValueSource(strings = {"null", "{}", "{", "{\"roles\":[\"ADMIN\"]}", "{\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"a valid password\",\"roles\":[\"ADMIN\"]}"})
    void invalidRegistrationNeverWrites(String body) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }
    @Test void loginAndTokenErrorsDoNotLeakSecrets() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"absent@example.com\",\"password\":\"secret\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid credentials"));
        tokenCall("refresh", "short").andExpect(status().isBadRequest());
        tokenCall("refresh", "A".repeat(43)).andExpect(status().isUnauthorized());
        mvc.perform(get("/events")).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/events").header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
        mvc.perform(get("/events").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void registrationRequiresStringFields() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":123,\"email\":\"ana@example.com\",\"password\":\"a valid password\"}"))
            .andExpect(status().isBadRequest());
        assertThat(users.findByEmail("ana@example.com")).isEmpty();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ana@example.com\",\"password\":123456789012}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":1234567890123456789012345678901234567890123}"))
            .andExpect(status().isBadRequest());
    }
    @Test void organizerProtectsEveryExistingWriteRoute() throws Exception {
        var customer = create.execute("Customer", "customer@example.com", "a valid password", Set.of(Role.CUSTOMER));
        var organizer = create.execute("Organizer", "organizer@example.com", "a valid password", Set.of(Role.ORGANIZER));
        String customerJwt = issuer.issue(customer), organizerJwt = issuer.issue(organizer);
        String event = UUID.randomUUID().toString(), seat = UUID.randomUUID().toString();
        var routes = List.of(
            new String[]{"POST", "/events"}, new String[]{"PUT", "/events/" + event},
            new String[]{"PATCH", "/events/" + event + "/status/available"},
            new String[]{"POST", "/events/" + event + "/seats/create-seats"},
            new String[]{"PUT", "/events/" + event + "/seats/" + seat});
        for (var route : routes) {
            var method = org.springframework.http.HttpMethod.valueOf(route[0]);
            String body = route[1].endsWith("/create-seats") ? "{\"sections\":[]}" : "{}";
            mvc.perform(request(method, route[1]).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
            mvc.perform(request(method, route[1]).header("Authorization", "Bearer " + customerJwt).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
            var result = mvc.perform(request(method, route[1]).header("Authorization", "Bearer " + organizerJwt).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
            assertThat(result.getStatus()).isIn(400, 404);
        }
        mvc.perform(delete("/events/" + event).header("Authorization", "Bearer " + organizerJwt)).andExpect(status().isForbidden());
        for (String path : List.of("/events", "/events/" + event, "/events/" + event + "/seats", "/events/" + event + "/seats/" + seat)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            var status = mvc.perform(get(path).header("Authorization", "Bearer " + customerJwt)).andReturn().getResponse().getStatus();
            assertThat(status).isIn(200, 404);
        }
    }
    private ResultActions tokenCall(String operation, String token) throws Exception {
        return mvc.perform(post("/auth/" + operation).contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + token + "\"}"));
    }
    private String field(MvcResult result, String name) throws Exception { return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get(name).asString(); }
}
