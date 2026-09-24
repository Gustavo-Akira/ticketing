package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.ConcurrentEventModificationException;
import br.com.gustavoakira.ticketing.core.event.application.EventHasNotSeatException;
import br.com.gustavoakira.ticketing.core.event.application.EventNotDraftException;
import br.com.gustavoakira.ticketing.core.event.application.EventNotFoundException;
import br.com.gustavoakira.ticketing.core.event.application.EventAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = EventController.class)
public class EventExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(EventAccessDeniedException.class)
    public ProblemDetail forbidden(EventAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail notFound(EventNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(EventNotDraftException.class)
    public ProblemDetail eventNotDraft(EventNotDraftException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(EventHasNotSeatException.class)
    public ProblemDetail eventHasNotSeat(EventHasNotSeatException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ConcurrentEventModificationException.class)
    public ProblemDetail concurrentModification(ConcurrentEventModificationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
