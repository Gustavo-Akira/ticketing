package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventStatusConflictApiTest {
    @Test
    void concurrentModificationReturns409ProblemDetail() throws Exception {
        var change = mock(ChangeEventStatusToAvailableUseCase.class);
        var id = UUID.randomUUID();
        doThrow(new ConcurrentEventModificationException("Conflict update not merged")).when(change).execute(id);
        var controller = new EventController(mock(CreateEventUseCase.class), mock(GetEventUseCase.class),
                mock(ListEventsUseCase.class), mock(UpdateEventUseCase.class), change);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new EventExceptionHandler()).build();

        mvc.perform(patch("/events/{id}/status/available", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Conflict update not merged"));
    }
}
