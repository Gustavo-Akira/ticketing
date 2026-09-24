package br.com.gustavoakira.ticketing.core.event.application;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.domain.EventStatus;
import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CreateSeatsBatchUseCase {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    public CreateSeatsBatchUseCase(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public void execute(UUID eventId, SeatBatchCreationCommand command, UUID actorId){
        Event event = eventRepository.getEventByIdForUpdate(eventId).orElseThrow(()->new EventNotFoundException(eventId));
        EventOwnership.requireOwner(event, actorId);
        if (event.getStatus() != EventStatus.DRAFT){
            throw new EventNotDraftException("Event status must be DRAFT to update/create seats");
        }
        int totalSeats = command.sections().stream()
                .mapToInt(section -> section.rowCount() * section.seatPerRow())
                .sum();

        List<Seat> seats = new ArrayList<>(totalSeats);
        for(SeatBatchCreationCommand.SeatSectionConfiguration section: command.sections()){
            if (section.rowCount() > 26) {
                throw new InvalidSeatConfigurationException(
                        "A section cannot contain more than 26 rows"
                );
            }
            for (int rowIndex = 0; rowIndex < section.rowCount(); rowIndex++) {
                String row = String.valueOf((char) ('A' + rowIndex));

                for (int seatNumber = 1; seatNumber <= section.seatPerRow(); seatNumber++) {
                    seats.add(new Seat(
                            eventId,
                            section.name(),
                            row,
                            String.valueOf(seatNumber),
                            section.price(),
                            section.currency()
                    ));
                }
            }
        }
        seatRepository.saveAll(seats);
    }
}
