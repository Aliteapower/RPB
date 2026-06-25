create table auth_accounts (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid null references tenants(id),
    username text not null,
    display_name text not null,
    actor_type text not null,
    status text not null,
    password_hash text not null,
    password_algo text not null default 'bcrypt-lowercase-v1',
    failed_login_count integer not null default 0,
    locked_until_at timestamptz null,
    last_login_at timestamptz null,
    last_login_failed_at timestamptz null,
    default_store_id uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_auth_accounts_default_store_scope
        foreign key (default_store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_auth_accounts_status check (status in ('active', 'disabled', 'locked')),
    constraint ck_auth_accounts_failed_login_count check (failed_login_count >= 0),
    constraint ck_auth_accounts_default_store_scope check (
        default_store_id is null or tenant_id is not null
    )
);

create unique index ux_auth_accounts_username_active
    on auth_accounts (lower(username))
    where deleted_at is null;

create index ix_auth_accounts_tenant_status
    on auth_accounts (tenant_id, status, deleted_at);

create table auth_account_roles (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth_accounts(id) on delete cascade,
    role_code text not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint ck_auth_account_roles_code check (length(trim(role_code)) > 0)
);

create unique index ux_auth_account_roles_active
    on auth_account_roles (account_id, role_code)
    where deleted_at is null;

create table auth_account_permissions (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth_accounts(id) on delete cascade,
    permission_code text not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint ck_auth_account_permissions_code check (length(trim(permission_code)) > 0)
);

create unique index ux_auth_account_permissions_active
    on auth_account_permissions (account_id, permission_code)
    where deleted_at is null;

create table auth_account_store_access (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth_accounts(id) on delete cascade,
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint fk_auth_account_store_access_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id)
);

create unique index ux_auth_account_store_access_active
    on auth_account_store_access (account_id, tenant_id, store_id)
    where deleted_at is null;

create table auth_user_sessions (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth_accounts(id) on delete cascade,
    tenant_id uuid null references tenants(id),
    session_hash text not null,
    status text not null,
    expires_at timestamptz not null,
    last_seen_at timestamptz not null default now(),
    remote_addr text null,
    user_agent text null,
    revoked_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_auth_user_sessions_status check (status in ('active', 'revoked', 'expired')),
    constraint ck_auth_user_sessions_expiry check (expires_at > created_at)
);

create unique index ux_auth_user_sessions_session_hash
    on auth_user_sessions (session_hash)
    where deleted_at is null;

create index ix_auth_user_sessions_account_status
    on auth_user_sessions (account_id, status, expires_at);

create table auth_slider_captcha_challenges (
    id uuid primary key default gen_random_uuid(),
    target_x integer not null,
    target_y integer not null,
    piece_size integer not null,
    image_width integer not null,
    image_height integer not null,
    tolerance_px integer not null,
    status text not null,
    attempt_count integer not null default 0,
    max_attempts integer not null default 3,
    expires_at timestamptz not null,
    verified_at timestamptz null,
    failed_at timestamptz null,
    consumed_at timestamptz null,
    remote_addr text null,
    user_agent text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_auth_slider_captcha_status check (status in ('created', 'verified', 'failed', 'consumed', 'expired')),
    constraint ck_auth_slider_captcha_attempts check (attempt_count >= 0 and max_attempts > 0),
    constraint ck_auth_slider_captcha_geometry check (
        target_x >= 0
        and target_y >= 0
        and piece_size > 0
        and image_width > piece_size
        and image_height > piece_size
        and target_x <= image_width - piece_size
        and target_y <= image_height - piece_size
        and tolerance_px >= 0
    ),
    constraint ck_auth_slider_captcha_expiry check (expires_at > created_at)
);

create index ix_auth_slider_captcha_active
    on auth_slider_captcha_challenges (status, expires_at, deleted_at);

