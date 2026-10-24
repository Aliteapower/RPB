-- Customer identity is captured server-side; employee proofs remain private to staff.
alter table wallet_collection_proofs add column uploaded_by_customer_id uuid;
alter table wallet_collection_proofs add constraint fk_wallet_proof_customer foreign key(uploaded_by_customer_id,tenant_id) references customers(id,tenant_id);
create index ix_wallet_customer_proof_pending on wallet_collection_proofs(tenant_id,store_id,uploaded_by_customer_id,created_at) where uploaded_by_customer_id is not null and collection_id is null;
