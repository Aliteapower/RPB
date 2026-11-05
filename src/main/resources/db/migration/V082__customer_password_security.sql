-- Password bootstrap sessions may only complete password change, never access business data.
alter table customer_auth_sessions add column password_change_required boolean not null default false;
alter table customer_password_credentials add column password_change_required boolean not null default true;
-- V058 inserts the bootstrap credential at version 0; explicit password changes increment it.
update customer_password_credentials set password_change_required=(version=0);

-- Legacy password sessions did not record whether the phone suffix was used.
-- Require re-authentication instead of silently granting existing bootstrap sessions full access.
update customer_auth_sessions s set status='revoked', revoked_at=now(), updated_at=now(), version=s.version+1
where s.status='active' and s.deleted_at is null and exists (
    select 1 from customer_password_credentials c where c.tenant_id=s.tenant_id and c.customer_id=s.customer_id
);

create unique index ux_customer_auth_accounts_security_scope on customer_auth_accounts(id,tenant_id,customer_id);

create table customer_security_email_codes (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    customer_id uuid not null,
    auth_account_id uuid not null,
    purpose text not null check(purpose in ('bind_email','reset_password')),
    email text not null,
    code_hash text not null check(length(code_hash)=64),
    attempts integer not null default 0 check(attempts between 0 and 5),
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    foreign key(store_id,tenant_id) references stores(id,tenant_id),
    foreign key(customer_id,tenant_id) references customers(id,tenant_id),
    foreign key(auth_account_id,tenant_id,customer_id) references customer_auth_accounts(id,tenant_id,customer_id),
    check(expires_at>created_at)
);
create index ix_customer_security_email_codes_lookup on customer_security_email_codes
    (tenant_id,store_id,customer_id,purpose,created_at desc,id desc) where consumed_at is null;
