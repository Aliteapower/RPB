with required_permissions(permission_code) as (
    values
        ('payment.intent.view'),
        ('payment.intent.create')
),
store_staff_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code in ('store_staff', 'store_manager')
     and role.deleted_at is null
    where account.actor_type = 'staff'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account.account_id, permission.permission_code
from store_staff_accounts account
cross join required_permissions permission
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = permission.permission_code
      and existing.deleted_at is null
);
