-- Confirmation intent only; completed debits remain in the existing points ledger.
create table wallet_points_redemption_requests (
 id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null,
 store_id uuid not null, customer_id uuid not null, created_by uuid not null,
 quantity int not null check(quantity between 1 and 1000000000), description text not null check(length(trim(description)) between 1 and 1000),
 status text not null default 'pending' check(status in ('pending','completed','cancelled')),
 code_hash text, expires_at timestamptz not null, entry_id uuid, created_at timestamptz not null default now(), completed_at timestamptz,
 foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id),
 foreign key(entry_id,tenant_id) references loyalty_entries(id,tenant_id),
 check((status='completed')=(entry_id is not null)),
 check((status='completed')=(completed_at is not null)), check(code_hash is null or length(code_hash)=64)
);
create unique index ux_wallet_redemption_code on wallet_points_redemption_requests(tenant_id,program_id,code_hash) where code_hash is not null;
create index ix_wallet_redemption_customer on wallet_points_redemption_requests(tenant_id,store_id,customer_id,created_at desc);
