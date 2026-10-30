create table workforce_attendance_rule_templates (
 id uuid primary key,tenant_id uuid not null references tenants(id),name text not null check(length(trim(name)) between 1 and 100),
 rules jsonb not null check(jsonb_typeof(rules)='object'),enabled boolean not null,version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now()
);
create index ix_workforce_rule_template_tenant on workforce_attendance_rule_templates(tenant_id,id);
alter table workforce_attendance_sessions add column break_started_rule_id text null check(length(break_started_rule_id)<=64);
alter table workforce_clock_events add column break_rule_id text null check(length(break_rule_id)<=64);
alter table workforce_approvals add column decisions jsonb null check(decisions is null or jsonb_typeof(decisions)='array');
