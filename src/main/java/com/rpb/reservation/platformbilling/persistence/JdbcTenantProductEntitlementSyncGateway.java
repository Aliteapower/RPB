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
                config_json, enabled_by, enabled_at
            )
            select stores.tenant_id,
                   stores.id,
                   ?,
                   true,
                   true,
                   '{}'::jsonb,
                   ?,
                   now()
            from stores
            where stores.tenant_id = ?
              and stores.deleted_at is null
            on conflict (tenant_id, store_id, app_key) do nothing
            """,
            appKey,
            operatorUserId,
            tenantId
        );
    }
}
