update tenants
set tenant_code = '20000000',
    display_name = case
        when display_name = 'Local Validation Tenant' then '食刻租户'
        else display_name
    end,
    default_locale = coalesce(default_locale, 'zh-CN'),
    updated_at = now(),
    version = version + 1
where id = '10000000-0000-0000-0000-000000000983'::uuid
  and deleted_at is null
  and not exists (
      select 1
      from tenants existing
      where lower(existing.tenant_code) = lower('20000000')
        and existing.id <> '10000000-0000-0000-0000-000000000983'::uuid
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
select account_id, 'platform.tenant.manage'
from platform_accounts
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = platform_accounts.account_id
      and existing.permission_code = 'platform.tenant.manage'
      and existing.deleted_at is null
);
