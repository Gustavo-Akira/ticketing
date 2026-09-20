package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChangeEventStatusToAvailableUseCase {

    private final EventRepository eventRepository;

    public ChangeEventStatusToAvailableUseCase(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void execute(UUID eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(()->new EventNotFoundException(eventId));
        EventStatus expectedStatus = event.getStatus();
        event.changeStatusToAvailable();
        int rowUpdated = eventRepository.updateEventStatusWithExpectedStatus(eventId, event.getStatus(),expectedStatus);
        if(rowUpdated == 0){
            throw new ConcurrentEventModificationException("Conflict update not merged");
        }
    }
}
