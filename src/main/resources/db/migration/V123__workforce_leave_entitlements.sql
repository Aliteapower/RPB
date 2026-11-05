-- Explicit manual annual grants. Revisions never rewrite previous policy snapshots.
create table workforce_leave_entitlements (
 id uuid not null,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,type_id uuid not null,source_store_id uuid not null,
 leave_year integer not null check(leave_year between 1900 and 9998),grant_units numeric(18,6) not null check(grant_units>=0),carry_units numeric(18,6) not null check(carry_units>=0),adjustment_units numeric(18,6) not null,
 unit text not null check(unit in ('HOURS','DAYS')),day_minutes integer check(day_minutes between 1 and 1440),reason text not null check(length(reason) between 1 and 500),version bigint not null check(version>0),actor_id uuid not null,created_at timestamptz not null default now(),
 primary key(tenant_id,id),unique(tenant_id,company_id,employee_id,type_id,leave_year,version),check(unit<>'DAYS' or day_minutes is not null),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(type_id,tenant_id,source_store_id) references workforce_leave_types(id,tenant_id,store_id),foreign key(source_store_id,tenant_id,company_id) references stores(id,tenant_id,operating_entity_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create table workforce_leave_entitlement_commands (
 tenant_id uuid not null,command_id uuid not null,store_id uuid not null,employee_id uuid not null,actor_id uuid not null,request_json jsonb not null,response_json jsonb not null,created_at timestamptz not null default now(),primary key(tenant_id,command_id),foreign key(tenant_id,command_id) references workforce_leave_entitlements(tenant_id,id)
);
