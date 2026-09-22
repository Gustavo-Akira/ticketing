package br.com.gustavoakira.ticketing.core.event.presentation;

import br.com.gustavoakira.ticketing.core.event.application.*;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events/{eventId}/seats")
public class SeatController {
    private final GetSeatUseCase get;
    private final ListSeatsUseCase list;
    private final UpdateSeatUseCase update;
    private final CreateSeatsBatchUseCase create;

    public SeatController(GetSeatUseCase get, ListSeatsUseCase list, UpdateSeatUseCase update, CreateSeatsBatchUseCase create) {
        this.get = get;
        this.list = list;
        this.update = update;
        this.create = create;
    }

    @GetMapping("/{id}")
    public SeatResult get(@PathVariable UUID eventId, @PathVariable UUID id) {
        return get.execute(eventId, id);
    }

    @GetMapping
    public SeatPage list(@PathVariable UUID eventId, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return list.execute(eventId, page, size);
    }

    @PutMapping("/{id}")
    public SeatResult update(@PathVariable UUID eventId, @PathVariable UUID id,
                             @RequestBody SeatRequest request) {
        return update.execute(eventId, id, request.toDetails(), request.expectedVersion());
    }

    @PostMapping("create-seats")
    public ResponseEntity<Void> createSeats(@PathVariable UUID eventId, @RequestBody SeatBatchCreationRequest request) {
        create.execute(eventId, request.toCommand());
        return ResponseEntity.ok().build();
    }
}
