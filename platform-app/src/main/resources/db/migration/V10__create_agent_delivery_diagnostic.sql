create table agent_delivery_diagnostic (
  id uuid primary key,
  test_run_id uuid not null references test_run(id) on delete cascade,
  profile_id uuid not null,
  profile_version integer not null,
  service_name varchar(160) not null,
  dropped_evidence_count bigint not null,
  delivery_failure_count bigint not null,
  last_failure varchar(255),
  updated_at timestamp with time zone not null,
  unique (test_run_id, service_name)
);
create index idx_agent_delivery_diagnostic_run on agent_delivery_diagnostic(test_run_id, service_name);
