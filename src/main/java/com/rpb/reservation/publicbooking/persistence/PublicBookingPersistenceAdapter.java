package com.rpb.reservation.publicbooking.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.publicbooking.application.PublicBookingQuotaOverride;
import com.rpb.reservation.publicbooking.application.PublicBookingSettings;
import com.rpb.reservation.publicbooking.application.PublicBookingStoreProfile;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsManagementPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingTableCapacityRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicBookingPersistenceAdapter implements
    PublicBookingSettingsRepositoryPort,
    PublicBookingTableCapacityRepositoryPort,
    PublicBookingStoreRepositoryPort,
    PublicBookingSettingsManagementPort {

    private final JdbcTemplate jdbc;

    public PublicBookingPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PublicBookingSettings> findSettings(StoreScope scope) {
        return jdbc.query(
            """
            select enabled, require_customer_login, default_quota_mode, default_quota_percent,
                   default_table_count, default_guest_count, min_lead_minutes, max_advance_days
            from store_public_booking_settings
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> settings(rs),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    @Override
    public Optional<PublicBookingQuotaOverride> findQuotaOverride(
        StoreScope scope,
        BusinessDate businessDate,
        String periodKey
    ) {
        return jdbc.query(
            """
            select period_key, quota_mode, quota_percent, table_count, guest_count
            from store_public_booking_quota_overrides
            where tenant_id = ?
              and store_id = ?
              and business_date = ?
              and coalesce(period_key, '') = coalesce(?, '')
              and deleted_at is null
            """,
            (rs, rowNum) -> quotaOverride(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            Date.valueOf(businessDate.value()),
            periodKey
        ).stream().findFirst();
    }

    @Override
    public List<Integer> findActiveTableCapacityMaxValues(StoreScope scope) {
        return jdbc.queryForList(
            """
            select capacity_max
            from dining_tables
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
              and status <> 'inactive'
            order by capacity_max desc, table_code asc
            """,
            Integer.class,
            scope.tenantId().value(),
            scope.storeId().value()
        );
    }

    @Override
    public Optional<PublicBookingStoreProfile> findActiveStoreProfileByStoreId(UUID storeId) {
        return jdbc.query(
            """
            select id, tenant_id, display_name, timezone, share_address, google_map_url,
                   share_contact_phone, share_email, whatsapp_business_phone_e164
            from stores
            where id = ?
              and status = 'active'
              and deleted_at is null
            """,
            (rs, rowNum) -> storeProfile(rs),
            storeId
        ).stream().findFirst();
    }

    @Override
    public Optional<PublicBookingStoreProfile> findActiveStoreProfile(StoreScope scope) {
        return jdbc.query(
            """
            select id, tenant_id, display_name, timezone, share_address, google_map_url,
                   share_contact_phone, share_email, whatsapp_business_phone_e164
            from stores
            where id = ?
              and tenant_id = ?
              and status = 'active'
              and deleted_at is null
            """,
            (rs, rowNum) -> storeProfile(rs),
            scope.storeId().value(),
            scope.tenantId().value()
        ).stream().findFirst();
    }

    @Override
    public PublicBookingSettings saveSettings(StoreScope scope, PublicBookingSettings settings) {
        jdbc.update(
            """
            insert into store_public_booking_settings (
                tenant_id, store_id, enabled, require_customer_login, default_quota_mode,
                default_quota_percent, default_table_count, default_guest_count,
                min_lead_minutes, max_advance_days
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict on constraint ux_store_public_booking_settings_scope
            do update set
                enabled = excluded.enabled,
                require_customer_login = excluded.require_customer_login,
                default_quota_mode = excluded.default_quota_mode,
                default_quota_percent = excluded.default_quota_percent,
                default_table_count = excluded.default_table_count,
                default_guest_count = excluded.default_guest_count,
                min_lead_minutes = excluded.min_lead_minutes,
                max_advance_days = excluded.max_advance_days,
                updated_at = now(),
                deleted_at = null,
                version = store_public_booking_settings.version + 1
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            settings.enabled(),
            settings.requireCustomerLogin(),
            settings.defaultQuotaMode(),
            settings.defaultQuotaPercent(),
            settings.defaultTableCount(),
            settings.defaultGuestCount(),
            settings.minLeadMinutes(),
            settings.maxAdvanceDays()
        );
        return findSettings(scope).orElse(settings);
    }

    @Override
    public PublicBookingQuotaOverride saveQuotaOverride(
        StoreScope scope,
        java.time.LocalDate businessDate,
        PublicBookingQuotaOverride override
    ) {
        int updated = jdbc.update(
            """
            update store_public_booking_quota_overrides
            set quota_mode = ?,
                quota_percent = ?,
                table_count = ?,
                guest_count = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and business_date = ?
              and coalesce(period_key, '') = coalesce(?, '')
              and deleted_at is null
            """,
            override.quotaMode(),
            override.quotaPercent(),
            override.tableCount(),
            override.guestCount(),
            scope.tenantId().value(),
            scope.storeId().value(),
            Date.valueOf(businessDate),
            override.periodKey()
        );
        if (updated == 0) {
            jdbc.update(
                """
                insert into store_public_booking_quota_overrides (
                    tenant_id, store_id, business_date, period_key, quota_mode,
                    quota_percent, table_count, guest_count
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scope.tenantId().value(),
                scope.storeId().value(),
                Date.valueOf(businessDate),
                override.periodKey(),
                override.quotaMode(),
                override.quotaPercent(),
                override.tableCount(),
                override.guestCount()
            );
        }
        return findQuotaOverride(scope, new BusinessDate(businessDate), override.periodKey()).orElse(override);
    }

    private static PublicBookingSettings settings(ResultSet rs) throws SQLException {
        return new PublicBookingSettings(
            rs.getBoolean("enabled"),
            rs.getBoolean("require_customer_login"),
            rs.getString("default_quota_mode"),
            rs.getInt("default_quota_percent"),
            nullableInteger(rs, "default_table_count"),
            nullableInteger(rs, "default_guest_count"),
            rs.getInt("min_lead_minutes"),
            rs.getInt("max_advance_days")
        );
    }

    private static PublicBookingQuotaOverride quotaOverride(ResultSet rs) throws SQLException {
        return new PublicBookingQuotaOverride(
            rs.getString("period_key"),
            rs.getString("quota_mode"),
            nullableInteger(rs, "quota_percent"),
            nullableInteger(rs, "table_count"),
            nullableInteger(rs, "guest_count")
        );
    }

    private static PublicBookingStoreProfile storeProfile(ResultSet rs) throws SQLException {
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        UUID storeId = rs.getObject("id", UUID.class);
        return new PublicBookingStoreProfile(
            new StoreScope(new TenantId(tenantId), storeId),
            rs.getString("display_name"),
            rs.getString("timezone"),
            rs.getString("share_address"),
            rs.getString("google_map_url"),
            rs.getString("share_contact_phone"),
            rs.getString("share_email"),
            rs.getString("whatsapp_business_phone_e164")
        );
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
