CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reservations_status CHECK (status IN ('ON_HOLD', 'PAYMENT_PROCESSING', 'EXPIRED', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX ix_reservations_event_id ON reservations(event_id);
CREATE INDEX ix_reservations_customer_id ON reservations(customer_id);

CREATE TABLE reserved_seats (
    id UUID PRIMARY KEY,
    seat_id UUID NOT NULL REFERENCES seats(id),
    price NUMERIC(38, 2),
    currency VARCHAR(4),
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    CONSTRAINT uk_reserved_seats_reservation_seat UNIQUE (reservation_id, seat_id)
);

-- The unique index also serves reservation-scoped seat lookups.
CREATE INDEX ix_reserved_seats_seat_id ON reserved_seats(seat_id);
