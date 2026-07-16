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
            select tenant.id,
                   tenant.tenant_code,
                   store.id as store_id,
                   store.store_code,
                   store.display_name,
                   store.status,
                   store.locale as default_locale,
                   store.share_contact_phone as contact_phone,
                   store.share_address as address,
                   coalesce(entity.principal_name, tenant.principal_name) as principal_name,
                   tenant.logo_media_asset_id,
                   store.created_at,
                   store.updated_at
            from tenants tenant
            join stores store
              on store.tenant_id = tenant.id
             and store.id = ?
             and store.deleted_at is null
            left join operating_entities entity
              on entity.id = store.operating_entity_id
             and entity.tenant_id = store.tenant_id
             and entity.deleted_at is null
            where tenant.id = ?
              and tenant.deleted_at is null
            """,
            (rs, rowNum) -> profile(rs),
            scope.storeId().value(),
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
        jdbc.update(
            """
            update operating_entities
            set principal_name = ?,
                updated_at = now(),
                version = version + 1
            where id = (
                select store.operating_entity_id
                from stores store
                where store.id = ?
                  and store.tenant_id = ?
                  and store.deleted_at is null
            )
              and tenant_id = ?
              and deleted_at is null
            """,
            principalName,
            scope.storeId().value(),
            scope.tenantId().value(),
            scope.tenantId().value()
        );
        int updated = jdbc.update(
            """
            update stores
            set display_name = ?,
                locale = ?,
                share_contact_phone = ?,
                share_address = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and deleted_at is null
            """,
            displayName,
            defaultLocale,
            contactPhone,
            address,
            scope.storeId().value(),
            scope.tenantId().value()
        );
        return updated > 0 ? find(scope) : Optional.empty();
    }

    public Optional<TenantAdminProfile> updateLogoMediaAsset(StoreScope scope, UUID logoMediaAssetId) {
        int updated = jdbc.update(
            """
            update tenants
            set logo_media_asset_id = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            """,
            logoMediaAssetId,
            scope.tenantId().value()
        );
        return updated > 0 ? find(scope) : Optional.empty();
    }

    private static TenantAdminProfile profile(ResultSet rs) throws SQLException {
        return new TenantAdminProfile(
            rs.getObject("id", UUID.class),
            rs.getString("tenant_code"),
            rs.getObject("store_id", UUID.class),
            rs.getString("store_code"),
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
