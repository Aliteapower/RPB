-- Shared customer identity stays in customers; credentials belong to customer-auth.
alter table customer_auth_accounts alter column email drop not null;

create table customer_password_credentials (
    tenant_id uuid not null references tenants(id),
    customer_id uuid not null,
    password_hash text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    primary key (tenant_id, customer_id),
    constraint fk_customer_password_scope foreign key (customer_id, tenant_id) references customers(id, tenant_id),
    constraint ck_customer_password_hash check (password_hash ~ '^\$2[aby]\$[0-9]{2}\$.{53}$')
);

create table customer_password_login_limits (
    tenant_id uuid not null references tenants(id),
    bucket_key text not null,
    attempts integer not null default 1 check (attempts > 0),
    window_started_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, bucket_key)
);

create index ix_customer_password_login_limits_updated on customer_password_login_limits(updated_at);
