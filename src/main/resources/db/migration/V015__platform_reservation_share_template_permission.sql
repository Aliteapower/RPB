insert into auth_account_permissions (account_id, permission_code)
select account.id, 'platform.reservation_share_template.manage'
from auth_accounts account
where account.actor_type = 'platform_admin'
  and account.deleted_at is null
  and not exists (
      select 1
      from auth_account_permissions existing
      where existing.account_id = account.id
        and existing.permission_code = 'platform.reservation_share_template.manage'
        and existing.deleted_at is null
  );
