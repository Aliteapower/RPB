create unique index if not exists ux_auth_accounts_platform_username_active
    on auth_accounts (lower(username))
    where actor_type = 'platform_admin'
      and deleted_at is null;

create unique index if not exists ux_auth_accounts_tenant_username_active
    on auth_accounts (tenant_id, lower(username))
    where actor_type <> 'platform_admin'
      and tenant_id is not null
      and deleted_at is null;

drop index if exists ux_auth_accounts_username_active;
