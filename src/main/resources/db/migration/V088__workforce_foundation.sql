-- Registration only: no tenant/store entitlement or existing product grant is changed.
insert into platform_apps(app_key,app_name,status,default_entry_route,description,sort_order,config_json)
values ('workforce','员工考勤与薪资','active','/stores/:storeId/workforce','员工档案、跨店出勤与公司计薪',70,'{}');
insert into platform_product_line_prices(app_key,billing_cycle,amount,currency,status)
values ('workforce','monthly',0,'SGD','disabled'),('workforce','yearly',0,'SGD','disabled');

insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,'workforce.view' from auth_accounts a
join auth_account_roles r on r.account_id=a.id and r.role_code='tenant_admin' and r.deleted_at is null
where a.deleted_at is null and a.actor_type<>'platform_admin';
insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,'workforce.admin.manage' from auth_accounts a
join auth_account_roles r on r.account_id=a.id and r.role_code='tenant_admin' and r.deleted_at is null
where a.deleted_at is null and a.actor_type<>'platform_admin'
and not exists(select 1 from auth_account_roles m where m.account_id=a.id and m.role_code='store_manager' and m.deleted_at is null);

create table workforce_company_settings (
    tenant_id uuid not null, company_id uuid not null,
    registration_no text not null default '' check(length(registration_no)<=40),
    pay_day integer not null default 7 check(pay_day between 1 and 28),
    enabled boolean not null default true, version bigint not null check(version>0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    primary key(tenant_id,company_id),
    foreign key(company_id,tenant_id) references operating_entities(id,tenant_id)
);
create table workforce_employees (
    id uuid primary key, tenant_id uuid not null references tenants(id),
    employee_no text not null check(length(employee_no) between 1 and 40),
    name text not null check(length(name) between 1 and 100),
    phone text not null default '' check(length(phone)<=40), email text not null default '' check(length(email)<=160),
    job_title text not null default '' check(length(job_title)<=80),
    hire_date date not null, leave_date date null, status text not null check(status in ('active','left')),
    company_id uuid not null, company_effective_date date not null, main_store_id uuid not null,
    account_id uuid null, version bigint not null check(version>0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    unique(id,tenant_id), unique(tenant_id,employee_no), unique(account_id),
    foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
    foreign key(main_store_id,tenant_id) references stores(id,tenant_id),
    foreign key(account_id,tenant_id) references auth_accounts(id,tenant_id),
    check(leave_date is null or leave_date>=hire_date),
    check(status<>'left' or leave_date is not null),
    check(company_effective_date>=hire_date)
);
create index ix_workforce_employee_company on workforce_employees(tenant_id,company_id,status);
create table workforce_company_history (
    employee_id uuid not null, tenant_id uuid not null, company_id uuid not null,
    effective_date date not null, version bigint not null, created_at timestamptz not null default now(),
    primary key(employee_id,version),
    foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
    foreign key(company_id,tenant_id) references operating_entities(id,tenant_id)
);
create table workforce_work_stores (
    employee_id uuid not null, tenant_id uuid not null, store_id uuid not null,
    started_on date not null, ended_on date null,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    id uuid primary key default gen_random_uuid(),
    foreign key(employee_id,tenant_id) references workforce_employees(id,tenant_id),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    check(ended_on is null or ended_on>=started_on)
);
create unique index ux_workforce_current_store on workforce_work_stores(employee_id,store_id) where ended_on is null;
create index ix_workforce_work_store_scope on workforce_work_stores(tenant_id,store_id,employee_id) where ended_on is null;
create table workforce_administrators (
    tenant_id uuid not null, store_id uuid not null, account_id uuid not null,
    employee_manage boolean not null default false, payroll_company_id uuid null,
    version bigint not null check(version>0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    primary key(store_id,account_id),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    foreign key(account_id,tenant_id) references auth_accounts(id,tenant_id),
    foreign key(payroll_company_id,tenant_id) references operating_entities(id,tenant_id)
);
create index ix_workforce_administrator_account on workforce_administrators(tenant_id,account_id);
