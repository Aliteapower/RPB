alter table tenants
    add column if not exists logo_media_asset_id uuid null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_tenants_logo_media_asset'
    ) then
        alter table tenants
            add constraint fk_tenants_logo_media_asset
                foreign key (logo_media_asset_id) references call_screen_media_assets(id);
    end if;
end;
$$;

create index if not exists ix_tenants_logo_media_asset
    on tenants (logo_media_asset_id)
    where logo_media_asset_id is not null;

create table if not exists platform_profile (
    id boolean primary key default true,
    platform_name text not null,
    uen text null,
    address text null,
    phone text null,
    email text null,
    website text null,
    logo_media_asset_id uuid null references call_screen_media_assets(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint ck_platform_profile_singleton check (id),
    constraint ck_platform_profile_name check (length(trim(platform_name)) > 0)
);

create table if not exists platform_social_links (
    id uuid primary key default gen_random_uuid(),
    display_name text not null,
    url text not null,
    logo_media_asset_id uuid null references call_screen_media_assets(id),
    sort_order integer not null,
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version integer not null default 0,
    constraint ck_platform_social_links_name check (length(trim(display_name)) > 0),
    constraint ck_platform_social_links_url check (length(trim(url)) > 0),
    constraint ck_platform_social_links_sort check (sort_order > 0),
    constraint ck_platform_social_links_status check (status in ('active', 'disabled'))
);

create unique index if not exists ux_platform_social_links_active_sort
    on platform_social_links (sort_order)
    where status = 'active' and deleted_at is null;

create index if not exists ix_platform_social_links_status_sort
    on platform_social_links (status, sort_order)
    where deleted_at is null;

create or replace function enforce_tenant_logo_media_asset_scope()
returns trigger
language plpgsql
as $$
declare
    asset_owner_scope text;
    asset_tenant_id uuid;
    asset_media_kind text;
begin
    if new.logo_media_asset_id is null then
        return new;
    end if;

    select owner_scope, tenant_id, media_kind
    into asset_owner_scope, asset_tenant_id, asset_media_kind
    from call_screen_media_assets
    where id = new.logo_media_asset_id
      and status = 'active'
      and deleted_at is null;

    if asset_owner_scope <> 'tenant' or asset_tenant_id <> new.id or asset_media_kind <> 'image' then
        raise exception 'tenant logo media asset must be an active image owned by the same tenant';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_tenants_logo_media_asset_scope
    on tenants;

create trigger trg_tenants_logo_media_asset_scope
before insert or update of id, logo_media_asset_id
on tenants
for each row
execute function enforce_tenant_logo_media_asset_scope();

create or replace function enforce_platform_logo_media_asset_scope()
returns trigger
language plpgsql
as $$
declare
    asset_owner_scope text;
    asset_media_kind text;
begin
    if new.logo_media_asset_id is null then
        return new;
    end if;

    select owner_scope, media_kind
    into asset_owner_scope, asset_media_kind
    from call_screen_media_assets
    where id = new.logo_media_asset_id
      and tenant_id is null
      and status = 'active'
      and deleted_at is null;

    if asset_owner_scope <> 'platform' or asset_media_kind <> 'image' then
        raise exception 'platform logo media asset must be an active platform image';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_platform_profile_logo_media_asset_scope
    on platform_profile;

create trigger trg_platform_profile_logo_media_asset_scope
before insert or update of logo_media_asset_id
on platform_profile
for each row
execute function enforce_platform_logo_media_asset_scope();

drop trigger if exists trg_platform_social_links_logo_media_asset_scope
    on platform_social_links;

create trigger trg_platform_social_links_logo_media_asset_scope
before insert or update of logo_media_asset_id
on platform_social_links
for each row
execute function enforce_platform_logo_media_asset_scope();

insert into platform_profile (id, platform_name)
values (true, 'RPB')
on conflict (id) do nothing;
