package br.com.gustavoakira.ticketing.core.reservation.domain;

import com.github.f4b6a3.uuid.UuidCreator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Reservation {
    private UUID id;
    private UUID eventId;
    private UUID customerId;
    private List<ReservedSeat> seats;
    private ReservationStatus status;
    private Instant createdAt;
    private Instant expiresAt;

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    private Reservation(){

    }

    public Reservation(UUID eventId, UUID customerId, List<ReservedSeat> seats, Instant createdAt) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.eventId = eventId;
        this.customerId = customerId;
        this.seats = List.copyOf(seats);
        this.status = ReservationStatus.ON_HOLD;
        this.createdAt = createdAt;
        this.expiresAt = this.createdAt.plus(HOLD_DURATION);
        validate();
    }

    public static Reservation restore(UUID id, UUID eventId, UUID customerId, List<ReservedSeat> seats, ReservationStatus status, Instant createdAt, Instant expiresAt) {
        var reservation = new Reservation();
        reservation.id = id;
        reservation.eventId = eventId;
        reservation.customerId = customerId;
        reservation.seats = List.copyOf(seats);
        reservation.status = status;
        reservation.createdAt = createdAt;
        reservation.expiresAt = expiresAt;
        reservation.validate();
        return reservation;
    }

    private void validate(){

        if(this.seats.isEmpty()){
            throw new IllegalArgumentException("Seats must not be empty");
        }
        if (this.seats.stream()
                .map(ReservedSeat::getId)
                .distinct()
                .count() != this.seats.size()) {
            throw new IllegalArgumentException("Reservation cannot contain duplicated seats");
        }

        if (!this.expiresAt.isAfter(this.createdAt)) {
            throw new IllegalArgumentException("Expires at must be after created at");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public List<ReservedSeat> getSeats() {
        return seats;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void expire(Instant now){
        expectedStatus(ReservationStatus.ON_HOLD,"Only ON_HOLD reservations can expire");
        if (now.isBefore(this.expiresAt)) {
            throw new IllegalStateException(
                    "Could not expire reservation because expiration time is not over"
            );
        }
        this.status = ReservationStatus.EXPIRED;
    }

    public void startPayment(Instant now) {
        expectedStatus(ReservationStatus.ON_HOLD,  "Only ON_HOLD reservations can start payment");
        if (!now.isBefore(this.expiresAt)) {
            throw new IllegalStateException(
                    "Expired reservation cannot start payment"
            );
        }


        this.status = ReservationStatus.PAYMENT_PROCESSING;
    }

    public void cancel(){
        if(this.status != ReservationStatus.ON_HOLD  && this.status != ReservationStatus.PAYMENT_PROCESSING){
            throw new IllegalStateException("Only ON_HOLD or PAYMENT_PROCESSING reservations can be cancelled");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void confirm(){
        expectedStatus(ReservationStatus.PAYMENT_PROCESSING,"Only PAYMENT_PROCESSING reservations can be confirmed");
        this.status = ReservationStatus.CONFIRMED;
    }

    public void releasePayment(Instant now) {
        expectedStatus(ReservationStatus.PAYMENT_PROCESSING,"Only PAYMENT_PROCESSING reservations can leave payment processing");

        if (now.isBefore(this.expiresAt)) {
            this.status = ReservationStatus.ON_HOLD;
            return;
        }

        this.status = ReservationStatus.EXPIRED;
    }
    private void expectedStatus(ReservationStatus status, String message){
        if(this.status != status){
            throw new IllegalStateException(message);
        }
    }
}
