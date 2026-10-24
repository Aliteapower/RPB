create table if not exists payment_business_days (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    business_date date not null,
    status text not null default 'open',
    opened_at timestamptz null,
    closed_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_business_days_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_payment_business_days_scope_date unique (tenant_id, store_id, business_date),
    constraint ck_payment_business_days_status check (status in ('open', 'closed'))
);

create unique index if not exists ux_payment_business_days_open_scope
    on payment_business_days (tenant_id, store_id)
    where status = 'open';

