-- Optional private evidence only; uploads never mutate wallet balances or payment intents.
alter table wallet_collections add constraint uq_wallet_collection_proof_scope unique(id,tenant_id,store_id);
create table wallet_collection_proofs (
 id uuid primary key, tenant_id uuid not null, store_id uuid not null,
 topup_id uuid, bill_id uuid, channel text not null check(split_part(channel,':',1)='paynow'),
 expected_account text not null, amount_cents bigint not null check(amount_cents>0),
 system_reference text not null, idempotency_key text not null check(length(idempotency_key) between 1 and 160),
 request_digest text not null check(length(request_digest)=64), image_digest text not null check(length(image_digest)=64),
 content_type text not null check(content_type in ('image/png','image/jpeg')),
 image_bytes bytea not null check(octet_length(image_bytes) between 1 and 5242880),
 evidence_json jsonb not null check(jsonb_typeof(evidence_json)='object'),
 confirmation_json jsonb not null default '{}'::jsonb check(jsonb_typeof(confirmation_json)='object'),
 collection_id uuid, created_by uuid not null, created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 check(num_nonnulls(topup_id,bill_id)=1),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 foreign key(topup_id,tenant_id,store_id) references wallet_topups(id,tenant_id,store_id),
 foreign key(bill_id,tenant_id,store_id) references wallet_bills(id,tenant_id,store_id),
 foreign key(collection_id,tenant_id,store_id) references wallet_collections(id,tenant_id,store_id),
 unique(tenant_id,store_id,idempotency_key),unique(collection_id)
);
create unique index uq_wallet_proof_confirmed_image on wallet_collection_proofs(tenant_id,expected_account,image_digest) where collection_id is not null;
create index ix_wallet_proof_topup on wallet_collection_proofs(tenant_id,store_id,topup_id,created_at);
create index ix_wallet_proof_bill on wallet_collection_proofs(tenant_id,store_id,bill_id,created_at);
