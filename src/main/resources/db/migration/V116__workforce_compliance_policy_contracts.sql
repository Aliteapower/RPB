-- Additive, opt-in policy/contract records. No historic attendance or payroll is rewritten.
create table workforce_compliance_policies (
 id uuid not null,tenant_id uuid not null,version bigint not null check(version>0),name varchar(100) not null,
 effective_from date not null,effective_to date not null,status text not null check(status in('draft','published')),
 snapshot jsonb not null,created_by uuid not null,created_at timestamptz not null default now(),
 primary key(id,tenant_id,version),foreign key(tenant_id) references tenants(id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),
 check(effective_to>=effective_from),check(jsonb_typeof(snapshot)='object')
);
create index ix_workforce_compliance_policies on workforce_compliance_policies(tenant_id,id,version desc);
create table workforce_employment_contracts (
 id uuid primary key,tenant_id uuid not null,employee_id uuid not null,company_id uuid not null,version bigint not null check(version>0),effective_from date not null,
 policy_id uuid not null,policy_version bigint not null,snapshot jsonb not null,created_by uuid not null,created_at timestamptz not null default now(),
 unique(id,tenant_id),unique(id,tenant_id,employee_id,company_id),unique(tenant_id,employee_id,effective_from),unique(tenant_id,employee_id,version),
 foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(policy_id,tenant_id,policy_version) references workforce_compliance_policies(id,tenant_id,version),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),
 check(jsonb_typeof(snapshot)='object')
);
create index ix_workforce_contract_effective on workforce_employment_contracts(tenant_id,company_id,employee_id,effective_from desc);
create table workforce_compliance_commands (
 tenant_id uuid not null,id uuid not null,store_id uuid not null,actor_id uuid not null,kind text not null,request_json jsonb not null,response_json jsonb not null,created_at timestamptz not null default now(),
 primary key(tenant_id,id),foreign key(store_id,tenant_id) references stores(id,tenant_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
