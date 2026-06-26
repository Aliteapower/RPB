alter table platform_call_screen_ad_seed_sets
    drop constraint if exists ck_platform_call_screen_ad_seed_sets_type;

alter table platform_call_screen_ad_seed_sets
    add constraint ck_platform_call_screen_ad_seed_sets_type
        check (ad_type in ('text', 'image', 'media'));

alter table tenant_call_screen_ad_sets
    drop constraint if exists ck_tenant_call_screen_ad_sets_type;

alter table tenant_call_screen_ad_sets
    add constraint ck_tenant_call_screen_ad_sets_type
        check (ad_type in ('text', 'image', 'media'));

alter table store_call_screen_settings
    drop constraint if exists ck_store_call_screen_settings_mode;

alter table store_call_screen_settings
    add constraint ck_store_call_screen_settings_mode
        check (ad_mode in ('text', 'image', 'media'));

create table if not exists call_screen_media_assets (
    id uuid primary key default gen_random_uuid(),
    owner_scope text not null,
    tenant_id uuid null references tenants(id),
    media_kind text not null,
    content_type text not null,
    byte_size bigint not null,
    original_filename text not null,
    storage_key text not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint uq_call_screen_media_assets_storage_key unique (storage_key),
    constraint ck_call_screen_media_assets_owner check (owner_scope in ('platform', 'tenant')),
    constraint ck_call_screen_media_assets_tenant_scope check (
        (owner_scope = 'platform' and tenant_id is null)
        or (owner_scope = 'tenant' and tenant_id is not null)
    ),
    constraint ck_call_screen_media_assets_kind check (media_kind in ('image', 'video')),
    constraint ck_call_screen_media_assets_size check (byte_size > 0),
    constraint ck_call_screen_media_assets_status check (status in ('active', 'disabled'))
);

create index if not exists ix_call_screen_media_assets_tenant_scope
    on call_screen_media_assets (owner_scope, tenant_id, status)
    where deleted_at is null;

create table if not exists platform_call_screen_media_seed_slides (
    id uuid primary key default gen_random_uuid(),
    seed_set_id uuid not null references platform_call_screen_ad_seed_sets(id),
    media_asset_id uuid not null references call_screen_media_assets(id),
    media_kind text not null,
    title text null,
    alt_text text null,
    sort_order integer not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_platform_call_screen_media_seed_slides_kind check (media_kind in ('image', 'video')),
    constraint ck_platform_call_screen_media_seed_slides_sort check (sort_order > 0),
    constraint ck_platform_call_screen_media_seed_slides_status check (status in ('active', 'disabled'))
);

create unique index if not exists ux_platform_call_screen_media_seed_slides_sort
    on platform_call_screen_media_seed_slides (seed_set_id, sort_order)
    where deleted_at is null;

create table if not exists tenant_call_screen_media_slides (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    ad_set_id uuid not null,
    source_seed_slide_id uuid null references platform_call_screen_media_seed_slides(id),
    media_asset_id uuid not null references call_screen_media_assets(id),
    media_kind text not null,
    title text null,
    alt_text text null,
    sort_order integer not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint fk_tenant_call_screen_media_slides_ad_set_scope
        foreign key (ad_set_id, tenant_id) references tenant_call_screen_ad_sets(id, tenant_id),
    constraint ck_tenant_call_screen_media_slides_kind check (media_kind in ('image', 'video')),
    constraint ck_tenant_call_screen_media_slides_sort check (sort_order > 0),
    constraint ck_tenant_call_screen_media_slides_status check (status in ('active', 'disabled'))
);

create unique index if not exists ux_tenant_call_screen_media_slides_active_sort
    on tenant_call_screen_media_slides (tenant_id, ad_set_id, sort_order)
    where status = 'active' and deleted_at is null;

create index if not exists ix_tenant_call_screen_media_slides_scope
    on tenant_call_screen_media_slides (tenant_id, ad_set_id, status, sort_order)
    where deleted_at is null;

