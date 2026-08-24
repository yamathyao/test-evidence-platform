create table assertion_result (
  id uuid primary key,
  test_run_id uuid not null references test_run(id) on delete cascade,
  sequence_no integer not null,
  assertion_type varchar(40) not null,
  status varchar(20) not null,
  expected_json jsonb not null,
  actual_json jsonb not null,
  failure_reason varchar(1000),
  unique (test_run_id, sequence_no)
);
create index idx_assertion_result_run_sequence on assertion_result(test_run_id, sequence_no);
