create table if not exists platform_call_screen_ad_seed_sets (
    id uuid primary key default gen_random_uuid(),
    seed_key text not null,
    display_name text not null,
    status text not null,
    ad_type text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint uq_platform_call_screen_ad_seed_sets_key unique (seed_key),
    constraint ck_platform_call_screen_ad_seed_sets_status check (status in ('active', 'disabled')),
    constraint ck_platform_call_screen_ad_seed_sets_type check (ad_type = 'text')
);

create index if not exists ix_platform_call_screen_ad_seed_sets_active
    on platform_call_screen_ad_seed_sets (status, ad_type, seed_key)
    where deleted_at is null;

create table if not exists platform_call_screen_ad_seed_slides (
    id uuid primary key default gen_random_uuid(),
    seed_set_id uuid not null references platform_call_screen_ad_seed_sets(id),
    title text not null,
    subtitle text not null,
    tagline text not null,
    sort_order integer not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_platform_call_screen_ad_seed_slides_sort check (sort_order > 0),
    constraint ck_platform_call_screen_ad_seed_slides_status check (status in ('active', 'disabled'))
);

create unique index if not exists ux_platform_call_screen_ad_seed_slides_sort
    on platform_call_screen_ad_seed_slides (seed_set_id, sort_order)
    where deleted_at is null;

create index if not exists ix_platform_call_screen_ad_seed_slides_active
    on platform_call_screen_ad_seed_slides (seed_set_id, status, sort_order)
    where deleted_at is null;

create table if not exists tenant_call_screen_ad_sets (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    source_seed_set_id uuid null references platform_call_screen_ad_seed_sets(id),
    name text not null,
    ad_type text not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint uq_tenant_call_screen_ad_sets_id_tenant unique (id, tenant_id),
    constraint ck_tenant_call_screen_ad_sets_type check (ad_type = 'text'),
    constraint ck_tenant_call_screen_ad_sets_status check (status in ('active', 'disabled'))
);

create index if not exists ix_tenant_call_screen_ad_sets_scope
    on tenant_call_screen_ad_sets (tenant_id, ad_type, status, deleted_at);

create unique index if not exists ux_tenant_call_screen_ad_sets_name_active
    on tenant_call_screen_ad_sets (tenant_id, name)
    where deleted_at is null;

create or replace function enforce_tenant_call_screen_ad_set_type_immutable()
returns trigger
language plpgsql
as $$
begin
    if old.ad_type <> new.ad_type then
        raise exception 'tenant call screen ad_type is immutable after creation';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_tenant_call_screen_ad_sets_type_immutable
    on tenant_call_screen_ad_sets;

create trigger trg_tenant_call_screen_ad_sets_type_immutable
before update of ad_type
on tenant_call_screen_ad_sets
for each row
execute function enforce_tenant_call_screen_ad_set_type_immutable();

create table if not exists tenant_call_screen_text_slides (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    ad_set_id uuid not null,
    source_seed_slide_id uuid null references platform_call_screen_ad_seed_slides(id),
    title text not null,
    subtitle text not null,
    tagline text not null,
    sort_order integer not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_tenant_call_screen_text_slides_ad_set_scope
        foreign key (ad_set_id, tenant_id) references tenant_call_screen_ad_sets(id, tenant_id),
    constraint ck_tenant_call_screen_text_slides_sort check (sort_order > 0),
    constraint ck_tenant_call_screen_text_slides_status check (status in ('active', 'disabled'))
);

create unique index if not exists ux_tenant_call_screen_text_slides_active_sort
    on tenant_call_screen_text_slides (tenant_id, ad_set_id, sort_order)
    where status = 'active' and deleted_at is null;

create index if not exists ix_tenant_call_screen_text_slides_scope
    on tenant_call_screen_text_slides (tenant_id, ad_set_id, status, sort_order)
    where deleted_at is null;

create table if not exists store_call_screen_settings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    active_ad_set_id uuid null,
    ad_mode text not null default 'text',
    status text not null default 'active',
    slide_duration_seconds integer not null default 5,
    state_poll_seconds integer not null default 3,
    show_waiting_preview boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_store_call_screen_settings_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_store_call_screen_settings_active_ad_set_scope
        foreign key (active_ad_set_id, tenant_id) references tenant_call_screen_ad_sets(id, tenant_id),
    constraint ck_store_call_screen_settings_mode check (ad_mode = 'text'),
    constraint ck_store_call_screen_settings_status check (status in ('active', 'disabled')),
    constraint ck_store_call_screen_settings_slide_duration check (slide_duration_seconds between 3 and 60),
    constraint ck_store_call_screen_settings_poll check (state_poll_seconds between 2 and 30)
);

