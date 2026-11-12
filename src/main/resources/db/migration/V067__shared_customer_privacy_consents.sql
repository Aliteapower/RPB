-- Creation-only merchant registration evidence. No historical consent backfill.
create table customer_privacy_consents (
 id uuid primary key,tenant_id uuid not null,customer_id uuid not null,store_id uuid not null,
 accepted boolean not null check(accepted),recorded_by uuid references auth_accounts(id),recorded_by_customer_id uuid,recorded_by_name text not null check(length(recorded_by_name)>0),
 recorded_at timestamptz not null default now(),
 entry_source text not null check(entry_source in ('wallet','tenant_admin','customer_registration')),
 confirmation_method text not null check(confirmation_method in ('merchant_attestation','customer_registration_submission')),
 notice_version text not null check(length(notice_version)>0),notice_text_zh text not null check(length(notice_text_zh)>0),notice_text_en text not null check(length(notice_text_en)>0),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id),
 foreign key(recorded_by_customer_id,tenant_id) references customers(id,tenant_id),
 foreign key(store_id,tenant_id) references stores(id,tenant_id),
 unique(tenant_id,customer_id),
 check((confirmation_method='merchant_attestation' and recorded_by is not null and recorded_by_customer_id is null and entry_source in ('wallet','tenant_admin')) or (confirmation_method='customer_registration_submission' and recorded_by is null and recorded_by_customer_id is not null and recorded_by_customer_id=customer_id and entry_source='customer_registration'))
);
create index ix_customer_privacy_scope_time on customer_privacy_consents(tenant_id,customer_id,recorded_at desc,id desc);
