package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantAdminProfileRepository {
    private final JdbcTemplate jdbc;

    public TenantAdminProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TenantAdminProfile> find(StoreScope scope) {
        return jdbc.query(
            """
            select tenant.id, tenant.tenant_code, tenant.display_name, tenant.status,
                   tenant.default_locale, tenant.contact_phone, tenant.address,
                   tenant.principal_name, tenant.logo_media_asset_id,
                   tenant.created_at, tenant.updated_at
            from tenants tenant
            where tenant.id = ?
              and tenant.deleted_at is null
            """,
            (rs, rowNum) -> profile(rs),
            scope.tenantId().value()
        ).stream().findFirst();
    }

    public Optional<TenantAdminProfile> update(
        StoreScope scope,
        String displayName,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
        return jdbc.query(
            """
            update tenants
            set display_name = ?,
                default_locale = ?,
                contact_phone = ?,
                address = ?,
                principal_name = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name, logo_media_asset_id,
                      created_at, updated_at
            """,
            (rs, rowNum) -> profile(rs),
            displayName,
            defaultLocale,
            contactPhone,
            address,
            principalName,
            scope.tenantId().value()
        ).stream().findFirst();
    }

    public boolean syncStoreShareContact(StoreScope scope, String contactPhone, String address) {
        int updated = jdbc.update(
            """
            update stores
            set share_contact_phone = ?,
                share_address = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and deleted_at is null
            """,
            contactPhone,
            address,
            scope.tenantId().value()
        );
        return updated > 0;
    }

    public Optional<TenantAdminProfile> updateLogoMediaAsset(StoreScope scope, UUID logoMediaAssetId) {
        return jdbc.query(
            """
            update tenants
            set logo_media_asset_id = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name, logo_media_asset_id,
                      created_at, updated_at
            """,
            (rs, rowNum) -> profile(rs),
            logoMediaAssetId,
            scope.tenantId().value()
        ).stream().findFirst();
    }

    private static TenantAdminProfile profile(ResultSet rs) throws SQLException {
        return new TenantAdminProfile(
            rs.getObject("id", UUID.class),
            rs.getString("tenant_code"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("default_locale"),
            rs.getString("contact_phone"),
            rs.getString("address"),
            rs.getString("principal_name"),
            rs.getObject("logo_media_asset_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
