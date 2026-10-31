with required_permissions(permission_code) as (
    values
        ('payment.proof.review')
),
payment_proof_review_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code in ('tenant_admin', 'store_manager', 'store_staff')
     and role.deleted_at is null
    where account.actor_type <> 'platform_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account.account_id, permission.permission_code
from payment_proof_review_accounts account
cross join required_permissions permission
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = permission.permission_code
      and existing.deleted_at is null
);

update platform_apps
set config_json = jsonb_set(
    coalesce(config_json, '{}'::jsonb),
    '{entryPermissions}',
    coalesce(config_json -> 'entryPermissions', '[]'::jsonb) || '["payment.proof.review"]'::jsonb,
    true
)
where app_key = 'payment'
  and not jsonb_exists(coalesce(config_json -> 'entryPermissions', '[]'::jsonb), 'payment.proof.review');
