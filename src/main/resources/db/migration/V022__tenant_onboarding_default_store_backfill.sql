with tenants_without_store as (
    select
        tenant.id as tenant_id,
        tenant.tenant_code,
        tenant.display_name,
        tenant.status,
        coalesce(nullif(trim(tenant.default_locale), ''), 'zh-CN') as locale,
        tenant.contact_phone,
        tenant.address
    from tenants tenant
    where tenant.deleted_at is null
      and not exists (
          select 1
          from stores existing_store
          where existing_store.tenant_id = tenant.id
            and existing_store.deleted_at is null
      )
),
inserted_default_stores as (
    insert into stores (
        tenant_id,
        store_code,
        display_name,
        status,
        timezone,
        locale,
        date_format,
        time_format,
        currency,
        share_contact_phone,
        share_address
    )
    select
        tenant_id,
        tenant_code,
        display_name,
        case status
            when 'active' then 'active'
            when 'closed' then 'archived'
            when 'suspended' then 'inactive'
            else 'created'
        end,
        'Asia/Singapore',
        locale,
        'DD-MM-YYYY',
        'HH:mm',
        'SGD',
        contact_phone,
        address
    from tenants_without_store
    returning tenant_id, id as store_id
),
default_stores as (
    select tenant_id, store_id
    from inserted_default_stores
    union all
    select tenant_id, store_id
    from (
        select
            store.tenant_id,
            store.id as store_id,
            row_number() over (
                partition by store.tenant_id
                order by store.created_at, store.id
            ) as store_rank
        from stores store
        join tenants tenant on tenant.id = store.tenant_id
        where tenant.deleted_at is null
          and store.deleted_at is null
    ) ranked_store
    where store_rank = 1
),
updated_tenant_admin_accounts as (
    update auth_accounts account
    set default_store_id = default_store.store_id,
        updated_at = now(),
        version = version + 1
    from default_stores default_store
    where account.tenant_id = default_store.tenant_id
      and account.actor_type = 'tenant_admin'
      and account.deleted_at is null
      and account.default_store_id is null
    returning account.id as account_id, default_store.tenant_id, default_store.store_id
),
tenant_admin_accounts as (
    select account.id as account_id, account.tenant_id, default_store.store_id
    from auth_accounts account
    join default_stores default_store on default_store.tenant_id = account.tenant_id
    where account.actor_type = 'tenant_admin'
      and account.deleted_at is null
)
insert into auth_account_store_access (account_id, tenant_id, store_id)
select account_id, tenant_id, store_id
from tenant_admin_accounts
where not exists (
    select 1
    from auth_account_store_access existing_access
    where existing_access.account_id = tenant_admin_accounts.account_id
      and existing_access.tenant_id = tenant_admin_accounts.tenant_id
      and existing_access.store_id = tenant_admin_accounts.store_id
      and existing_access.deleted_at is null
);