create unique index if not exists uq_store_call_screen_settings_scope
    on store_call_screen_settings (tenant_id, store_id)
    where deleted_at is null;

create index if not exists ix_store_call_screen_settings_active_ad_set
    on store_call_screen_settings (tenant_id, active_ad_set_id)
    where active_ad_set_id is not null and deleted_at is null;

create or replace function enforce_tenant_call_screen_text_slide_ad_type()
returns trigger
language plpgsql
as $$
declare
    actual_ad_type text;
begin
    select ad_type
    into actual_ad_type
    from tenant_call_screen_ad_sets
    where id = new.ad_set_id
      and tenant_id = new.tenant_id;

    if actual_ad_type is not null and actual_ad_type <> 'text' then
        raise exception 'text slide ad_set_id must reference a text ad set';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_tenant_call_screen_text_slides_ad_type
    on tenant_call_screen_text_slides;

create trigger trg_tenant_call_screen_text_slides_ad_type
before insert or update of tenant_id, ad_set_id
on tenant_call_screen_text_slides
for each row
execute function enforce_tenant_call_screen_text_slide_ad_type();

create or replace function enforce_store_call_screen_settings_ad_mode()
returns trigger
language plpgsql
as $$
declare
    actual_ad_type text;
begin
    if new.active_ad_set_id is null then
        return new;
    end if;

    select ad_type
    into actual_ad_type
    from tenant_call_screen_ad_sets
    where id = new.active_ad_set_id
      and tenant_id = new.tenant_id;

    if actual_ad_type is not null and actual_ad_type <> new.ad_mode then
        raise exception 'store call screen ad_mode must match active ad set ad_type';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_store_call_screen_settings_ad_mode
    on store_call_screen_settings;

create trigger trg_store_call_screen_settings_ad_mode
before insert or update of tenant_id, active_ad_set_id, ad_mode
on store_call_screen_settings
for each row
execute function enforce_store_call_screen_settings_ad_mode();

insert into platform_call_screen_ad_seed_sets (
    seed_key,
    display_name,
    status,
    ad_type
)
values (
    'restaurant_default',
    '餐厅默认叫号屏文案',
    'active',
    'text'
)
on conflict (seed_key) do update
set
    display_name = excluded.display_name,
    status = excluded.status,
    ad_type = excluded.ad_type,
    updated_at = now(),
    deleted_at = null,
    version = platform_call_screen_ad_seed_sets.version + 1;

insert into platform_call_screen_ad_seed_slides (
    seed_set_id,
    title,
    subtitle,
    tagline,
    sort_order,
    status
)
select
    seed.id,
    slide.title,
    slide.subtitle,
    slide.tagline,
    slide.sort_order,
    'active'
from platform_call_screen_ad_seed_sets seed
cross join (
    values
        (1, '欢迎光临', '食刻 · 餐厅', '新鲜食材 · 匠心烹饪 · 极致服务'),
        (2, '今日推荐', '招牌炭烤牛排', '精选澳洲谷饲牛肉 · 现点现烤'),
        (3, '特惠活动', '工作日午餐8折', '周一至周五 11:00-14:00 全场8折'),
        (4, '会员专享', '充值满赠', '充500送50 · 充1000送120')
) as slide(sort_order, title, subtitle, tagline)
where seed.seed_key = 'restaurant_default'
on conflict (seed_set_id, sort_order) where deleted_at is null do update
set
    title = excluded.title,
    subtitle = excluded.subtitle,
    tagline = excluded.tagline,
    status = excluded.status,
    updated_at = now(),
    version = platform_call_screen_ad_seed_slides.version + 1;

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
insert into auth_account_permissions (account_id, permission_code)
select account.id, 'queue.display.view'
from seed_accounts seed
join auth_accounts account on lower(account.username) = lower(seed.username)
join validation_scope scope on scope.tenant_id = account.tenant_id
where account.default_store_id = scope.store_id
  and account.deleted_at is null
  and exists (
      select 1
      from auth_account_store_access access
      where access.account_id = account.id
        and access.tenant_id = scope.tenant_id
        and access.store_id = scope.store_id
        and access.deleted_at is null
  )
  and not exists (
      select 1
      from auth_account_permissions existing
      where existing.account_id = account.id
        and existing.permission_code = 'queue.display.view'
        and existing.deleted_at is null
  );

with platform_accounts as (
    select account.id as account_id
    from auth_accounts account
    join auth_account_roles role on role.account_id = account.id
    where role.role_code = 'platform_admin'
      and account.deleted_at is null
      and role.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account_id, 'platform.call_screen_ad.manage'
from platform_accounts
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = platform_accounts.account_id
      and existing.permission_code = 'platform.call_screen_ad.manage'
      and existing.deleted_at is null
);
