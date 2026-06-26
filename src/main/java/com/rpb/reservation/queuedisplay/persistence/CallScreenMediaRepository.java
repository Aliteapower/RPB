package com.rpb.reservation.queuedisplay.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaAsset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

public interface CallScreenMediaRepository {
    CallScreenMediaAsset createAsset(
        String ownerScope,
        UUID tenantId,
        String mediaKind,
        String contentType,
        long byteSize,
        String originalFilename,
        String storageKey
    );

    Optional<CallScreenMediaAsset> findTenantAsset(UUID tenantId, UUID assetId);

    Optional<CallScreenMediaAsset> findPlatformAsset(UUID assetId);

    Optional<CallScreenMediaAsset> findQueueDisplayAsset(StoreScope scope, UUID assetId);
}

@Repository
class JdbcCallScreenMediaRepository implements CallScreenMediaRepository {
    private final JdbcTemplate jdbc;

    JdbcCallScreenMediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CallScreenMediaAsset createAsset(
        String ownerScope,
        UUID tenantId,
        String mediaKind,
        String contentType,
        long byteSize,
        String originalFilename,
        String storageKey
    ) {
        UUID assetId = jdbc.queryForObject(
            """
            insert into call_screen_media_assets (
                owner_scope, tenant_id, media_kind, content_type, byte_size, original_filename, storage_key, status
            )
            values (?, ?, ?, ?, ?, ?, ?, 'active')
            returning id
            """,
            UUID.class,
            ownerScope,
            tenantId,
            mediaKind,
            contentType,
            byteSize,
            originalFilename,
            storageKey
        );
        return findById(assetId).orElseThrow();
    }

    @Override
    public Optional<CallScreenMediaAsset> findTenantAsset(UUID tenantId, UUID assetId) {
        return jdbc.query(
            """
            select id, owner_scope, tenant_id, media_kind, content_type, byte_size,
                   original_filename, storage_key, version
            from call_screen_media_assets
            where id = ?
              and owner_scope = 'tenant'
              and tenant_id = ?
              and status = 'active'
              and deleted_at is null
            """,
            (rs, rowNum) -> asset(rs),
            assetId,
            tenantId
        ).stream().findFirst();
    }

    @Override
    public Optional<CallScreenMediaAsset> findPlatformAsset(UUID assetId) {
        return jdbc.query(
            """
            select id, owner_scope, tenant_id, media_kind, content_type, byte_size,
                   original_filename, storage_key, version
            from call_screen_media_assets
            where id = ?
              and owner_scope = 'platform'
              and tenant_id is null
              and status = 'active'
              and deleted_at is null
            """,
            (rs, rowNum) -> asset(rs),
            assetId
        ).stream().findFirst();
    }

    @Override
    public Optional<CallScreenMediaAsset> findQueueDisplayAsset(StoreScope scope, UUID assetId) {
        return jdbc.query(
            """
            select distinct asset.id,
                   asset.owner_scope,
                   asset.tenant_id,
                   asset.media_kind,
                   asset.content_type,
                   asset.byte_size,
                   asset.original_filename,
                   asset.storage_key,
                   asset.version
            from store_call_screen_settings setting
            join tenant_call_screen_ad_sets ad_set on ad_set.id = setting.active_ad_set_id
             and ad_set.tenant_id = setting.tenant_id
             and ad_set.status = 'active'
             and ad_set.ad_type in ('image', 'media')
             and ad_set.deleted_at is null
            join tenant_call_screen_media_slides slide on slide.ad_set_id = ad_set.id
             and slide.tenant_id = ad_set.tenant_id
             and slide.status = 'active'
             and slide.deleted_at is null
            join call_screen_media_assets asset on asset.id = slide.media_asset_id
             and asset.owner_scope = 'tenant'
             and asset.tenant_id = slide.tenant_id
             and asset.status = 'active'
             and asset.deleted_at is null
            where setting.tenant_id = ?
              and setting.store_id = ?
              and setting.status = 'active'
              and setting.deleted_at is null
              and asset.id = ?
            """,
            (rs, rowNum) -> asset(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            assetId
        ).stream().findFirst();
    }

    private Optional<CallScreenMediaAsset> findById(UUID assetId) {
        return jdbc.query(
            """
            select id, owner_scope, tenant_id, media_kind, content_type, byte_size,
                   original_filename, storage_key, version
            from call_screen_media_assets
            where id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> asset(rs),
            assetId
        ).stream().findFirst();
    }

    private static CallScreenMediaAsset asset(ResultSet rs) throws SQLException {
        return new CallScreenMediaAsset(
            rs.getObject("id", UUID.class),
            rs.getString("owner_scope"),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("media_kind"),
            rs.getString("content_type"),
            rs.getLong("byte_size"),
            rs.getString("original_filename"),
            rs.getString("storage_key"),
            null,
            rs.getInt("version")
        );
    }
}
