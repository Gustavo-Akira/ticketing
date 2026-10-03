package br.com.gustavoakira.ticketing.core.reservation.presentation;

import br.com.gustavoakira.ticketing.core.reservation.application.EventNotAvailableException;
import br.com.gustavoakira.ticketing.core.reservation.application.EventNotFoundException;
import br.com.gustavoakira.ticketing.core.reservation.application.SeatUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = ReservationController.class)
public class ReservationExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail notFound(EventNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({EventNotAvailableException.class, SeatUnavailableException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
