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
        var actorId = UUID.randomUUID();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "RS256").subject(actorId.toString()).build();
        doThrow(new ConcurrentEventModificationException("Conflict update not merged")).when(change).execute(id, actorId);
        var controller = new EventController(mock(CreateEventUseCase.class), mock(GetEventUseCase.class),
                mock(ListEventsUseCase.class), mock(UpdateEventUseCase.class), change);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new EventExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(org.springframework.security.oauth2.jwt.Jwt.class);
                    }
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                            org.springframework.web.method.support.ModelAndViewContainer container,
                            org.springframework.web.context.request.NativeWebRequest request,
                            org.springframework.web.bind.support.WebDataBinderFactory factory) {
                        return jwt;
                    }
                }).build();

        mvc.perform(patch("/events/{id}/status/available", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Conflict update not merged"));
    }
}
