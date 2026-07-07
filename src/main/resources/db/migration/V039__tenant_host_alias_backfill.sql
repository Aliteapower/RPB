insert into tenant_host_aliases (
    tenant_id, alias_code, alias_type, default_store_id, status
)
select
    tenant.id,
    tenant.tenant_code,
    'tenant',
    null,
    case
        when tenant.status = 'active' then 'active'
        when tenant.status = 'closed' then 'archived'
        else 'inactive'
    end
from tenants tenant
where tenant.deleted_at is null
  and not exists (
      select 1
      from tenant_host_aliases alias
      where lower(alias.alias_code) = lower(tenant.tenant_code)
        and alias.deleted_at is null
  )
on conflict do nothing;
