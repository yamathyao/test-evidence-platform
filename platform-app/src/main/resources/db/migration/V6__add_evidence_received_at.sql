ALTER TABLE evidence_event ADD COLUMN received_at TIMESTAMP WITH TIME ZONE;

UPDATE evidence_event SET received_at = event_time WHERE received_at IS NULL;

ALTER TABLE evidence_event ALTER COLUMN received_at SET NOT NULL;

CREATE INDEX evidence_event_run_received_at_idx
    ON evidence_event (test_run_id, received_at DESC, id DESC);
