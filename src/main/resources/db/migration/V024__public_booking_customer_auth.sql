create table if not exists customer_auth_accounts (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    customer_id uuid not null,
    email text not null,
    display_name text null,
    status text not null default 'active',
    email_verified_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_customer_auth_accounts_customer_scope
        foreign key (customer_id, tenant_id) references customers(id, tenant_id),
    constraint ck_customer_auth_accounts_status
        check (status in ('active', 'disabled', 'archived')),
    constraint ck_customer_auth_accounts_email
        check (email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,}$')
);

create unique index if not exists ux_customer_auth_accounts_email_active
    on customer_auth_accounts (tenant_id, lower(email))
    where deleted_at is null;

create index if not exists ix_customer_auth_accounts_customer
    on customer_auth_accounts (tenant_id, customer_id)
    where deleted_at is null;

create table if not exists customer_auth_identities (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    auth_account_id uuid not null references customer_auth_accounts(id) on delete cascade,
    provider text not null,
    provider_subject text not null,
    email text null,
    display_name text null,
    linked_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_customer_auth_identities_provider
        check (provider in ('email', 'google', 'facebook')),
    constraint ck_customer_auth_identities_subject
        check (length(trim(provider_subject)) > 0)
);

create unique index if not exists ux_customer_auth_identities_provider_subject_active
    on customer_auth_identities (tenant_id, provider, provider_subject)
    where deleted_at is null;

create index if not exists ix_customer_auth_identities_account
    on customer_auth_identities (auth_account_id)
    where deleted_at is null;

create table if not exists customer_auth_sessions (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    auth_account_id uuid not null references customer_auth_accounts(id) on delete cascade,
    customer_id uuid not null,
    session_hash text not null,
    status text not null default 'active',
    expires_at timestamptz not null,
    last_seen_at timestamptz not null default now(),
    remote_addr text null,
    user_agent text null,
    revoked_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_customer_auth_sessions_customer_scope
        foreign key (customer_id, tenant_id) references customers(id, tenant_id),
    constraint ck_customer_auth_sessions_status
        check (status in ('active', 'revoked', 'expired')),
    constraint ck_customer_auth_sessions_expiry
        check (expires_at > created_at)
);

create unique index if not exists ux_customer_auth_sessions_hash_active
    on customer_auth_sessions (session_hash)
    where deleted_at is null;

create index if not exists ix_customer_auth_sessions_account_status
    on customer_auth_sessions (auth_account_id, status, expires_at)
    where deleted_at is null;

create table if not exists customer_email_login_codes (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    email text not null,
    code_hash text not null,
    status text not null default 'created',
    attempt_count integer not null default 0,
    max_attempts integer not null default 5,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    remote_addr text null,
    user_agent text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_customer_email_login_codes_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_customer_email_login_codes_status
        check (status in ('created', 'consumed', 'expired', 'failed')),
    constraint ck_customer_email_login_codes_attempts
        check (attempt_count >= 0 and max_attempts > 0),
    constraint ck_customer_email_login_codes_expiry
        check (expires_at > created_at),
    constraint ck_customer_email_login_codes_email
        check (email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,}$')
);

create index if not exists ix_customer_email_login_codes_lookup
    on customer_email_login_codes (tenant_id, store_id, lower(email), status, expires_at)
    where deleted_at is null;

create table if not exists store_public_booking_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    enabled boolean not null default false,
    require_customer_login boolean not null default true,
    default_quota_mode text not null default 'percentage',
    default_quota_percent integer not null default 20,
    default_table_count integer null,
    default_guest_count integer null,
    min_lead_minutes integer not null default 60,
    max_advance_days integer not null default 30,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ux_store_public_booking_settings_scope unique (tenant_id, store_id),
    constraint fk_store_public_booking_settings_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_public_booking_settings_quota_mode
        check (default_quota_mode in ('percentage', 'table_count', 'guest_count')),
    constraint ck_store_public_booking_settings_quota_percent
        check (default_quota_percent between 0 and 100),
    constraint ck_store_public_booking_settings_table_count
        check (default_table_count is null or default_table_count >= 0),
    constraint ck_store_public_booking_settings_guest_count
        check (default_guest_count is null or default_guest_count >= 0),
    constraint ck_store_public_booking_settings_window
        check (min_lead_minutes >= 0 and max_advance_days between 0 and 366)
);

create table if not exists store_public_booking_quota_overrides (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    business_date date not null,
    period_key text null,
    quota_mode text not null,
    quota_percent integer null,
    table_count integer null,
    guest_count integer null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_public_booking_quota_overrides_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_public_booking_quota_overrides_mode
        check (quota_mode in ('percentage', 'table_count', 'guest_count', 'closed')),
    constraint ck_store_public_booking_quota_overrides_percent
        check (quota_percent is null or quota_percent between 0 and 100),
    constraint ck_store_public_booking_quota_overrides_table_count
        check (table_count is null or table_count >= 0),
    constraint ck_store_public_booking_quota_overrides_guest_count
        check (guest_count is null or guest_count >= 0)
);

create unique index if not exists ux_store_public_booking_quota_override_active
    on store_public_booking_quota_overrides (tenant_id, store_id, business_date, coalesce(period_key, ''))
    where deleted_at is null;

create index if not exists ix_store_public_booking_quota_overrides_store_date
    on store_public_booking_quota_overrides (tenant_id, store_id, business_date)
    where deleted_at is null;
