-- Pending intent only. Money and points remain in their original independent ledgers.
create table wallet_merchant_orders (
 id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null,
 store_id uuid not null, created_by uuid not null, kind text not null check(kind in ('money','points')),
 amount_cents bigint, quantity int, description text not null default '' check(length(description)<=1000),
 status text not null default 'pending' check(status in ('pending','completed','cancelled')),
 customer_id uuid, bill_id uuid, points_entry_id uuid, code_hash text, expires_at timestamptz not null,
 created_at timestamptz not null default now(), completed_at timestamptz,
 foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id),
 foreign key(bill_id,tenant_id) references wallet_bills(id,tenant_id),
 foreign key(points_entry_id,tenant_id) references loyalty_entries(id,tenant_id),
 check((kind='money' and amount_cents is not null and amount_cents between 1 and 9999999999999 and quantity is null) or
       (kind='points' and quantity is not null and quantity between 1 and 1000000000 and amount_cents is null and length(trim(description))>0)),
 check((status='completed')=(customer_id is not null)),
 check((status='completed')=(completed_at is not null)),
 check((status='completed' and kind='money')=(bill_id is not null)),
 check((status='completed' and kind='points')=(points_entry_id is not null)),
 check(code_hash is null or length(code_hash)=64)
);
create unique index ux_wallet_merchant_code on wallet_merchant_orders(tenant_id,program_id,code_hash) where code_hash is not null;
create unique index ux_wallet_merchant_bill on wallet_merchant_orders(tenant_id,bill_id) where bill_id is not null;
create unique index ux_wallet_merchant_points on wallet_merchant_orders(tenant_id,points_entry_id) where points_entry_id is not null;
create index ix_wallet_merchant_store on wallet_merchant_orders(tenant_id,store_id,created_at desc,id);
