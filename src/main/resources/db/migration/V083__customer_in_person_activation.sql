create unique index ux_auth_accounts_activation_scope on auth_accounts(id,tenant_id);

create table customer_activation_codes (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    customer_id uuid not null,
    issued_by uuid not null,
    identity_confirmed_at timestamptz not null default now(),
    code_hash text not null check(length(code_hash)=64),
    attempts integer not null default 0 check(attempts between 0 and 5),
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    foreign key(customer_id,tenant_id) references customers(id,tenant_id),
    foreign key(issued_by,tenant_id) references auth_accounts(id,tenant_id),
    check(expires_at>created_at)
);
create index ix_customer_activation_codes_lookup on customer_activation_codes
    (tenant_id,store_id,customer_id,created_at desc,id desc) where consumed_at is null;

-- Old phone-suffix sessions cannot be used to set the first password after this release.
update customer_auth_sessions s set status='revoked',revoked_at=now(),updated_at=now(),version=s.version+1
where s.status='active' and s.deleted_at is null and (s.password_change_required or exists(
    select 1 from customer_password_credentials c where c.tenant_id=s.tenant_id and c.customer_id=s.customer_id and c.password_change_required
));
