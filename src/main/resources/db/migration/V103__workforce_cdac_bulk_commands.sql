-- Additive command receipt; existing profiles, snapshots and payments are untouched.
create table workforce_cdac_bulk_commands (
 id uuid primary key,tenant_id uuid not null,company_id uuid not null,store_id uuid not null,actor_id uuid not null,
 payload jsonb not null,response jsonb not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(company_id,tenant_id) references operating_entities(id,tenant_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(actor_id,tenant_id) references auth_accounts(id,tenant_id)
);
create index ix_workforce_cdac_bulk_scope on workforce_cdac_bulk_commands(tenant_id,company_id,created_at desc);
