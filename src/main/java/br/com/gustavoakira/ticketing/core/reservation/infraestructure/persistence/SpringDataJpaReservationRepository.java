package br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataJpaReservationRepository extends JpaRepository<JpaReservationEntity, UUID> {
}
