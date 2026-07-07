create table operating_entities (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    entity_code text not null,
    display_name text not null,
    status text not null,
    default_locale text null,
    contact_phone text null,
    address text null,
    principal_name text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_operating_entities_status check (status in ('active', 'inactive', 'archived')),
    constraint uq_operating_entities_id_tenant unique (id, tenant_id)
);

create unique index ux_operating_entities_tenant_code_active
    on operating_entities (tenant_id, lower(entity_code))
    where deleted_at is null;

create index ix_operating_entities_tenant_status
    on operating_entities (tenant_id, status, deleted_at);

alter table stores
    add column operating_entity_id uuid null;

alter table stores
    add constraint fk_stores_operating_entity_scope
        foreign key (operating_entity_id, tenant_id)
        references operating_entities(id, tenant_id);

create index ix_stores_tenant_operating_entity
    on stores (tenant_id, operating_entity_id, status, deleted_at);

create table tenant_host_aliases (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    alias_code text not null,
    alias_type text not null,
    default_store_id uuid null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_tenant_host_aliases_type check (alias_type in ('tenant', 'store')),
    constraint ck_tenant_host_aliases_status check (status in ('active', 'inactive', 'archived')),
    constraint ck_tenant_host_aliases_default_store_required check (
        (alias_type = 'tenant' and default_store_id is null)
        or (alias_type = 'store' and default_store_id is not null)
    ),
    constraint fk_tenant_host_aliases_default_store_scope
        foreign key (default_store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_tenant_host_aliases_id_tenant unique (id, tenant_id)
);

create unique index ux_tenant_host_aliases_code_active
    on tenant_host_aliases (lower(alias_code))
    where deleted_at is null;

create index ix_tenant_host_aliases_tenant
    on tenant_host_aliases (tenant_id, alias_type, status, deleted_at);
