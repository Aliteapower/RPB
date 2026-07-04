create table if not exists store_customer_email_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    enabled boolean not null default false,
    provider text not null default 'smtp',
    from_email text null,
    from_name text null,
    smtp_host text null,
    smtp_port integer not null default 587,
    smtp_username text null,
    smtp_password_secret text null,
    smtp_start_tls boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_customer_email_settings_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_customer_email_settings_provider
        check (provider in ('smtp')),
    constraint ck_store_customer_email_settings_port
        check (smtp_port between 1 and 65535)
);

create unique index if not exists ux_store_customer_email_settings_scope_active
    on store_customer_email_settings (tenant_id, store_id)
    where deleted_at is null;

create index if not exists ix_store_customer_email_settings_store
    on store_customer_email_settings (tenant_id, store_id)
    where deleted_at is null;

create table if not exists store_customer_oauth_provider_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    provider text not null,
    enabled boolean not null default false,
    client_id text null,
    client_secret_secret text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_customer_oauth_provider_settings_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_customer_oauth_provider
        check (provider in ('google', 'facebook'))
);

create unique index if not exists ux_store_customer_oauth_provider_settings_active
    on store_customer_oauth_provider_settings (tenant_id, store_id, provider)
    where deleted_at is null;

create index if not exists ix_store_customer_oauth_provider_settings_store
    on store_customer_oauth_provider_settings (tenant_id, store_id)
    where deleted_at is null;
