ALTER TABLE usage_event
    ADD COLUMN playback_id VARCHAR(64) NULL;

CREATE UNIQUE INDEX uq_usage_event_playback
    ON usage_event (anon_client_id, playback_id, event_type);
