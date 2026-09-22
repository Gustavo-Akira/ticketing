package br.com.gustavoakira.ticketing.core.event.infrastructure.persistence;

import br.com.gustavoakira.ticketing.core.event.domain.Seat;
import br.com.gustavoakira.ticketing.core.event.port.PageResult;
import br.com.gustavoakira.ticketing.core.event.port.SeatRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSeatRepository implements SeatRepository {
    private final SpringDataSeatRepository seats;

    public JpaSeatRepository(SpringDataSeatRepository seats) {
        this.seats = seats;
    }

    @Override
    public Seat save(Seat seat) {
        return seats.saveAndFlush(SeatJpaEntity.fromDomain(seat)).toDomain();
    }

    @Override
    public void saveAll(List<Seat> models) {
        seats.saveAll(models.stream().map(SeatJpaEntity::fromDomain).toList());
    }

    @Override
    public Optional<Seat> findById(UUID id) {
        return seats.findById(id).map(SeatJpaEntity::toDomain);
    }

    @Override
    public List<Seat> findByEventId(UUID eventId) {
        return seats.findByEventId(eventId).stream().map(SeatJpaEntity::toDomain).toList();
    }

    @Override
    public PageResult<Seat> findByEventId(UUID eventId, int page, int size) {
        var result = seats.findByEventId(eventId, PageRequest.of(page, size, Sort.by("id")));
        return new PageResult<>(result.getContent().stream().map(SeatJpaEntity::toDomain).toList(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public Optional<Seat> findByIdAndEventId(UUID id, UUID eventId) {
        return seats.findByIdAndEventId(id, eventId).map(SeatJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return seats.existsAvailableSeatByEventId(eventId);
    }
}