create table wine_storage_custodians (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,
 name text not null check(length(trim(name)) between 1 and 100),
 kind text not null check(kind in('hostess','custodian')),enabled boolean not null default true,
 version integer not null default 0 check(version>=0),created_by uuid not null,updated_by uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,store_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create unique index ux_storage_custodian_active_name on wine_storage_custodians(tenant_id,store_id,lower(name),kind) where enabled;
create index ix_storage_custodians_scope on wine_storage_custodians(tenant_id,store_id,enabled);
create table wine_storage_custodian_events (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,custodian_id uuid not null,actor_id uuid not null,
 details jsonb not null,created_at timestamptz not null default now(),
 foreign key(custodian_id,tenant_id,store_id) references wine_storage_custodians(id,tenant_id,store_id)
);
create index ix_storage_custodian_events_scope on wine_storage_custodian_events(tenant_id,store_id,custodian_id,created_at);
create index ix_storage_pending_review on wine_storage_applications(tenant_id,store_id,created_at,id) where status='pending';
insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,'wine_storage.custodians.manage' from auth_accounts a join auth_account_roles r on r.account_id=a.id and r.deleted_at is null
where a.deleted_at is null and a.actor_type<>'platform_admin' and r.role_code in('tenant_admin','store_manager')
and not exists(select 1 from auth_account_permissions ep where ep.account_id=a.id and ep.permission_code='wine_storage.custodians.manage' and ep.deleted_at is null);
