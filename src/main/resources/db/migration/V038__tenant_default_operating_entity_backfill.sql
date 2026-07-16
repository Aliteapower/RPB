insert into operating_entities (
    tenant_id, entity_code, display_name, status,
    default_locale, contact_phone, address, principal_name
)
select
    tenant.id,
    tenant.tenant_code,
    tenant.display_name,
    case
        when tenant.status = 'closed' then 'archived'
        when tenant.status = 'suspended' then 'inactive'
        else 'active'
    end,
    tenant.default_locale,
    tenant.contact_phone,
    tenant.address,
    tenant.principal_name
from tenants tenant
where tenant.deleted_at is null
  and not exists (
      select 1
      from operating_entities existing
      where existing.tenant_id = tenant.id
        and existing.deleted_at is null
  )
on conflict do nothing;
