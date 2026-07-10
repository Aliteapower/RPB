with tenant_admin_accounts as (
    select account.id as account_id
    from auth_accounts account
    where account.actor_type = 'tenant_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_roles (account_id, role_code)
select account_id, 'tenant_admin'
from tenant_admin_accounts account
where not exists (
    select 1
    from auth_account_roles existing
    where existing.account_id = account.account_id
      and existing.role_code = 'tenant_admin'
      and existing.deleted_at is null
);

with tenant_admin_accounts as (
    select account.id as account_id
    from auth_accounts account
    where account.actor_type = 'tenant_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account_id, 'tenant.admin.manage'
from tenant_admin_accounts account
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = 'tenant.admin.manage'
      and existing.deleted_at is null
);

with store_manager_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code = 'store_manager'
     and role.deleted_at is null
    where account.actor_type = 'staff'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_roles (account_id, role_code)
select account_id, 'tenant_admin'
from store_manager_accounts manager
where not exists (
    select 1
    from auth_account_roles existing
    where existing.account_id = manager.account_id
      and existing.role_code = 'tenant_admin'
      and existing.deleted_at is null
);

with store_manager_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code = 'store_manager'
     and role.deleted_at is null
    where account.actor_type = 'staff'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account_id, 'tenant.admin.manage'
from store_manager_accounts manager
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = manager.account_id
      and existing.permission_code = 'tenant.admin.manage'
      and existing.deleted_at is null
);

with required_permissions(permission_code) as (
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
        ('queue.display.view'),
        ('table.view'),
        ('table.switch'),
        ('customer.lookup'),
        ('walkin.direct_seating.create'),
        ('walkin.queue.create'),
        ('cleaning.start'),
        ('cleaning.complete')
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
