package com.rpb.reservation.platformbilling.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTenantProductEntitlementSyncGateway implements TenantProductEntitlementSyncGateway {
    private final JdbcTemplate jdbc;

    public JdbcTenantProductEntitlementSyncGateway(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enableTenantApp(UUID tenantId, String appKey, OffsetDateTime validFrom, OffsetDateTime validUntil, UUID operatorUserId) {
        jdbc.update(
            """
            insert into tenant_app_entitlements (
                tenant_id, app_key, status, valid_from, valid_until,
                config_json, enabled_by, enabled_at
            )
            values (?, ?, 'enabled', ?, ?, '{}'::jsonb, ?, now())
            on conflict (tenant_id, app_key) do update
            set status = 'enabled',
                valid_from = excluded.valid_from,
                valid_until = excluded.valid_until,
                enabled_by = excluded.enabled_by,
                enabled_at = now(),
                updated_at = now()
            """,
            tenantId,
            appKey,
            validFrom,
            validUntil,
            operatorUserId
        );
        upsertStoreSettings(tenantId, appKey, operatorUserId);
    }

    @Override
    public void suspendTenantApp(UUID tenantId, String appKey, UUID operatorUserId) {
        syncStatus(tenantId, appKey, "suspended", operatorUserId);
    }

    @Override
    public void disableTenantApp(UUID tenantId, String appKey, UUID operatorUserId) {
        syncStatus(tenantId, appKey, "disabled", operatorUserId);
    }

    private void syncStatus(UUID tenantId, String appKey, String status, UUID operatorUserId) {
        jdbc.update(
            """
            insert into tenant_app_entitlements (
                tenant_id, app_key, status, config_json, enabled_by, enabled_at
            )
            values (?, ?, ?, '{}'::jsonb, ?, now())
            on conflict (tenant_id, app_key) do update
            set status = excluded.status,
                updated_at = now()
            """,
            tenantId,
            appKey,
            status,
            operatorUserId
        );
    }

    private void upsertStoreSettings(UUID tenantId, String appKey, UUID operatorUserId) {
        jdbc.update(
            """
            insert into store_app_settings (
                tenant_id, store_id, app_key, is_enabled, entry_visible,
                config_json, enabled_by, enabled_at, disabled_at
            )
            select item.tenant_id,
                   item.store_id,
                   item.app_key,
                   true,
                   true,
                   '{}'::jsonb,
                   ?,
                   now(),
                   null
            from tenant_product_subscription_items item
            join tenant_product_subscriptions subscription
              on subscription.id = item.subscription_id
             and subscription.tenant_id = item.tenant_id
             and subscription.app_key = item.app_key
             and subscription.status = 'active'
            join stores store
              on store.id = item.store_id
             and store.tenant_id = item.tenant_id
             and store.status = 'active'
             and store.deleted_at is null
            where item.tenant_id = ?
              and item.app_key = ?
              and item.scope_type = 'store'
              and item.status = 'active'
            on conflict (tenant_id, store_id, app_key) do update
            set is_enabled = true,
                entry_visible = true,
                enabled_by = excluded.enabled_by,
                enabled_at = now(),
                disabled_at = null,
                updated_at = now()
            where store_app_settings.is_enabled is distinct from true
               or store_app_settings.entry_visible is distinct from true
               or store_app_settings.disabled_at is not null
            """,
            operatorUserId,
            tenantId,
            appKey
        );
    }
}
