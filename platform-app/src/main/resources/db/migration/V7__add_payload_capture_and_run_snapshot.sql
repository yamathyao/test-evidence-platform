alter table test_case add column http_payload_capture_enabled boolean not null default false;
alter table test_case add column completed_at timestamp with time zone;

alter table test_run add column case_snapshot_json jsonb;
