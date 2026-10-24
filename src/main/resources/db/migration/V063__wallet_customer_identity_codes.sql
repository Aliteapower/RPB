-- Customer identity only: no money account or payment consent is required or granted.
create table wallet_customer_identity_codes (
 id uuid primary key default gen_random_uuid(),tenant_id uuid not null,program_id uuid not null,customer_id uuid not null,
 code_hash text not null unique check(code_hash ~ '^[a-f0-9]{64}$'),
 created_at timestamptz not null default now(),expires_at timestamptz not null,
 foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id),check(expires_at>=created_at)
);
create index ix_wallet_customer_identity_owner on wallet_customer_identity_codes(tenant_id,program_id,customer_id,expires_at);
create index ix_wallet_customer_identity_expiry on wallet_customer_identity_codes(expires_at);
