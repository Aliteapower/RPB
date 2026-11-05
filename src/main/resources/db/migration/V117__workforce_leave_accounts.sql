-- Balance and provenance are independent of payroll snapshots. Credits and debits remain append-only.
create table workforce_leave_accounts (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,employee_id uuid not null,account_year integer not null check(account_year between 2000 and 2100),
 kind text not null check(kind in('annual','sick','hospital','company')),unit text not null check(unit in('day','hour')),contract_id uuid not null,
 opening_on date not null,baseline_accrual numeric(18,6) not null,version bigint not null check(version>0),created_by uuid not null,created_at timestamptz not null default now(),
 unique(id,tenant_id),unique(tenant_id,company_id,employee_id,account_year,kind),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
 foreign key(contract_id,tenant_id,employee_id,company_id) references workforce_employment_contracts(id,tenant_id,employee_id,company_id),foreign key(created_by,tenant_id) references auth_accounts(id,tenant_id),
 check(extract(year from opening_on)=account_year)
);
create table workforce_leave_credit_lots (
 id uuid primary key,tenant_id uuid not null,account_id uuid not null,origin text not null,source_key text not null,expires_on date not null,
 quantity numeric(18,6) not null check(quantity>0),entitlement_year integer not null check(entitlement_year between 2000 and 2100),contract_id uuid not null,account_version bigint not null check(account_version>0),created_at timestamptz not null default now(),unique(id,tenant_id),unique(id,tenant_id,account_id),unique(tenant_id,account_id,source_key),
 foreign key(account_id,tenant_id) references workforce_leave_accounts(id,tenant_id),foreign key(contract_id,tenant_id) references workforce_employment_contracts(id,tenant_id)
);
create index ix_workforce_leave_lots on workforce_leave_credit_lots(tenant_id,account_id,expires_on,id);
create table workforce_leave_ledger (
 id uuid primary key,tenant_id uuid not null,account_id uuid not null,lot_id uuid not null,source_key text not null,operation text not null,
 quantity numeric(18,6) not null check(quantity<>0),contract_id uuid not null,account_version bigint not null check(account_version>0),reason text not null,actor_id uuid not null,created_at timestamptz not null default now(),
 unique(tenant_id,account_id,lot_id,source_key,operation),foreign key(account_id,tenant_id) references workforce_leave_accounts(id,tenant_id),
 foreign key(lot_id,tenant_id,account_id) references workforce_leave_credit_lots(id,tenant_id,account_id),foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id),foreign key(contract_id,tenant_id) references workforce_employment_contracts(id,tenant_id)
);
create index ix_workforce_leave_ledger on workforce_leave_ledger(tenant_id,account_id,created_at,id);
create table workforce_leave_holds (
 tenant_id uuid not null,source_id uuid not null,source_kind text not null check(source_kind in('leave','encashment')),account_id uuid not null,lot_id uuid not null,
 quantity numeric(18,6) not null check(quantity>0),status text not null check(status in('reserved','used','released')),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,source_id,account_id,lot_id),foreign key(account_id,tenant_id) references workforce_leave_accounts(id,tenant_id),foreign key(lot_id,tenant_id,account_id) references workforce_leave_credit_lots(id,tenant_id,account_id)
);
create table workforce_leave_type_policies (
 tenant_id uuid not null,type_id uuid not null,store_id uuid not null,kind text not null check(kind in('annual','sick','hospital','company','unpaid')),version bigint not null check(version>0),
 primary key(tenant_id,type_id),foreign key(type_id,tenant_id,store_id) references workforce_leave_types(id,tenant_id,store_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
