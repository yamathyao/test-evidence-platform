create table protocol_payload_evidence (
  id uuid primary key,
  evidence_event_id uuid not null unique references evidence_event(id) on delete cascade,
  request_content_type varchar(255), response_content_type varchar(255),
  request_status varchar(32) not null, response_status varchar(32) not null,
  request_body text, response_body text,
  request_truncated boolean not null, response_truncated boolean not null,
  expires_at timestamp with time zone not null, created_at timestamp with time zone not null
);
create index idx_protocol_payload_evidence_expiry on protocol_payload_evidence(expires_at);
