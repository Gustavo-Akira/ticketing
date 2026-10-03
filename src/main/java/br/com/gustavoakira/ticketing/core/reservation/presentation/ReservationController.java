package br.com.gustavoakira.ticketing.core.reservation.presentation;

import br.com.gustavoakira.ticketing.core.reservation.application.CreateReservationUseCase;
import br.com.gustavoakira.ticketing.core.reservation.presentation.dto.CreateReservationRequest;
import br.com.gustavoakira.ticketing.core.reservation.presentation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reservations")
public class ReservationController {
    private final CreateReservationUseCase createReservationUseCase;
    public ReservationController(CreateReservationUseCase createReservationUseCase) {
        this.createReservationUseCase = createReservationUseCase;
    }

    @PostMapping()
    public ResponseEntity<ReservationResponse> createReservation(@RequestBody @Valid CreateReservationRequest createReservationRequest, @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        ReservationResponse response = ReservationResponse.from(createReservationUseCase.createReservation(createReservationRequest.toCommand(customerId)));
        return ResponseEntity.created(URI.create("/v1/reservations/" + response.id())).body(response);
    }
}
