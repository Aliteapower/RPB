-- Append-only formal coverage, with no rewrite of ordinary payroll.
create table workforce_pay_segments (
 id uuid primary key, tenant_id uuid not null, company_id uuid not null, employee_id uuid not null,
 period_month date not null check(extract(day from period_month)=1), from_date date not null, cutoff date not null,
 status text not null check(status in ('draft','calculated','confirmed','published','voided')), version bigint not null check(version>0),
 request_json jsonb not null check(jsonb_typeof(request_json)='object'), snapshot jsonb null,
 created_by uuid not null, updated_by uuid not null, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(id,tenant_id), unique(id,tenant_id,company_id,employee_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),foreign key(updated_by,tenant_id) references auth_accounts(id,tenant_id),
 check(from_date>=period_month and cutoff>=from_date and cutoff<period_month+interval '1 month'),
 check(status not in ('confirmed','published') or snapshot is not null)
);
create index ix_workforce_segments_month on workforce_pay_segments(tenant_id,company_id,period_month,employee_id,cutoff);
create table workforce_pay_segment_days (
 tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,covered_date date not null,segment_id uuid not null,
 primary key(tenant_id,company_id,employee_id,covered_date),
 foreign key(segment_id,tenant_id,company_id,employee_id) references workforce_pay_segments(id,tenant_id,company_id,employee_id)
);
create table workforce_pay_segment_commands (
 tenant_id uuid not null,command_id uuid not null,segment_id uuid not null,store_id uuid not null,actor_id uuid not null,
 request_json jsonb not null,response_json jsonb not null,created_at timestamptz not null default now(),primary key(tenant_id,command_id),
 foreign key(segment_id,tenant_id) references workforce_pay_segments(id,tenant_id),foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create table workforce_pay_segment_payments (
 id uuid primary key,tenant_id uuid not null,segment_id uuid not null,snapshot jsonb not null,created_at timestamptz not null default now(),
 foreign key(segment_id,tenant_id) references workforce_pay_segments(id,tenant_id)
);
create index ix_workforce_segment_payments on workforce_pay_segment_payments(tenant_id,segment_id);
