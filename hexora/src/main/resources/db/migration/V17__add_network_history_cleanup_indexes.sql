ALTER TABLE network_scan
    ADD KEY idx_network_scan_started (started_at);

ALTER TABLE network_event
    ADD KEY idx_network_event_created (created_at);
