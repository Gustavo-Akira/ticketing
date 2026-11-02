package br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence;

import br.com.gustavoakira.ticketing.core.reservation.domain.Reservation;
import br.com.gustavoakira.ticketing.core.reservation.domain.ReservationStatus;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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

    @Override
    public int updateExpectedStatusToNewStatus(UUID id, ReservationStatus expectedStatus, ReservationStatus newStatus) {
        return springDataJpaReservationRepository.updateStatusWitExpectedStatus(id,expectedStatus.name(),newStatus.name());
    }


    @Override
    public Optional<Reservation> findReservationById(UUID id) {
        return springDataJpaReservationRepository.findById(id).map(JpaReservationEntity::toDomain);
    }
}
