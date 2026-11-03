package br.com.gustavoakira.ticketing.core.payment.application;

import br.com.gustavoakira.ticketing.core.payment.domain.*;
import br.com.gustavoakira.ticketing.core.payment.port.PaymentRepository;
import br.com.gustavoakira.ticketing.core.reservation.domain.*;
import br.com.gustavoakira.ticketing.core.reservation.port.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentUseCaseTest {
    @Mock PaymentRepository payments;
    @Mock ReservationRepository reservations;
    private CreatePaymentUseCase useCase;
    private Reservation reservation;
    private CreatePaymentCommand command;

    @BeforeEach
    void setUp() {
        useCase = new CreatePaymentUseCase(payments, reservations);
        reservation = new Reservation(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ReservedSeat(UUID.randomUUID(), new BigDecimal("100.25"), "BRL"),
                new ReservedSeat(UUID.randomUUID(), new BigDecimal("50.50"), "BRL")), Instant.now());
        command = new CreatePaymentCommand(reservation.getId(), "checkout", PaymentType.CREDIT);
    }

    @Test
    void createsPendingPaymentWithTotalAndCustomerFromReservation() {
        when(reservations.findReservationById(command.reservationId())).thenReturn(Optional.of(reservation));
        when(reservations.updateExpectedStatusToNewStatus(command.reservationId(),
                ReservationStatus.ON_HOLD, ReservationStatus.PAYMENT_PROCESSING)).thenReturn(1);
        when(payments.savePayment(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var before = Instant.now();
        var result = useCase.execute(command);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getType()).isEqualTo(PaymentType.CREDIT);
        assertThat(result.getIdempotencyKey()).isEqualTo("checkout");
        assertThat(result.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(result.getExpiresAt()).isEqualTo(result.getCreatedAt().plusSeconds(600));
        assertThat(result.getReservationSnapshot().getId()).isEqualTo(reservation.getId());
        assertThat(result.getReservationSnapshot().getAmount()).isEqualByComparingTo("150.75");
        assertThat(result.getReservationSnapshot().getCurrency()).isEqualTo("BRL");
        assertThat(result.getReservationSnapshot().getCustomerId()).isEqualTo(reservation.getCustomerId());
        var order = inOrder(reservations, payments);
        order.verify(reservations).updateExpectedStatusToNewStatus(command.reservationId(),
                ReservationStatus.ON_HOLD, ReservationStatus.PAYMENT_PROCESSING);
        order.verify(payments).savePayment(result);
    }

    @Test
    void returnsExistingPaymentWithoutChangingReservation() {
        var existing = existingPayment();
        when(payments.findPaymentByIdempotencyKeyAndReservationId("checkout", reservation.getId()))
                .thenReturn(Optional.of(existing));

        assertThat(useCase.execute(command)).isSameAs(existing);
        verifyNoInteractions(reservations);
        verify(payments, never()).savePayment(any());
    }

    @Test
    void rejectsMissingReservationWithoutSavingPayment() {
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining(reservation.getId().toString());
        verify(payments, never()).savePayment(any());
        verify(reservations, never()).updateExpectedStatusToNewStatus(any(), any(), any());
    }

    @Test
    void returnsPaymentCommittedBetweenInitialLookupAndReservationRead() {
        var existing = existingPayment();
        reservation.startPayment(Instant.now());
        when(payments.findPaymentByIdempotencyKeyAndReservationId("checkout", reservation.getId()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(reservations.findReservationById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThat(useCase.execute(command)).isSameAs(existing);
        verify(payments, never()).savePayment(any());
        verify(reservations, never()).updateExpectedStatusToNewStatus(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = "ON_HOLD", mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonPayableReservationWithoutAnExistingPayment(ReservationStatus status) {
        var restored = Reservation.restore(reservation.getId(), reservation.getEventId(),
                reservation.getCustomerId(), reservation.getSeats(), status,
                reservation.getCreatedAt(), reservation.getExpiresAt());
        when(reservations.findReservationById(reservation.getId())).thenReturn(Optional.of(restored));

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only ON_HOLD");
        verify(payments, never()).savePayment(any());
        verify(reservations, never()).updateExpectedStatusToNewStatus(any(), any(), any());
    }

    @Test
    void rejectsExpiredHoldWithoutChangingPersistentState() {
        var expired = Reservation.restore(reservation.getId(), reservation.getEventId(),
                reservation.getCustomerId(), reservation.getSeats(), ReservationStatus.ON_HOLD,
                Instant.now().minusSeconds(1200), Instant.now().minusSeconds(600));
        when(reservations.findReservationById(reservation.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expired reservation");
        verify(payments, never()).savePayment(any());
        verify(reservations, never()).updateExpectedStatusToNewStatus(any(), any(), any());
    }

    @Test
    void returnsWinningPaymentWhenConditionalStatusUpdateLosesRace() {
        var existing = existingPayment();
        when(payments.findPaymentByIdempotencyKeyAndReservationId("checkout", reservation.getId()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(reservations.findReservationById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservations.updateExpectedStatusToNewStatus(reservation.getId(),
                ReservationStatus.ON_HOLD, ReservationStatus.PAYMENT_PROCESSING)).thenReturn(0);

        assertThat(useCase.execute(command)).isSameAs(existing);
        verify(payments, never()).savePayment(any());
    }

    @Test
    void rejectsLostStatusUpdateWithoutMatchingPayment() {
        when(reservations.findReservationById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservations.updateExpectedStatusToNewStatus(reservation.getId(),
                ReservationStatus.ON_HOLD, ReservationStatus.PAYMENT_PROCESSING)).thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(ReservationNotFoundException.class);
        verify(payments, never()).savePayment(any());
    }

    private Payment existingPayment() {
        return new Payment(new ReservationSnapshot(reservation.getId(), new BigDecimal("150.75"),
                reservation.getCustomerId(), "BRL"), PaymentType.CREDIT, "checkout", Instant.now());
    }
}
