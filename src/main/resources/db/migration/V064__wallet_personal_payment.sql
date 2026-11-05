-- Personal authorization is independent of identity and money-account creation.
create table wallet_personal_payment_policies (
 tenant_id uuid not null,program_id uuid not null,limit_cents bigint,
 version integer not null default 0 check(version>=0),consent_required_version integer not null default 0 check(consent_required_version>=0 and consent_required_version<=version),updated_by uuid,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,program_id),foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),
 check(limit_cents is null or limit_cents between 1 and 9999999999999)
);
create table wallet_personal_payment_consents (
 tenant_id uuid not null,program_id uuid not null,customer_id uuid not null,
 accepted boolean not null,limit_cents bigint,policy_version integer not null check(policy_version>=0),rule_version integer not null check(rule_version=1),
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,program_id,customer_id),foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),
 foreign key(customer_id,tenant_id) references customers(id,tenant_id),
 check(limit_cents is null or limit_cents between 1 and 9999999999999),check(not accepted or limit_cents is not null)
);
alter table wallet_bills add constraint uq_wallet_personal_bill_scope unique(id,tenant_id,program_id,store_id);
alter table wallet_customer_identity_codes
 add column payment_enabled boolean not null default false,
 add column payment_limit_cents bigint check(payment_limit_cents between 1 and 9999999999999),
 add column payment_policy_version integer check(payment_policy_version>=0),
 add column payment_bill_id uuid,
 add column payment_store_id uuid,
 add column payment_description varchar(200),
 add constraint fk_wallet_personal_code_bill foreign key(payment_bill_id,tenant_id,program_id,payment_store_id) references wallet_bills(id,tenant_id,program_id,store_id),
 add constraint ck_wallet_personal_payment_snapshot check(not payment_enabled or (payment_limit_cents is not null and payment_policy_version is not null)),
 add constraint ck_wallet_personal_bill_binding check((payment_bill_id is null)=(payment_store_id is null)),
 add constraint uq_wallet_personal_bill_binding unique(payment_bill_id);
create index ix_wallet_personal_consent_customer on wallet_personal_payment_consents(tenant_id,customer_id);
