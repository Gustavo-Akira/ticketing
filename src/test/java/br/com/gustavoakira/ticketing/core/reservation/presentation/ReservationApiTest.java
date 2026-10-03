package br.com.gustavoakira.ticketing.core.reservation.presentation;

import br.com.gustavoakira.ticketing.core.identity.infrastructure.security.SecurityConfiguration;
import br.com.gustavoakira.ticketing.core.identity.infrastructure.security.SecurityProblemHandler;
import br.com.gustavoakira.ticketing.core.reservation.application.*;
import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservedSeat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class ReservationApiTest {
    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID EVENT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID FIRST = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SECOND = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String BODY = """
            {"eventId":"%s","seatIds":["%s","%s"]}
            """.formatted(EVENT, FIRST, SECOND);

    @Autowired MockMvc mvc;
    @MockitoBean CreateReservationUseCase create;
    @MockitoBean JwtDecoder decoder;

    @Test
    void createsReservationWithAuthenticatedCustomerPricesExpirationAndLocation() throws Exception {
        var reservation = givenReservation();
        mvc.perform(post("/v1/reservations").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/reservations/" + reservation.getId()))
                .andExpect(jsonPath("$.id").value(reservation.getId().toString()))
                .andExpect(jsonPath("$.eventId").value(EVENT.toString()))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER.toString()))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-02T12:10:00Z"))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].seatId").value(FIRST.toString()))
                .andExpect(jsonPath("$.seats[0].price").value(120.50))
                .andExpect(jsonPath("$.seats[0].currency").value("BRL"))
                .andExpect(jsonPath("$.seats[1].seatId").value(SECOND.toString()))
                .andExpect(jsonPath("$.seats[1].price").value(75.25))
                .andExpect(jsonPath("$.seats[1].currency").value("USD"));
        var command = org.mockito.ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(create).createReservation(command.capture());
        assertThat(command.getValue().eventId()).isEqualTo(EVENT);
        assertThat(command.getValue().customerId()).isEqualTo(CUSTOMER);
        assertThat(command.getValue().seatIds()).containsExactlyInAnyOrder(FIRST, SECOND);
    }

    @Test
    void bodyCannotOverrideAuthenticatedCustomer() throws Exception {
        givenReservation();
        String body = BODY.replace("{", "{\"customerId\":\"" + UUID.randomUUID() + "\",");
        mvc.perform(post("/v1/reservations").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER.toString()));
        var command = org.mockito.ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(create).createReservation(command.capture());
        assertThat(command.getValue().customerId()).isEqualTo(CUSTOMER);
    }

    @Test
    void anonymousRequestIsRejectedBeforeCreatingReservation() throws Exception {
        mvc.perform(post("/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        verifyNoInteractions(create);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ORGANIZER", "USER"})
    void roleWithoutCustomerCannotCreateReservation(String role) throws Exception {
        mvc.perform(post("/v1/reservations")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(create);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidInputReturns400WithoutCallingUseCase(String body) throws Exception {
        mvc.perform(post("/v1/reservations").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(create);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("", "null", "{", "{}",
                BODY.replace("\"" + EVENT + "\"", "null"),
                BODY.replace(EVENT.toString(), "not-a-uuid"),
                "{\"seatIds\":[\"" + FIRST + "\"]}",
                "{\"eventId\":\"" + EVENT + "\"}",
                "{\"eventId\":\"" + EVENT + "\",\"seatIds\":null}",
                "{\"eventId\":\"" + EVENT + "\",\"seatIds\":[]}",
                "{\"eventId\":\"" + EVENT + "\",\"seatIds\":[null]}",
                BODY.replace("\"" + FIRST + "\"", "null"),
                BODY.replace(FIRST.toString(), "not-a-uuid"));
    }

    @Test
    void missingEventReturns404() throws Exception {
        assertBusinessError(new EventNotFoundException("Event not found"), 404);
    }

    @Test
    void unavailableEventReturns409() throws Exception {
        assertBusinessError(new EventNotAvailableException("Event not available"), 409);
    }

    @Test
    void unavailableSeatReturns409() throws Exception {
        assertBusinessError(new SeatUnavailableException("Seat unavailable"), 409);
    }

    private void assertBusinessError(RuntimeException exception, int expectedStatus) throws Exception {
        when(create.createReservation(any())).thenThrow(exception);
        mvc.perform(post("/v1/reservations").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()));
    }

    private Reservation givenReservation() {
        var reservation = new Reservation(EVENT, CUSTOMER,
                List.of(new ReservedSeat(FIRST, new BigDecimal("120.50"), "BRL"),
                        new ReservedSeat(SECOND, new BigDecimal("75.25"), "USD")),
                Instant.parse("2026-10-02T12:00:00Z"));
        when(create.createReservation(any())).thenReturn(reservation);
        return reservation;
    }

    private RequestPostProcessor customer() {
        return jwt().jwt(j -> j.subject(CUSTOMER.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
