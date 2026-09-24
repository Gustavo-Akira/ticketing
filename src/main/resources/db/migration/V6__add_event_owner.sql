-- NULL is retained for legacy events, which cannot be managed through the API.
ALTER TABLE events ADD COLUMN owner_id UUID;
ALTER TABLE events ADD CONSTRAINT fk_events_owner
    FOREIGN KEY (owner_id) REFERENCES users(id);
CREATE INDEX ix_events_owner_id ON events(owner_id);
