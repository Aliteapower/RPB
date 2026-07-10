insert into store_app_settings (
    tenant_id,
    store_id,
    app_key,
    is_enabled,
    entry_visible,
    config_json,
    enabled_by,
    enabled_at,
    disabled_at
)
select item.tenant_id,
       item.store_id,
       item.app_key,
       true,
       true,
       '{}'::jsonb,
       coalesce(subscription.operator_user_id, entitlement.enabled_by),
       now(),
       null
from tenant_product_subscription_items item
join tenant_product_subscriptions subscription
  on subscription.id = item.subscription_id
 and subscription.tenant_id = item.tenant_id
 and subscription.app_key = item.app_key
 and subscription.status = 'active'
join tenant_app_entitlements entitlement
  on entitlement.tenant_id = item.tenant_id
 and entitlement.app_key = item.app_key
 and entitlement.status in ('enabled', 'trial')
 and (entitlement.valid_until is null or entitlement.valid_until > now())
join stores store
  on store.id = item.store_id
 and store.tenant_id = item.tenant_id
 and store.status = 'active'
 and store.deleted_at is null
where item.scope_type = 'store'
  and item.status = 'active'
on conflict (tenant_id, store_id, app_key) do update
set is_enabled = true,
    entry_visible = true,
    enabled_by = coalesce(excluded.enabled_by, store_app_settings.enabled_by),
    enabled_at = coalesce(store_app_settings.enabled_at, excluded.enabled_at, now()),
    disabled_at = null,
    updated_at = now()
where store_app_settings.is_enabled is distinct from true
   or store_app_settings.entry_visible is distinct from true
   or store_app_settings.disabled_at is not null;
