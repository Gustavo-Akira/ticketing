CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL CHECK (name ~ '[^[:space:]]'),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'AVAILABLE', 'SALES_CLOSED', 'FINISHED', 'CANCELLED'))
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    section VARCHAR(100) NOT NULL CHECK (section ~ '[^[:space:]]'),
    seat_row VARCHAR(50) NOT NULL CHECK (seat_row ~ '[^[:space:]]'),
    seat_number VARCHAR(20) NOT NULL CHECK (seat_number ~ '[^[:space:]]'),
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_seats_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD')),
    CONSTRAINT uk_seats_event_location UNIQUE (event_id, section, seat_row, seat_number)
);

-- The unique index also serves event-scoped seat lookups (event_id is its leading column).
