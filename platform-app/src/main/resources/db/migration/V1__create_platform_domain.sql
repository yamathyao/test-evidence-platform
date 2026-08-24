create table capture_profile (
  id uuid primary key,
  name varchar(120) not null,
  version integer not null check (version > 0),
  definition_json jsonb not null,
  created_at timestamp with time zone not null,
  unique (name, version)
);

create table test_case (
  id uuid primary key,
  name varchar(160) not null,
  profile_id uuid not null references capture_profile(id),
  trigger_type varchar(20) not null,
  trigger_config_json jsonb not null,
  timeout_seconds integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table assertion (
  id uuid primary key,
  test_case_id uuid not null references test_case(id) on delete cascade,
  sequence_no integer not null,
  assertion_type varchar(40) not null,
  definition_json jsonb not null,
  unique (test_case_id, sequence_no)
);

create table test_run (
  id uuid primary key,
  test_case_id uuid not null references test_case(id),
  profile_version integer not null,
  status varchar(20) not null,
  started_at timestamp with time zone,
  finished_at timestamp with time zone,
  failure_reason varchar(1000)
);
create index idx_test_run_case on test_run(test_case_id);
