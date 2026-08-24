alter table test_run add column draining_at timestamp with time zone;
alter table test_run add column finish_reason varchar(20);
