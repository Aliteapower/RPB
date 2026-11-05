-- Append-only annual evidence. Existing payroll snapshots and policy revisions are untouched.
create table workforce_statutory_year_subjects (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,year integer not null check(year between 2000 and 2100),
 created_at timestamptz not null default now(),primary key(tenant_id,company_id,employee_id,year),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id)
);
create table workforce_statutory_year_revisions (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,year integer not null,version bigint not null check(version>0),
 snapshot jsonb not null,created_by uuid not null,created_at timestamptz not null default now(),
 primary key(tenant_id,company_id,employee_id,year,version),
 foreign key(tenant_id,company_id,employee_id,year) references workforce_statutory_year_subjects
);
create table workforce_statutory_year_commands (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,year integer not null,store_id uuid not null,actor_id uuid not null,
 kind text not null check(kind in ('opening','reconcile')),payload jsonb not null,response jsonb not null,created_at timestamptz not null default now(),
 foreign key(tenant_id,company_id,employee_id,year) references workforce_statutory_year_subjects,
 foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table workforce_aw_reconciliations (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,year integer not null,
 source_fingerprint text not null,settlement_month date not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 unique(tenant_id,company_id,employee_id,year,source_fingerprint,settlement_month),
 foreign key(tenant_id,company_id,employee_id,year) references workforce_statutory_year_subjects
);
create table workforce_aw_assessments (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,year integer not null,batch_id uuid not null,
 source_fingerprint text not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 primary key(tenant_id,batch_id,employee_id),
 foreign key(tenant_id,company_id,employee_id,year) references workforce_statutory_year_subjects,
 foreign key(batch_id,tenant_id,company_id) references workforce_pay_batches(id,tenant_id,company_id)
);
create table workforce_wage_events (
 tenant_id uuid not null,company_id uuid not null,event_id uuid not null,employee_id uuid not null,year integer not null,
 snapshot jsonb not null,created_at timestamptz not null default now(),primary key(tenant_id,company_id,event_id),
 unique(tenant_id,company_id,employee_id,event_id),
 foreign key(tenant_id,company_id,employee_id,year) references workforce_statutory_year_subjects
);
create table workforce_wage_event_consumptions (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,event_id uuid not null,batch_id uuid not null,
 active boolean not null default true,created_at timestamptz not null default now(),released_at timestamptz,
 primary key(tenant_id,company_id,event_id,batch_id),
 foreign key(tenant_id,company_id,employee_id,event_id) references workforce_wage_events(tenant_id,company_id,employee_id,event_id),
 foreign key(batch_id,tenant_id,company_id) references workforce_pay_batches(id,tenant_id,company_id),
 check(active=(released_at is null))
);
create unique index uq_wage_event_active on workforce_wage_event_consumptions(tenant_id,company_id,event_id) where active;
create index ix_wage_event_batch on workforce_wage_event_consumptions(tenant_id,batch_id);
create trigger statutory_year_revision_immutable before update or delete on workforce_statutory_year_revisions for each row execute function workforce_pay_template_immutable();
create trigger statutory_year_command_immutable before update or delete on workforce_statutory_year_commands for each row execute function workforce_pay_template_immutable();
create trigger aw_reconciliation_immutable before update or delete on workforce_aw_reconciliations for each row execute function workforce_pay_template_immutable();
create trigger aw_assessment_immutable before update or delete on workforce_aw_assessments for each row execute function workforce_pay_template_immutable();
create trigger wage_event_immutable before update or delete on workforce_wage_events for each row execute function workforce_pay_template_immutable();
