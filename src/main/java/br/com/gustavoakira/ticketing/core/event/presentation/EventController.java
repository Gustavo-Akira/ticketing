package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.CreateEventUseCase;
import br.com.gustavoakira.ticketing.core.event.application.EventPage;
import br.com.gustavoakira.ticketing.core.event.application.EventResult;
import br.com.gustavoakira.ticketing.core.event.application.GetEventUseCase;
import br.com.gustavoakira.ticketing.core.event.application.ListEventsUseCase;
import br.com.gustavoakira.ticketing.core.event.application.UpdateEventUseCase;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {
    private final CreateEventUseCase create;
    private final GetEventUseCase get;
    private final ListEventsUseCase list;
    private final UpdateEventUseCase update;

    public EventController(CreateEventUseCase create, GetEventUseCase get,
                           ListEventsUseCase list, UpdateEventUseCase update) {
        this.create = create;
        this.get = get;
        this.list = list;
        this.update = update;
    }

    @PostMapping
    public ResponseEntity<EventResult> create(@RequestBody EventRequest request) {
        var result = create.execute(request.toDetails());
        return ResponseEntity.created(URI.create("/events/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public EventResult get(@PathVariable UUID id) {
        return get.execute(id);
    }

    @GetMapping
    public EventPage list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return list.execute(page, size);
    }

    @PutMapping("/{id}")
    public EventResult update(@PathVariable UUID id, @RequestBody EventRequest request) {
        return update.execute(id, request.toDetails());
    }
}