create or replace function enforce_tenant_call_screen_media_slide_ad_type()
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

    if actual_ad_type is not null and actual_ad_type not in ('image', 'media') then
        raise exception 'media slide ad_set_id must reference a media ad set';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_tenant_call_screen_media_slides_ad_type
    on tenant_call_screen_media_slides;

create trigger trg_tenant_call_screen_media_slides_ad_type
before insert or update of tenant_id, ad_set_id
on tenant_call_screen_media_slides
for each row
execute function enforce_tenant_call_screen_media_slide_ad_type();

create or replace function enforce_store_call_screen_settings_ad_mode()
returns trigger
language plpgsql
as $$
declare
    actual_ad_type text;
    actual_ad_status text;
begin
    if new.active_ad_set_id is null then
        return new;
    end if;

    select ad_type, status
    into actual_ad_type, actual_ad_status
    from tenant_call_screen_ad_sets
    where id = new.active_ad_set_id
      and tenant_id = new.tenant_id;

    if actual_ad_status is not null and actual_ad_status <> 'active' then
        raise exception 'store call screen active_ad_set_id must reference an active ad set';
    end if;

    if actual_ad_type is not null
       and actual_ad_type <> new.ad_mode
       and not (actual_ad_type = 'image' and new.ad_mode = 'media')
       and not (actual_ad_type = 'media' and new.ad_mode = 'image') then
        raise exception 'store call screen ad_mode must match active ad set ad_type';
    end if;

    return new;
end;
$$;

create or replace function enforce_tenant_call_screen_media_asset_scope()
returns trigger
language plpgsql
as $$
declare
    asset_owner_scope text;
    asset_tenant_id uuid;
    asset_media_kind text;
begin
    select owner_scope, tenant_id, media_kind
    into asset_owner_scope, asset_tenant_id, asset_media_kind
    from call_screen_media_assets
    where id = new.media_asset_id
      and deleted_at is null;

    if asset_owner_scope <> 'tenant' or asset_tenant_id <> new.tenant_id then
        raise exception 'tenant media slide asset must belong to the same tenant';
    end if;

    if asset_media_kind <> new.media_kind then
        raise exception 'tenant media slide media_kind must match asset media_kind';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_tenant_call_screen_media_slides_asset_scope
    on tenant_call_screen_media_slides;

create trigger trg_tenant_call_screen_media_slides_asset_scope
before insert or update of tenant_id, media_asset_id, media_kind
on tenant_call_screen_media_slides
for each row
execute function enforce_tenant_call_screen_media_asset_scope();

create or replace function enforce_platform_call_screen_media_asset_scope()
returns trigger
language plpgsql
as $$
declare
    asset_owner_scope text;
    asset_media_kind text;
begin
    select owner_scope, media_kind
    into asset_owner_scope, asset_media_kind
    from call_screen_media_assets
    where id = new.media_asset_id
      and deleted_at is null;

    if asset_owner_scope <> 'platform' then
        raise exception 'platform media seed slide asset must belong to platform scope';
    end if;

    if asset_media_kind <> new.media_kind then
        raise exception 'platform media seed slide media_kind must match asset media_kind';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_platform_call_screen_media_seed_slides_asset_scope
    on platform_call_screen_media_seed_slides;

create trigger trg_platform_call_screen_media_seed_slides_asset_scope
before insert or update of media_asset_id, media_kind
on platform_call_screen_media_seed_slides
for each row
execute function enforce_platform_call_screen_media_asset_scope();

insert into platform_call_screen_ad_seed_sets (
    seed_key,
    display_name,
    status,
    ad_type
)
values (
    'restaurant_media_default',
    '餐厅默认叫号屏图片/视频',
    'disabled',
    'media'
)
on conflict (seed_key) do update
set
    display_name = excluded.display_name,
    ad_type = excluded.ad_type,
    updated_at = now(),
    deleted_at = null,
    version = platform_call_screen_ad_seed_sets.version + 1;
