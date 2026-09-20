package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.*;

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
    private final ChangeEventStatusToAvailableUseCase change;

    public EventController(CreateEventUseCase create, GetEventUseCase get,
                           ListEventsUseCase list, UpdateEventUseCase update, ChangeEventStatusToAvailableUseCase change) {
        this.create = create;
        this.get = get;
        this.list = list;
        this.update = update;
        this.change = change;
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

    @PatchMapping("/{id}/status/available")
    public ResponseEntity<Void>  changeStatus(@PathVariable UUID id) {
        change.execute(id);
        return ResponseEntity.noContent().build();
    }
}
