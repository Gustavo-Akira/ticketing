package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.ConcurrentSeatModificationException;
import br.com.gustavoakira.ticketing.core.event.application.EventNotDraftException;
import br.com.gustavoakira.ticketing.core.event.application.EventNotFoundException;
import br.com.gustavoakira.ticketing.core.event.application.InvalidSeatConfigurationException;
import br.com.gustavoakira.ticketing.core.event.application.SeatNotFoundException;
import br.com.gustavoakira.ticketing.core.event.application.EventAccessDeniedException;
import br.com.gustavoakira.ticketing.core.event.domain.SeatLocationChangeException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = SeatController.class)
public class SeatExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(EventAccessDeniedException.class)
    public ProblemDetail forbidden(EventAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({EventNotFoundException.class, SeatNotFoundException.class})
    public ProblemDetail notFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidSeatConfigurationException.class)
    public ProblemDetail invalidInput(InvalidSeatConfigurationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }


    @ExceptionHandler(SeatLocationChangeException.class)
    public ProblemDetail locationConflict(SeatLocationChangeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail persistenceConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Seat update conflicts with an existing location");
    }

    @ExceptionHandler({ConcurrentSeatModificationException.class, OptimisticLockingFailureException.class})
    public ProblemDetail concurrentModification(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Seat was modified by another request; reload it before updating");
    }

    @ExceptionHandler(EventNotDraftException.class)
    public ProblemDetail eventNotDraft(EventNotDraftException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
