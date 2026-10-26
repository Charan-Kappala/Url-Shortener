-- Every click event now carries a unique id assigned by core-api when the redirect happens.
-- The consumer inserts with ON CONFLICT (event_id) DO NOTHING, so a redelivered event is stored and counted once.
-- Rows written before this migration have no event id; a unique index allows any number of NULLs.
ALTER TABLE click_events ADD COLUMN event_id UUID;
CREATE UNIQUE INDEX uq_click_events_event_id ON click_events (event_id);