with validation_scope as (
    select
        tenant.id as tenant_id,
        store.id as store_id
    from tenants tenant
    join stores store on store.tenant_id = tenant.id
    where tenant.id = '10000000-0000-0000-0000-000000000983'::uuid
      and store.id = '20000000-0000-0000-0000-000000000983'::uuid
      and tenant.deleted_at is null
      and store.deleted_at is null
),
seed_accounts(account_id, username, display_name, actor_type) as (
    values
        ('30000000-0000-0000-0000-000000000901'::uuid, 'sysadmin', '平台管理员', 'platform_admin'),
        ('30000000-0000-0000-0000-000000000902'::uuid, '20000000', '租户管理员', 'tenant_admin'),
        ('30000000-0000-0000-0000-000000000903'::uuid, '1000', '租户员工', 'staff')
)
insert into auth_accounts (
    id, tenant_id, username, display_name, actor_type, status,
    password_hash, password_algo, default_store_id
)
select
    seed.account_id,
    scope.tenant_id,
    seed.username,
    seed.display_name,
    seed.actor_type,
    'active',
    '$2a$10$ktA3gOgzus6v0bsJqw53.OerYPoQT6oet7NDdkmNhYYZaKH9ix9Vy',
    'bcrypt-lowercase-v1',
    scope.store_id
from seed_accounts seed
cross join validation_scope scope
where not exists (
    select 1
    from auth_accounts existing
    where lower(existing.username) = lower(seed.username)
      and existing.deleted_at is null
);

with seed_roles(username, role_code) as (
    values
        ('sysadmin', 'platform_admin'),
        ('sysadmin', 'tenant_admin'),
        ('20000000', 'tenant_admin'),
        ('1000', 'store_staff')
)
insert into auth_account_roles (account_id, role_code)
select account.id, seed.role_code
from seed_roles seed
join auth_accounts account on lower(account.username) = lower(seed.username)
where account.deleted_at is null
  and not exists (
      select 1
      from auth_account_roles existing
      where existing.account_id = account.id
        and existing.role_code = seed.role_code
        and existing.deleted_at is null
  );

with seed_permissions(permission_code) as (
    values
        ('reservation.create'),
        ('reservation.check_in'),
        ('reservation.seat'),
        ('reservation.today_view'),
        ('reservation.queue'),
        ('reservation.cancel'),
        ('reservation.no_show'),
        ('reservation.complete'),
        ('queue.view'),
        ('queue.call'),
        ('queue.seat'),
        ('queue.skip'),
        ('queue.rejoin'),
        ('queue.cancel'),
        ('table.view'),
        ('table.switch'),
        ('customer.lookup'),
        ('walkin.direct_seating.create'),
        ('walkin.queue.create'),
        ('cleaning.start'),
        ('cleaning.complete')
),
seed_accounts(username) as (
    values ('sysadmin'), ('20000000'), ('1000')
)
insert into auth_account_permissions (account_id, permission_code)
select account.id, permission.permission_code
from seed_accounts seed
join auth_accounts account on lower(account.username) = lower(seed.username)
cross join seed_permissions permission
where account.deleted_at is null
  and not exists (
      select 1
      from auth_account_permissions existing
      where existing.account_id = account.id
        and existing.permission_code = permission.permission_code
        and existing.deleted_at is null
  );

with validation_scope as (
    select
        tenant.id as tenant_id,
        store.id as store_id
    from tenants tenant
    join stores store on store.tenant_id = tenant.id
    where tenant.id = '10000000-0000-0000-0000-000000000983'::uuid
      and store.id = '20000000-0000-0000-0000-000000000983'::uuid
      and tenant.deleted_at is null
      and store.deleted_at is null
),
seed_accounts(username) as (
    values ('sysadmin'), ('20000000'), ('1000')
)
insert into auth_account_store_access (account_id, tenant_id, store_id)
select account.id, scope.tenant_id, scope.store_id
from seed_accounts seed
join auth_accounts account on lower(account.username) = lower(seed.username)
cross join validation_scope scope
where account.deleted_at is null
  and not exists (
      select 1
      from auth_account_store_access existing
      where existing.account_id = account.id
        and existing.tenant_id = scope.tenant_id
        and existing.store_id = scope.store_id
        and existing.deleted_at is null
  );
