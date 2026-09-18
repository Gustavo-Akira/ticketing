package br.com.gustavoakira.ticketing.core.event.infrastructure;

import br.com.gustavoakira.ticketing.core.event.domain.Event;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {}
