ALTER TABLE usage_event DROP CHECK chk_usage_event_type;
ALTER TABLE usage_event ADD CONSTRAINT chk_usage_event_type
    CHECK (event_type IN ('APP_OPEN', 'APP_ACTIVE', 'AUTO_PLAY', 'TAP_POI', 'AUDIO_COMPLETED', 'ROUTE_SELECTED'));
CREATE INDEX idx_usage_event_route_time ON usage_event (route_id, occurred_at);
