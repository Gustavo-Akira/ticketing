-- Existing draft data has no historical audit information: these timestamps
-- record migration time, not the original creation/update time.
ALTER TABLE events
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT statement_timestamp(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT statement_timestamp(),
    ADD CONSTRAINT ck_events_audit_order CHECK (updated_at >= created_at);

CREATE FUNCTION maintain_event_audit() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.created_at := clock_timestamp();
        NEW.updated_at := NEW.created_at;
    ELSE
        NEW.created_at := OLD.created_at;
        NEW.updated_at := GREATEST(clock_timestamp(), OLD.updated_at + INTERVAL '1 microsecond');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER events_audit
BEFORE INSERT OR UPDATE ON events
FOR EACH ROW EXECUTE FUNCTION maintain_event_audit();

-- BRL is the explicit backfill assumption for the original draft's seats.
-- New writes must always specify currency: no permanent default is retained.
ALTER TABLE seats
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    ADD CONSTRAINT ck_seats_currency_format CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE seats ALTER COLUMN currency DROP DEFAULT;
