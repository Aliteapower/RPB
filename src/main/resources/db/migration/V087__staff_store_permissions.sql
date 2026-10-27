-- V083 supplies the (id,tenant_id) account unique key. No legacy grants are changed.
create table auth_staff_store_permissions (
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    account_id uuid not null,
    permissions text[] not null,
    version bigint not null check(version > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key(account_id,store_id),
    foreign key(account_id,tenant_id) references auth_accounts(id,tenant_id),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    check(array_position(permissions,null) is null)
);
create index ix_auth_staff_store_permissions_scope on auth_staff_store_permissions(tenant_id,store_id,account_id);
