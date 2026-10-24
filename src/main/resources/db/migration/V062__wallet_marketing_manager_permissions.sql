-- Extend existing wallet management roles only. Ordinary employees get no new points/marketing authority.
insert into auth_account_permissions(account_id,permission_code)
select distinct a.id,p.permission from auth_accounts a
join auth_account_roles r on r.account_id=a.id and r.deleted_at is null
cross join(values('wallet.marketing.view'),('wallet.marketing.manage'),('wallet.points.view'),('wallet.points.grant'),('wallet.points.redeem'),('wallet.points.adjust'),('wallet.points.reverse'))p(permission)
where a.deleted_at is null and a.actor_type<>'platform_admin' and r.role_code in ('tenant_admin','store_manager')
and not exists(select 1 from auth_account_permissions ep where ep.account_id=a.id and ep.permission_code=p.permission and ep.deleted_at is null);
