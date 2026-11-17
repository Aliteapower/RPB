create table wallet_voucher_campaigns (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    owner_store_id uuid not null,
    activity_code integer not null check (activity_code between 0 and 9999),
    status varchar(12) not null check (status in ('draft','active','disabled','archived')),
    version integer not null default 0,
    revision integer not null default 0,
    total integer not null check (total between 1 and 1000000),
    issued integer not null default 0 check (issued between 0 and total),
    redeemed integer not null default 0 check (redeemed between 0 and issued),
    starts_at timestamptz not null,
    ends_at timestamptz not null check (ends_at > starts_at),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (tenant_id, activity_code),
    unique (tenant_id, id),
    foreign key (owner_store_id, tenant_id) references stores(id, tenant_id)
);
create index wallet_voucher_campaigns_owner on wallet_voucher_campaigns(tenant_id, owner_store_id, created_at desc);

create table wallet_voucher_revisions (
    campaign_id uuid not null references wallet_voucher_campaigns(id) on delete cascade,
    revision integer not null,
    face jsonb not null,
    primary key (campaign_id, revision)
);

create table wallet_voucher_claims (
    campaign_id uuid not null,
    tenant_id uuid not null,
    revision integer not null,
    ip inet not null,
    receipt_hash varchar(64) not null,
    code_hash varchar(64) not null unique,
    claimed_at timestamptz not null,
    redeemed_at timestamptz,
    redeemed_store_id uuid,
    redeemed_by uuid,
    primary key (campaign_id, ip),
    unique (campaign_id, receipt_hash),
    foreign key (tenant_id, campaign_id) references wallet_voucher_campaigns(tenant_id, id),
    foreign key (campaign_id, revision) references wallet_voucher_revisions(campaign_id, revision),
    foreign key (redeemed_store_id, tenant_id) references stores(id, tenant_id),
    check ((redeemed_at is null and redeemed_store_id is null and redeemed_by is null)
        or (redeemed_at is not null and redeemed_store_id is not null and redeemed_by is not null))
);
