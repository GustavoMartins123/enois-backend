-- Pré-triagem determinística antes da IA + ledger de feedback humano para pesos aprendidos.
alter table reports add column if not exists pre_triage text null;
alter table reports add column if not exists pre_triage_score int null;
alter table reports add column if not exists pre_triaged_at timestamp null;

create index if not exists idx_reports_pre_triage
  on reports (status, pre_triage_score, ai_triaged_at);

create table if not exists moderation_feedback (
  id varchar(64) not null,
  report_id varchar(64) not null,
  decision varchar(32) not null,
  source varchar(16) not null,
  decided_by varchar(255) null,
  signals varchar(500) null,
  score int null,
  created_at timestamp not null,
  primary key (id),
  key idx_moderation_feedback_report (report_id),
  key idx_moderation_feedback_created (decision, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_uca1400_ai_ci;

create table if not exists moderation_signal_weights (
  signal varchar(64) not null,
  weight int not null,
  samples int not null default 0,
  updated_at timestamp not null,
  primary key (signal)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_uca1400_ai_ci;
