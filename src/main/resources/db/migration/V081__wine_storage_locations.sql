create table wine_storage_locations (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,
 name text not null check(length(btrim(name)) between 1 and 100),
 is_default boolean not null default false,version integer not null default 0 check(version>=0),
 created_by uuid not null,updated_by uuid not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(id,tenant_id,store_id),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create unique index ux_storage_location_name on wine_storage_locations(tenant_id,store_id,lower(btrim(name)));
create unique index ux_storage_location_default on wine_storage_locations(tenant_id,store_id) where is_default;
create table wine_storage_location_events (
 id uuid primary key,tenant_id uuid not null,store_id uuid not null,location_id uuid not null,
 actor_id uuid not null,details jsonb not null,created_at timestamptz not null default now(),
 foreign key(location_id,tenant_id,store_id) references wine_storage_locations(id,tenant_id,store_id)
);
create index ix_storage_location_events_scope on wine_storage_location_events(tenant_id,store_id,location_id,created_at);
