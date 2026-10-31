-- Wallet owns stored value. Product registration never grants a tenant subscription.
insert into platform_apps(app_key,app_name,status,default_entry_route,description,sort_order,config_json)
values ('wallet','储值钱包','active','/stores/:storeId/wallet','线下充值、顾客授权消费与不足额补付',50,'{}'::jsonb);
insert into platform_product_line_prices(app_key,billing_cycle,amount,currency,status)
values ('wallet','monthly',0,'SGD','disabled'),('wallet','yearly',0,'SGD','disabled');

alter table stores add constraint uq_wallet_store_issuer unique(id,tenant_id,operating_entity_id);

create table wallet_programs (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, operating_entity_id uuid not null,
    currency text not null default 'SGD' check(currency='SGD'),
    channels jsonb not null default '["cash","paynow","credit_card"]'::jsonb,
    status text not null default 'active' check(status in ('active','frozen','closed')),
    version integer not null default 0 check(version>=0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    foreign key(operating_entity_id,tenant_id) references operating_entities(id,tenant_id),
    unique(tenant_id,operating_entity_id,currency), unique(id,tenant_id), unique(id,tenant_id,operating_entity_id),
    check(jsonb_typeof(channels)='array' and channels <@ '["cash","paynow","credit_card"]'::jsonb and jsonb_array_length(channels)>0)
);
create table wallet_program_stores (
    program_id uuid not null, tenant_id uuid not null, store_id uuid not null, operating_entity_id uuid not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    primary key(program_id,store_id), unique(program_id,tenant_id,store_id),
    foreign key(program_id,tenant_id,operating_entity_id) references wallet_programs(id,tenant_id,operating_entity_id),
    foreign key(store_id,tenant_id,operating_entity_id) references stores(id,tenant_id,operating_entity_id)
);
create table wallet_accounts (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null, customer_id uuid not null,
    currency text not null default 'SGD' check(currency='SGD'),
    balance_cents bigint not null default 0, reserved_cents bigint not null default 0,
    status text not null default 'active' check(status in ('active','frozen','closed')),
    version integer not null default 0 check(version>=0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    foreign key(program_id,tenant_id) references wallet_programs(id,tenant_id),
    foreign key(customer_id,tenant_id) references customers(id,tenant_id),
    unique(tenant_id,program_id,customer_id,currency), unique(id,tenant_id), unique(id,tenant_id,program_id),
    check(balance_cents between 0 and 9999999999999 and reserved_cents between 0 and balance_cents)
);
create table wallet_entries (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null,
    account_id uuid not null, store_id uuid not null, actor_id uuid not null,
    kind text not null check(kind in ('topup_credit','bill_debit','bill_refund','topup_refund')),
    source_id uuid not null, amount_cents bigint not null check(amount_cents>0),
    before_cents bigint not null check(before_cents>=0), after_cents bigint not null check(after_cents>=0),
    account_version integer not null check(account_version>0), original_entry_id uuid null,
    created_at timestamptz not null default now(),
    foreign key(account_id,tenant_id,program_id) references wallet_accounts(id,tenant_id,program_id),
    foreign key(program_id,tenant_id,store_id) references wallet_program_stores(program_id,tenant_id,store_id),
    unique(id,tenant_id), unique(tenant_id,kind,source_id), unique(account_id,account_version),
    foreign key(original_entry_id,tenant_id) references wallet_entries(id,tenant_id),
    check((kind in ('topup_credit','bill_refund') and after_cents=before_cents+amount_cents)
       or (kind in ('bill_debit','topup_refund') and after_cents=before_cents-amount_cents))
);
create index ix_wallet_entries_account on wallet_entries(tenant_id,account_id,created_at desc,id);

create table wallet_topups (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null,
    account_id uuid not null, store_id uuid not null, amount_cents bigint not null check(amount_cents>0),
    channel text not null check(channel in ('cash','paynow','credit_card')),
    status text not null default 'pending_collection' check(status in ('pending_collection','verified_pending_credit','credited','cancelled','exception')),
    created_by uuid not null, version integer not null default 0 check(version>=0),
    refunded_cents bigint not null default 0 check(refunded_cents between 0 and amount_cents),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    foreign key(account_id,tenant_id,program_id) references wallet_accounts(id,tenant_id,program_id),
    foreign key(program_id,tenant_id,store_id) references wallet_program_stores(program_id,tenant_id,store_id),
    unique(id,tenant_id), unique(id,tenant_id,store_id), unique(id,tenant_id,program_id,account_id,store_id)
);
create index ix_wallet_topups_store on wallet_topups(tenant_id,store_id,created_at desc);

create table wallet_bills (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null,
    account_id uuid not null, store_id uuid not null,
    amount_cents bigint not null check(amount_cents>0), wallet_paid_cents bigint not null default 0 check(wallet_paid_cents>=0),
    external_paid_cents bigint not null default 0 check(external_paid_cents>=0),
    status text not null default 'unpaid' check(status in ('unpaid','partially_paid','paid')),
    wallet_applied boolean not null default false, closed boolean not null default false, refund_status text not null default 'none'
        check(refund_status in ('none','refund_pending','partially_refunded','refunded')),
    code_hash text not null unique, code_expires_at timestamptz not null default now(), created_by uuid not null, version integer not null default 0 check(version>=0),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    foreign key(account_id,tenant_id,program_id) references wallet_accounts(id,tenant_id,program_id),
    foreign key(program_id,tenant_id,store_id) references wallet_program_stores(program_id,tenant_id,store_id),
    unique(id,tenant_id), unique(id,tenant_id,store_id), unique(id,tenant_id,program_id,account_id,store_id), unique(id,tenant_id,account_id),
    check(wallet_paid_cents+external_paid_cents<=amount_cents),
    check((status='unpaid' and wallet_paid_cents+external_paid_cents=0)
       or (status='partially_paid' and wallet_paid_cents+external_paid_cents>0 and wallet_paid_cents+external_paid_cents<amount_cents)
       or (status='paid' and wallet_paid_cents+external_paid_cents=amount_cents))
);
create index ix_wallet_bills_store on wallet_bills(tenant_id,store_id,created_at desc);
create index ix_wallet_bills_account on wallet_bills(tenant_id,account_id,created_at desc);

create table wallet_collections (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, store_id uuid not null,
    topup_id uuid null, bill_id uuid null,
    channel text not null check(channel in ('cash','paynow','credit_card')),
    amount_cents bigint not null check(amount_cents>0), collection_account text not null, receipt_reference text not null,
    terminal_code text not null, note text not null default '', confirmed_by uuid not null,
    verification_method text not null default 'offline_staff' check(verification_method='offline_staff'),
    is_exception boolean not null default false, exception_reason text not null default '',
    refunded_cents bigint not null default 0 check(refunded_cents between 0 and amount_cents),
    created_at timestamptz not null default now(),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    foreign key(topup_id,tenant_id,store_id) references wallet_topups(id,tenant_id,store_id),
    foreign key(bill_id,tenant_id,store_id) references wallet_bills(id,tenant_id,store_id),
    check(num_nonnulls(topup_id,bill_id)=1),
    unique(id,tenant_id), unique(tenant_id,channel,collection_account,receipt_reference)
);
create unique index uq_wallet_applied_topup_collection on wallet_collections(topup_id) where is_exception=false;

create table wallet_payment_codes (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, account_id uuid not null,
    code_hash text not null unique, bill_id uuid null, expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    foreign key(account_id,tenant_id) references wallet_accounts(id,tenant_id),
    foreign key(bill_id,tenant_id,account_id) references wallet_bills(id,tenant_id,account_id)
);
create table wallet_authorizations (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, bill_id uuid not null, account_id uuid not null,
    amount_cents bigint not null check(amount_cents>=0), account_version integer not null, bill_version integer not null,
    customer_id uuid not null, expires_at timestamptz not null, consumed_at timestamptz null,
    created_at timestamptz not null default now(),
    foreign key(bill_id,tenant_id,account_id) references wallet_bills(id,tenant_id,account_id),
    foreign key(customer_id,tenant_id) references customers(id,tenant_id)
);

create table wallet_refunds (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, program_id uuid not null,
    account_id uuid not null, store_id uuid not null, topup_id uuid null, bill_id uuid null,
    exception_collection_id uuid null,
    amount_cents bigint not null check(amount_cents>0), wallet_cents bigint not null check(wallet_cents>=0),
    status text not null check(status in ('pending','completed','cancelled')), reason text not null,
    created_by uuid not null, confirmed_by uuid null, version integer not null default 0,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    foreign key(account_id,tenant_id,program_id) references wallet_accounts(id,tenant_id,program_id),
    foreign key(program_id,tenant_id,store_id) references wallet_program_stores(program_id,tenant_id,store_id),
    foreign key(topup_id,tenant_id,program_id,account_id,store_id) references wallet_topups(id,tenant_id,program_id,account_id,store_id),
    foreign key(bill_id,tenant_id,program_id,account_id,store_id) references wallet_bills(id,tenant_id,program_id,account_id,store_id),
    foreign key(exception_collection_id,tenant_id) references wallet_collections(id,tenant_id),
    check(num_nonnulls(topup_id,bill_id)=1), check(wallet_cents<=amount_cents), unique(id,tenant_id)
);
create table wallet_refund_parts (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, refund_id uuid not null,
    collection_id uuid not null, amount_cents bigint not null check(amount_cents>0),
    completed boolean not null default false, receipt_reference text null,
    receipt_key text null,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    foreign key(refund_id,tenant_id) references wallet_refunds(id,tenant_id),
    foreign key(collection_id,tenant_id) references wallet_collections(id,tenant_id), unique(refund_id,collection_id), unique(tenant_id,receipt_key)
);
create index ix_wallet_refunds_source on wallet_refunds(tenant_id,topup_id,bill_id,status);

create table wallet_commands (
    tenant_id uuid not null references tenants(id), principal_id uuid not null, operation text not null,
    idempotency_key text not null check(length(idempotency_key) between 1 and 100),
    request_hash text not null, response jsonb not null,
    created_at timestamptz not null default now(),
    primary key(tenant_id,principal_id,operation,idempotency_key)
);
create table wallet_audit (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null, store_id uuid not null,
    actor_id uuid not null, action text not null, source_id uuid not null, details jsonb not null,
    created_at timestamptz not null default now(),
    foreign key(store_id,tenant_id) references stores(id,tenant_id)
);
create index ix_wallet_audit_store on wallet_audit(tenant_id,store_id,created_at desc);

-- Platform-billing-owned replay snapshots for explicitly selected-store commands.
create table platform_billing_command_results (
    tenant_id uuid not null references tenants(id), operation text not null, idempotency_key text not null,
    request_hash text not null, response jsonb not null, created_at timestamptz not null default now(),
    primary key(tenant_id,operation,idempotency_key)
);

create function wallet_entries_immutable() returns trigger language plpgsql as $$
begin raise exception 'wallet entries are append-only'; end $$;
create trigger wallet_entries_immutable before update or delete on wallet_entries
for each row execute function wallet_entries_immutable();

-- Shared customer identity must not be merged/archived/deleted underneath a wallet.
create function wallet_customer_identity_guard() returns trigger language plpgsql as $$
begin
    if exists(select 1 from wallet_accounts where tenant_id=old.tenant_id and customer_id=old.id) then
        if tg_op='DELETE' then raise exception 'customer has wallet records'; end if;
        if new.tenant_id is distinct from old.tenant_id or new.id is distinct from old.id
           or new.status is distinct from old.status or new.deleted_at is distinct from old.deleted_at
           or new.merged_into_customer_id is distinct from old.merged_into_customer_id then
            raise exception 'customer has wallet records';
        end if;
    end if;
    if tg_op='DELETE' then return old; end if;
    return new;
end $$;
create trigger wallet_customer_identity_guard before update or delete on customers
for each row execute function wallet_customer_identity_guard();

insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,p.permission from auth_accounts a
join auth_account_roles r on r.account_id=a.id and r.deleted_at is null
cross join (values ('wallet.view'),('wallet.topup.create'),('wallet.charge.create'),('wallet.collection.confirm'),
    ('wallet.refund.manage'),('wallet.reconcile.view'),('wallet.settings.manage')) p(permission)
where a.deleted_at is null and a.actor_type<>'platform_admin'
and (r.role_code in ('tenant_admin','store_manager')
    or (r.role_code='store_staff' and p.permission in ('wallet.view','wallet.topup.create','wallet.charge.create')))
and not exists(select 1 from auth_account_permissions ep where ep.account_id=a.id and ep.permission_code=p.permission and ep.deleted_at is null);
