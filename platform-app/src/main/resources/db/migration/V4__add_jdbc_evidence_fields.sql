alter table evidence_event add column jdbc_operation varchar(16);
alter table evidence_event add column sql_template varchar(4000);
alter table evidence_event add column jdbc_parameters jsonb;
