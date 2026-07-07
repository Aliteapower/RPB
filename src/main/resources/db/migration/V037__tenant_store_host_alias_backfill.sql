with globally_unique_active_store_codes as (
    select lower(store.store_code) as normalized_store_code
    from stores store
    join tenants tenant
      on tenant.id = store.tenant_id
     and tenant.status = 'active'
     and tenant.deleted_at is null
    where store.status = 'active'
      and store.deleted_at is null
      and not exists (
          select 1
          from tenants code_owner
          where lower(code_owner.tenant_code) = lower(store.store_code)
            and code_owner.status = 'active'
            and code_owner.deleted_at is null
      )
    group by lower(store.store_code)
    having count(*) = 1
),
store_alias_backfill as (
    select
        store.tenant_id,
        store.id as store_id,
        store.store_code
    from stores store
    join tenants tenant
      on tenant.id = store.tenant_id
     and tenant.status = 'active'
     and tenant.deleted_at is null
    join globally_unique_active_store_codes unique_code
      on unique_code.normalized_store_code = lower(store.store_code)
    where store.status = 'active'
      and store.deleted_at is null
      and not exists (
          select 1
          from tenant_host_aliases alias
          where lower(alias.alias_code) = lower(store.store_code)
            and alias.deleted_at is null
      )
)
insert into tenant_host_aliases (
    tenant_id,
    alias_code,
    alias_type,
    default_store_id,
    status
)
select
    tenant_id,
    store_code,
    'store',
    store_id,
    'active'
from store_alias_backfill;
