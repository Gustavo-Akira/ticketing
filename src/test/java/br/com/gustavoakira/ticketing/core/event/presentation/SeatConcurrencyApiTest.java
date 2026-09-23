package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
class SeatConcurrencyApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SeatRepository seats;

    @Test
    void simultaneousEditsOfTheSameVersionCommitExactlyOneAndReturn409ForTheOther() throws Exception {
        var eventId = UUID.randomUUID();
        var seatId = UUID.randomUUID();
        jdbc.update("insert into events(id, name, starts_at, status) values (?, 'Concert', '2027-01-01T00:00:00Z', 'DRAFT')", eventId);
        jdbc.update("""
                insert into seats(id, event_id, section, seat_row, seat_number, price, currency, status, version)
                values (?, ?, 'Floor', 'A', '1', 100, 'BRL', 'AVAILABLE', 0)
                """, seatId, eventId);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var path = "/events/" + eventId + "/seats/" + seatId;
        var bothHaveRead = new CyclicBarrier(2);
        // Keep the real database reads and writes. Only synchronize the point after
        // each independent request transaction has loaded version zero.
        doAnswer(invocation -> {
            var snapshot = invocation.callRealMethod();
            bothHaveRead.await(15, TimeUnit.SECONDS);
            return snapshot;
        }).when(seats).findByIdAndEventId(seatId, eventId);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var priceEdit = executor.submit(() -> update(mvc, path, """
                    {"section":"Floor","row":"A","number":"1","price":150,"currency":"BRL","expectedVersion":0}
                    """));
            var locationEdit = executor.submit(() -> update(mvc, path, """
                    {"section":"Floor","row":"B","number":"1","price":100,"currency":"BRL","expectedVersion":0}
                    """));
            var priceResult = priceEdit.get(30, TimeUnit.SECONDS);
            var locationResult = locationEdit.get(30, TimeUnit.SECONDS);
            assertThat(new int[]{priceResult.getResponse().getStatus(), locationResult.getResponse().getStatus()})
                    .containsExactlyInAnyOrder(200, 409);
            var conflict = priceResult.getResponse().getStatus() == 409 ? priceResult : locationResult;
            content().contentTypeCompatibleWith("application/problem+json").match(conflict);
            jsonPath("$.status").value(409).match(conflict);

            var stored = jdbc.queryForMap("select * from seats where id = ?", seatId);
            boolean priceWon = priceResult.getResponse().getStatus() == 200;
            assertThat((java.math.BigDecimal) stored.get("price")).isEqualByComparingTo(priceWon ? "150" : "100");
            assertThat(stored.get("seat_row")).isEqualTo(priceWon ? "A" : "B");
            assertThat(stored.get("currency")).isEqualTo("BRL");
            assertThat(stored.get("status")).isEqualTo("AVAILABLE");
            assertThat(((Number) stored.get("version")).longValue()).isEqualTo(1L);
            var success = priceWon ? priceResult : locationResult;
            jsonPath("$.version").value(1).match(success);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MvcResult update(MockMvc mvc, String path, String body) throws Exception {
        return mvc.perform(put(path).with(user("editor").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }
}
