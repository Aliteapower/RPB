create table wine_storage_settings (
 tenant_id uuid not null,store_id uuid not null,
 operation_mode text not null default 'customer_verification' check(operation_mode in('customer_verification','staff_registration')),
 version integer not null check(version>=1),updated_by uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,store_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create table wine_storage_settings_events (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,actor_id uuid not null,
 details jsonb not null,created_at timestamptz not null default now(),
 foreign key(tenant_id,store_id) references wine_storage_settings(tenant_id,store_id)
);
create index ix_storage_settings_events_scope on wine_storage_settings_events(tenant_id,store_id,created_at);
