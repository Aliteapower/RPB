insert into platform_apps(app_key,app_name,status,default_entry_route,description,sort_order,config_json)
values ('daily_ledger','每日营业记录','active','/stores/:storeId/daily-ledger','累计现金盘点、非现金、开支、抽款、交班与历史报表',40,
        '{"entryPermissions":["daily_ledger.view","daily_ledger.write"]}'::jsonb);
insert into platform_product_line_prices(app_key,billing_cycle,amount,currency,status)
values ('daily_ledger','monthly',30.00,'SGD','active'),('daily_ledger','yearly',360.00,'SGD','active');

create table daily_ledger_settings (
    tenant_id uuid not null references tenants(id), store_id uuid not null,
    config jsonb not null, branding jsonb not null default '{}'::jsonb,
    version integer not null default 0 check(version>=0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    primary key(tenant_id,store_id),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    check(jsonb_typeof(config)='object' and jsonb_typeof(branding)='object')
);
create table daily_ledger_days (
    tenant_id uuid not null, store_id uuid not null, business_date date not null, config jsonb not null,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    primary key(tenant_id,store_id,business_date),
    foreign key(tenant_id,store_id) references daily_ledger_settings(tenant_id,store_id),
    check(jsonb_typeof(config)='object')
);
create table daily_ledger_records (
    tenant_id uuid not null, store_id uuid not null, business_date date not null,
    period text not null check(period in ('breakfast','lunch','dinner')),
    version integer not null check(version>0), payload jsonb not null, submitted_payload jsonb null,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    primary key(tenant_id,store_id,business_date,period),
    foreign key(tenant_id,store_id,business_date) references daily_ledger_days(tenant_id,store_id,business_date),
    check(jsonb_typeof(payload)='object'),
    check(submitted_payload is null or jsonb_typeof(submitted_payload)='object')
);
create table daily_ledger_saves (
    tenant_id uuid not null, store_id uuid not null, client_save_id text not null,
    business_date date not null, period text not null, actor_id uuid not null,
    request_hash text not null, created_at timestamptz not null default now(),
    primary key(tenant_id,store_id,client_save_id),
    foreign key(tenant_id,store_id,business_date,period) references daily_ledger_records(tenant_id,store_id,business_date,period),
    check(length(client_save_id) between 1 and 100)
);
create table daily_ledger_audit (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, store_id uuid not null,
    business_date date null, period text null, action text not null,
    actor_id uuid null, actor_name text not null, payload jsonb not null,
    source_id text null, created_at timestamptz not null default now(),
    foreign key(tenant_id,store_id) references daily_ledger_settings(tenant_id,store_id),
    check(action in ('settings','draft','submit','import')),
    check(period is null or period in ('breakfast','lunch','dinner'))
);
create index ix_daily_ledger_audit_lookup on daily_ledger_audit(tenant_id,store_id,business_date,period,created_at desc);

-- Align existing accounts with the same role-based grants used when creating new accounts.
insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,p.permission from auth_accounts a
join auth_account_roles r on r.account_id=a.id and r.deleted_at is null
cross join (values ('daily_ledger.view'),('daily_ledger.write'),('daily_ledger.settings.manage')) p(permission)
where a.deleted_at is null and a.actor_type<>'platform_admin'
and ((r.role_code in ('tenant_admin','store_manager')) or (r.role_code='store_staff' and p.permission<>'daily_ledger.settings.manage'))
and not exists(select 1 from auth_account_permissions ep where ep.account_id=a.id and ep.permission_code=p.permission and ep.deleted_at is null);
