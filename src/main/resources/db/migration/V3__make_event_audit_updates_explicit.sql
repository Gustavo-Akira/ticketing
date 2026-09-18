-- Audit timestamps are not logical clocks or concurrency tokens.
-- Keep statement_timestamp() insertion defaults; application update queries
-- explicitly assign updated_at = statement_timestamp().
DROP TRIGGER events_audit ON events;
DROP FUNCTION maintain_event_audit();
ALTER TABLE events DROP CONSTRAINT ck_events_audit_order;
