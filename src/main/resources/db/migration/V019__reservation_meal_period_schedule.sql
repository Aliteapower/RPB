create table if not exists platform_reservation_meal_period_seeds (
    id uuid primary key default gen_random_uuid(),
    period_key text not null,
    display_name text not null,
    start_local_time time without time zone not null,
    end_local_time time without time zone not null,
    crosses_next_day boolean not null default false,
    slot_interval_minutes integer not null default 30,
    status text not null default 'active',
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint uq_platform_reservation_meal_period_key unique (period_key),
    constraint ck_platform_reservation_meal_period_status check (status in ('active', 'disabled')),
    constraint ck_platform_reservation_meal_period_interval check (slot_interval_minutes between 5 and 240),
    constraint ck_platform_reservation_meal_period_key check (length(trim(period_key)) > 0),
    constraint ck_platform_reservation_meal_period_name check (length(trim(display_name)) > 0)
);

create unique index if not exists ux_platform_reservation_meal_period_key_active
    on platform_reservation_meal_period_seeds (period_key)
    where deleted_at is null;

create index if not exists ix_platform_reservation_meal_period_active
    on platform_reservation_meal_period_seeds (status, sort_order)
    where deleted_at is null;

create table if not exists store_reservation_meal_period_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    use_platform_seed boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ux_store_reservation_meal_period_settings_scope unique (tenant_id, store_id),
    constraint fk_store_reservation_meal_period_settings_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id)
);

create table if not exists store_reservation_meal_periods (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    source_seed_id uuid null references platform_reservation_meal_period_seeds(id),
    period_key text not null,
    display_name text not null,
    start_local_time time without time zone not null,
    end_local_time time without time zone not null,
    crosses_next_day boolean not null default false,
    slot_interval_minutes integer not null default 30,
    status text not null default 'active',
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_reservation_meal_periods_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint ck_store_reservation_meal_period_status check (status in ('active', 'disabled')),
    constraint ck_store_reservation_meal_period_interval check (slot_interval_minutes between 5 and 240),
    constraint ck_store_reservation_meal_period_key check (length(trim(period_key)) > 0),
    constraint ck_store_reservation_meal_period_name check (length(trim(display_name)) > 0)
);

create unique index if not exists ux_store_reservation_meal_period_key_active
    on store_reservation_meal_periods (tenant_id, store_id, period_key)
    where deleted_at is null;

create index if not exists ix_store_reservation_meal_period_active
    on store_reservation_meal_periods (tenant_id, store_id, status, sort_order)
    where deleted_at is null;

insert into platform_reservation_meal_period_seeds (
    id,
    period_key,
    display_name,
    start_local_time,
    end_local_time,
    crosses_next_day,
    slot_interval_minutes,
    status,
    sort_order
)
values
    (
        '9d81f2ab-f8de-4b8a-bc77-58bb7b026001',
        'lunch',
        '午餐',
        time '11:00',
        time '15:00',
        false,
        30,
        'active',
        10
    ),
    (
        '9d81f2ab-f8de-4b8a-bc77-58bb7b026002',
        'dinner',
        '晚餐',
        time '17:00',
        time '00:30',
        true,
        30,
        'active',
        20
    )
on conflict (period_key) do update
set display_name = excluded.display_name,
    start_local_time = excluded.start_local_time,
    end_local_time = excluded.end_local_time,
    crosses_next_day = excluded.crosses_next_day,
    slot_interval_minutes = excluded.slot_interval_minutes,
    status = excluded.status,
    sort_order = excluded.sort_order,
    updated_at = now(),
    version = platform_reservation_meal_period_seeds.version + 1;

insert into auth_account_permissions (account_id, permission_code)
select account.id, 'platform.reservation_meal_period.manage'
from auth_accounts account
where account.actor_type = 'platform_admin'
  and account.deleted_at is null
  and not exists (
      select 1
      from auth_account_permissions existing
      where existing.account_id = account.id
        and existing.permission_code = 'platform.reservation_meal_period.manage'
        and existing.deleted_at is null
  );
