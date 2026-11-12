with required_permissions(permission_code) as (
    values
        ('payment.settings.manage'),
        ('payment.intent.view'),
        ('payment.intent.create'),
        ('payment.verification.review')
),
tenant_admin_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code = 'tenant_admin'
     and role.deleted_at is null
    where account.actor_type <> 'platform_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account.account_id, permission.permission_code
from tenant_admin_accounts account
cross join required_permissions permission
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = permission.permission_code
      and existing.deleted_at is null
);
