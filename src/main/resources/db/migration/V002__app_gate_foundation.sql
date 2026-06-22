create table platform_apps (
    id uuid primary key default gen_random_uuid(),
    app_key text not null,
    app_name text not null,
    status text not null,
    default_entry_route text not null,
    description text null,
    sort_order integer not null default 0,
    config_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_platform_apps_key unique (app_key),
    constraint ck_platform_apps_status check (status in ('active', 'disabled'))
);

create index ix_platform_apps_status_sort
    on platform_apps (status, sort_order, app_key);

create table tenant_app_entitlements (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    app_key text not null references platform_apps(app_key),
    status text not null,
    valid_from timestamptz null,
    valid_until timestamptz null,
    config_json jsonb not null default '{}'::jsonb,
    enabled_by uuid null,
    enabled_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_tenant_app_entitlements_scope unique (tenant_id, app_key),
    constraint ck_tenant_app_entitlements_status check (
        status in ('enabled', 'disabled', 'trial', 'expired', 'suspended')
    ),
    constraint ck_tenant_app_entitlements_valid_range check (
        valid_until is null or valid_from is null or valid_until > valid_from
    )
);

create index ix_tenant_app_entitlements_status
    on tenant_app_entitlements (tenant_id, app_key, status, valid_until);

create table store_app_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    app_key text not null references platform_apps(app_key),
    is_enabled boolean not null default false,
    entry_visible boolean not null default false,
    config_json jsonb not null default '{}'::jsonb,
    enabled_by uuid null,
    enabled_at timestamptz null,
    disabled_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_store_app_settings_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_store_app_settings_scope unique (tenant_id, store_id, app_key)
);

create index ix_store_app_settings_enabled
    on store_app_settings (tenant_id, store_id, app_key, is_enabled, entry_visible);

create table app_gate_audit_logs (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid null,
    app_key text not null references platform_apps(app_key),
    action text not null,
    operator_user_id uuid null,
    operator_role text null,
    before_json jsonb null,
    after_json jsonb null,
    created_at timestamptz not null default now(),
    constraint fk_app_gate_audit_logs_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_app_gate_audit_logs_action check (
        action in (
            'TENANT_APP_ENABLED',
            'TENANT_APP_DISABLED',
            'TENANT_APP_SUSPENDED',
            'TENANT_APP_UPDATED',
            'STORE_APP_ENABLED',
            'STORE_APP_DISABLED',
            'STORE_APP_VISIBILITY_UPDATED',
            'STORE_APP_CONFIG_UPDATED',
            'APP_GATE_DENIED'
        )
    )
);

create index ix_app_gate_audit_logs_scope
    on app_gate_audit_logs (tenant_id, store_id, app_key, created_at);

create index ix_app_gate_audit_logs_action
    on app_gate_audit_logs (action, created_at);

insert into platform_apps (
    app_key,
    app_name,
    status,
    default_entry_route,
    description,
    sort_order,
    config_json
)
values (
    'reservation_queue',
    '订位排号系统',
    'active',
    '/stores/:storeId/staff',
    'Reservation, queue, walk-in, seating, and cleaning operational app.',
    10,
    '{}'::jsonb
)
on conflict (app_key) do update
set
    app_name = excluded.app_name,
    status = excluded.status,
    default_entry_route = excluded.default_entry_route,
    description = excluded.description,
    sort_order = excluded.sort_order,
    updated_at = now();

insert into tenant_app_entitlements (
    tenant_id,
    app_key,
    status,
    valid_from,
    valid_until,
    config_json,
    enabled_at
)
select
    tenants.id,
    'reservation_queue',
    'enabled',
    now(),
    null,
    '{}'::jsonb,
    now()
from tenants
where tenants.deleted_at is null
on conflict (tenant_id, app_key) do nothing;

insert into store_app_settings (
    tenant_id,
    store_id,
    app_key,
    is_enabled,
    entry_visible,
    config_json,
    enabled_at
)
select
    stores.tenant_id,
    stores.id,
    'reservation_queue',
    true,
    true,
    '{}'::jsonb,
    now()
from stores
where stores.deleted_at is null
on conflict (tenant_id, store_id, app_key) do nothing;
