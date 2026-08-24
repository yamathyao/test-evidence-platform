create table blackbox_correlation_rule (
  id uuid primary key,
  test_run_id uuid not null references test_run(id) on delete cascade,
  test_case_id uuid not null references test_case(id),
  profile_id uuid not null references capture_profile(id),
  profile_version integer not null,
  target_services jsonb not null,
  definition_json jsonb not null,
  value_json jsonb not null,
  status varchar(20) not null,
  expires_at timestamp with time zone not null,
  bound_at timestamp with time zone,
  created_at timestamp with time zone not null
);

create index idx_blackbox_correlation_rule_status_expires
  on blackbox_correlation_rule(status, expires_at);
