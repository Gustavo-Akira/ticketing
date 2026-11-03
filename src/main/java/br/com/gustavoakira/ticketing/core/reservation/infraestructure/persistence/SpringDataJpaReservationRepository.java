package br.com.gustavoakira.ticketing.core.reservation.infraestructure.persistence;

import br.com.gustavoakira.ticketing.core.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SpringDataJpaReservationRepository extends JpaRepository<JpaReservationEntity, UUID> {
    @Modifying
    @Query(value="UPDATE reservations SET status=:newStatus WHERE id=:id AND status=:expectedStatus",nativeQuery=true)
    int updateStatusWitExpectedStatus(UUID id, String expectedStatus, String newStatus);
}
