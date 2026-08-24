create table evidence_event (
  id uuid primary key,
  test_run_id uuid not null references test_run(id),
  profile_id uuid not null references capture_profile(id),
  profile_version integer not null,
  trace_id varchar(64) not null,
  span_id varchar(64) not null,
  parent_span_id varchar(64),
  service_name varchar(160) not null,
  protocol varchar(32) not null,
  direction varchar(32) not null,
  http_method varchar(16),
  target varchar(1000) not null,
  status_code integer,
  event_time timestamp with time zone not null,
  duration_millis bigint not null,
  error_summary varchar(1000)
);
create index idx_evidence_event_run_time on evidence_event(test_run_id, event_time, id);
