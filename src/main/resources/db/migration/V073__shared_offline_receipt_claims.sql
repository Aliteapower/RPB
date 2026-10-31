-- One external receipt cannot fund both wallet stored value and a card purchase.
create table payment_offline_receipt_claims (
 tenant_id uuid not null,store_id uuid not null,channel text not null,account text not null,receipt text not null,
 source_type text not null check(source_type in('wallet','cards')),source_id uuid not null,created_at timestamptz not null default now(),
 primary key(tenant_id,channel,account,receipt),foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
insert into payment_offline_receipt_claims(tenant_id,store_id,channel,account,receipt,source_type,source_id)
select tenant_id,store_id,split_part(channel,':',1),collection_account,receipt_reference,'wallet',coalesce(topup_id,bill_id) from wallet_collections on conflict do nothing;
