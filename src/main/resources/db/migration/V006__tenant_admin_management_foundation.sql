alter table auth_accounts
    add column if not exists contact_phone text null,
    add column if not exists email text null;

create index if not exists ix_auth_accounts_tenant_staff_keyword
    on auth_accounts (tenant_id, lower(username), lower(display_name))
    where actor_type = 'staff' and deleted_at is null;

update auth_accounts
set contact_phone = coalesce(contact_phone, '13800001000'),
    email = coalesce(email, 'staff1000@example.test'),
    updated_at = now(),
    version = version + 1
where lower(username) = lower('1000')
  and actor_type = 'staff'
  and deleted_at is null;

with tenant_admin_accounts as (
    select account.id as account_id
    from auth_accounts account
    join auth_account_roles role on role.account_id = account.id
    where account.actor_type = 'tenant_admin'
      and role.role_code = 'tenant_admin'
      and account.deleted_at is null
      and role.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account_id, 'tenant.admin.manage'
from tenant_admin_accounts
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = tenant_admin_accounts.account_id
      and existing.permission_code = 'tenant.admin.manage'
      and existing.deleted_at is null
);
