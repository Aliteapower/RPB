-- Approved leave encashment is an independent source, never overtime or a rewritten old payroll.
create table workforce_leave_encashments (
 tenant_id uuid not null, id uuid not null,
 employee_id uuid not null, company_id uuid not null, account_id uuid not null,
 status varchar(20) not null check(status in ('reserved','approved','released','consumed')),
 snapshot jsonb not null, created_at timestamptz not null default now(),
 primary key(tenant_id,id),
 foreign key(account_id,tenant_id) references workforce_leave_accounts(id,tenant_id),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id)
);
create index workforce_leave_encashments_employee on workforce_leave_encashments(tenant_id,employee_id,created_at);
create table workforce_leave_encashment_consumption (
 tenant_id uuid not null, source_id uuid not null, batch_id uuid not null,
 channel varchar(20) not null check(channel in ('monthly','segment','supplemental')),
 actor_id uuid not null, created_at timestamptz not null default now(),
 primary key(tenant_id,source_id),
 foreign key(tenant_id,source_id) references workforce_leave_encashments(tenant_id,id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);

-- Nullable evidence preserves every existing leave and approval snapshot. No clinical details required.
alter table workforce_leaves add column medical_reference text;
