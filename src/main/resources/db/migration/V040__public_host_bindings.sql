create table public_host_bindings (
    id uuid primary key default gen_random_uuid(),
    host_alias_id uuid not null,
    tenant_id uuid not null references tenants(id),
    host_prefix text not null,
    hostname text not null,
    host_type text not null,
    tls_status text not null,
    certificate_name text null,
    covered_at timestamptz null,
    last_checked_at timestamptz null,
    last_error text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_public_host_bindings_host_alias_scope
        foreign key (host_alias_id, tenant_id) references tenant_host_aliases(id, tenant_id),
    constraint ck_public_host_bindings_type check (host_type in ('tenant', 'store')),
    constraint ck_public_host_bindings_tls_status check (tls_status in ('pending', 'covered', 'failed', 'archived')),
    constraint ck_public_host_bindings_hostname_lower check (hostname = lower(hostname))
);

create unique index ux_public_host_bindings_alias_active
    on public_host_bindings (host_alias_id)
    where deleted_at is null;

create unique index ux_public_host_bindings_hostname_active
    on public_host_bindings (lower(hostname))
    where deleted_at is null;

create index ix_public_host_bindings_tls_status
    on public_host_bindings (tls_status, deleted_at, updated_at);

create index ix_public_host_bindings_tenant
    on public_host_bindings (tenant_id, host_type, tls_status, deleted_at);
