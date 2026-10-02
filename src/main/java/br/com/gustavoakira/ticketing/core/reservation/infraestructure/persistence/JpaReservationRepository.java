package br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence;

import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaReservationRepository implements ReservationRepository {
    private final SpringDataJpaReservationRepository springDataJpaReservationRepository;

    public JpaReservationRepository(SpringDataJpaReservationRepository springDataJpaReservationRepository) {
        this.springDataJpaReservationRepository = springDataJpaReservationRepository;
    }

    @Override
    public Reservation createReservation(Reservation reservation) {
        return springDataJpaReservationRepository.saveAndFlush(JpaReservationEntity.fromDomain(reservation)).toDomain();
    }
}
