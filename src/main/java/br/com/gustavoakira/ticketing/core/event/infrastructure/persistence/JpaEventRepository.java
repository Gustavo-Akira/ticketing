package br.com.gustavoakira.ticketing.core.event.infrastructure.persistence;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import br.com.gustavoakira.ticketing.core.event.port.EventRepository;
import br.com.gustavoakira.ticketing.core.event.port.PageResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventRepository implements EventRepository {
    private final SpringDataEventRepository events;

    public JpaEventRepository(SpringDataEventRepository events) {
        this.events = events;
    }

    @Override
    public Event save(Event event) {
        return events.saveAndFlush(EventJpaEntity.fromDomain(event)).toDomain();
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return events.findById(id).map(EventJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return events.existsById(id);
    }

    @Override
    public PageResult<Event> findAll(int page, int size) {
        var result = events.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new PageResult<>(result.getContent().stream().map(EventJpaEntity::toDomain).toList(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public int updateDetails(UUID id, String name, Instant startsAt) {
        return events.updateDetails(id, name, startsAt);
    }

    @Override
    public Optional<Event> getEventByIdForUpdate(UUID eventId) {
        return events.getEventByIdForUpdate(eventId).map(EventJpaEntity::toDomain);
    }
}