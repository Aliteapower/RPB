-- Payroll is additive and company-scoped. No subscription, default role or other product grant changes.
create table workforce_pay_templates (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,name text not null check(length(name) between 1 and 100),
 snapshot jsonb not null,enabled boolean not null,version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,company_id),foreign key(company_id,tenant_id) references operating_entities(id,tenant_id)
);
create index ix_workforce_pay_template_company on workforce_pay_templates(tenant_id,company_id);
create table workforce_pay_profiles (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,effective_from date not null,
 template_id uuid not null,template_version bigint not null check(template_version>0),snapshot jsonb not null,version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,company_id,employee_id),unique(tenant_id,company_id,employee_id,effective_from),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(template_id,tenant_id,company_id) references workforce_pay_templates(id,tenant_id,company_id)
);
create index ix_workforce_pay_profile_effective on workforce_pay_profiles(tenant_id,company_id,employee_id,effective_from desc);
create table workforce_pay_batches (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,company_name text not null,period_month date not null check(extract(day from period_month)=1),
 status text not null check(status in ('draft','calculated','confirmed','published','voided')),version bigint not null check(version>0),
 calculation jsonb null,created_by uuid not null,confirmed_by uuid null,published_by uuid null,confirmed_at timestamptz null,published_at timestamptz null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,company_id),unique(id,tenant_id),foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),foreign key(confirmed_by,tenant_id) references auth_accounts(id,tenant_id),foreign key(published_by,tenant_id) references auth_accounts(id,tenant_id),
 check(status not in ('confirmed','published') or (calculation is not null and confirmed_at is not null and confirmed_by is not null)),
 check(status<>'published' or (published_at is not null and published_by is not null))
);
create unique index ux_workforce_pay_company_month on workforce_pay_batches(tenant_id,company_id,period_month) where status<>'voided';
create index ix_workforce_pay_batch_lookup on workforce_pay_batches(tenant_id,company_id,period_month desc);
create table workforce_pay_inputs (
 batch_id uuid not null,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,snapshot jsonb not null,version bigint not null check(version>0),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(batch_id,employee_id),
 foreign key(batch_id,tenant_id,company_id) references workforce_pay_batches(id,tenant_id,company_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id)
);
create index ix_workforce_pay_input_tenant on workforce_pay_inputs(tenant_id,batch_id);
create table workforce_pay_commands (
 id uuid primary key,tenant_id uuid not null,batch_id uuid not null,actor_id uuid not null,action text not null check(action in ('calculate','confirm','publish','void')),
 payload jsonb not null,response jsonb not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(batch_id,tenant_id) references workforce_pay_batches(id,tenant_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_workforce_pay_command_batch on workforce_pay_commands(tenant_id,batch_id);
create table workforce_pay_payments (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,batch_id uuid not null,employee_id uuid not null,
 amount numeric(18,2) not null check(amount>0),payment_date date not null,method text not null check(method in ('cash','bank','other')),
 reference text not null check(length(reference)<=100),note text not null check(length(note) between 1 and 500),reverses_id uuid null unique,
 actor_id uuid not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,company_id,batch_id,employee_id),
 foreign key(batch_id,tenant_id,company_id) references workforce_pay_batches(id,tenant_id,company_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id),
 foreign key(reverses_id,tenant_id,company_id,batch_id,employee_id) references workforce_pay_payments(id,tenant_id,company_id,batch_id,employee_id),check(reverses_id is null or reverses_id<>id)
);
create index ix_workforce_pay_payment_batch on workforce_pay_payments(tenant_id,batch_id,employee_id);
