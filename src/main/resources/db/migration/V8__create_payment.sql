CREATE TABLE payment (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    amount NUMERIC(38, 2) NOT NULL,
    customer_id UUID NOT NULL REFERENCES users(id),
    currency VARCHAR(4) NOT NULL,
    status VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payment_reservation_idempotency UNIQUE (reservation_id, idempotency_key),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_payment_status CHECK (status IN ('UNKNOWN', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'PROCESSING')),
    CONSTRAINT ck_payment_type CHECK (type IN ('CREDIT', 'DEBIT'))
);

CREATE INDEX ix_payment_reservation_id ON payment(reservation_id);
