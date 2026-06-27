package com.rpb.reservation.platform.persistence;

import com.rpb.reservation.platform.application.PlatformProfile;
import com.rpb.reservation.platform.application.PlatformSocialLink;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformProfileRepository {
    private final JdbcTemplate jdbc;

    public PlatformProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PlatformProfile> findProfile() {
        return jdbc.query(
            """
            select platform_name, uen, address, phone, email, website,
                   logo_media_asset_id, created_at, updated_at, version
            from platform_profile
            where id = true
            """,
            (rs, rowNum) -> profile(rs, List.of())
        ).stream().findFirst().map(this::withSocialLinks);
    }

    public PlatformProfile upsertProfile(
        String platformName,
        String uen,
        String address,
        String phone,
        String email,
        String website
    ) {
        List<PlatformProfile> updated = jdbc.query(
            """
            update platform_profile
            set platform_name = ?,
                uen = ?,
                address = ?,
                phone = ?,
                email = ?,
                website = ?,
                updated_at = now(),
                version = version + 1
            where id = true
            returning platform_name, uen, address, phone, email, website,
                      logo_media_asset_id, created_at, updated_at, version
            """,
            (rs, rowNum) -> profile(rs, List.of()),
            platformName,
            uen,
            address,
            phone,
            email,
            website
        );
        if (!updated.isEmpty()) {
            return withSocialLinks(updated.get(0));
        }

        jdbc.update(
            """
            insert into platform_profile (id, platform_name, uen, address, phone, email, website)
            values (true, ?, ?, ?, ?, ?, ?)
            """,
            platformName,
            uen,
            address,
            phone,
            email,
            website
        );
        return findProfile().orElseThrow();
    }

    public PlatformProfile updateProfileLogo(UUID logoMediaAssetId) {
        return jdbc.query(
            """
            update platform_profile
            set logo_media_asset_id = ?,
                updated_at = now(),
                version = version + 1
            where id = true
            returning platform_name, uen, address, phone, email, website,
                      logo_media_asset_id, created_at, updated_at, version
            """,
            (rs, rowNum) -> profile(rs, List.of()),
            logoMediaAssetId
        ).stream().findFirst().map(this::withSocialLinks).orElseThrow();
    }

    public PlatformSocialLink createSocialLink(
        UUID linkId,
        String displayName,
        String url,
        int sortOrder,
        String status
    ) {
        return jdbc.queryForObject(
            """
            insert into platform_social_links (id, display_name, url, sort_order, status)
            values (?, ?, ?, ?, ?)
            returning id, display_name, url, logo_media_asset_id, sort_order, status,
                      created_at, updated_at, version
            """,
            (rs, rowNum) -> socialLink(rs),
            linkId,
            displayName,
            url,
            sortOrder,
            status
        );
    }

    public Optional<PlatformSocialLink> updateSocialLink(
        UUID linkId,
        String displayName,
        String url,
        int sortOrder,
        String status
    ) {
        return jdbc.query(
            """
            update platform_social_links
            set display_name = ?,
                url = ?,
                sort_order = ?,
                status = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            returning id, display_name, url, logo_media_asset_id, sort_order, status,
                      created_at, updated_at, version
            """,
            (rs, rowNum) -> socialLink(rs),
            displayName,
            url,
            sortOrder,
            status,
            linkId
        ).stream().findFirst();
    }

    public Optional<PlatformSocialLink> updateSocialLinkLogo(UUID linkId, UUID logoMediaAssetId) {
        return jdbc.query(
            """
            update platform_social_links
            set logo_media_asset_id = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            returning id, display_name, url, logo_media_asset_id, sort_order, status,
                      created_at, updated_at, version
            """,
            (rs, rowNum) -> socialLink(rs),
            logoMediaAssetId,
            linkId
        ).stream().findFirst();
    }

    public Optional<PlatformSocialLink> softDeleteSocialLink(UUID linkId) {
        return jdbc.query(
            """
            update platform_social_links
            set deleted_at = coalesce(deleted_at, now()),
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            returning id, display_name, url, logo_media_asset_id, sort_order, status,
                      created_at, updated_at, version
            """,
            (rs, rowNum) -> socialLink(rs),
            linkId
        ).stream().findFirst();
    }

    public Optional<PlatformSocialLink> findSocialLink(UUID linkId) {
        return jdbc.query(
            """
            select id, display_name, url, logo_media_asset_id, sort_order, status,
                   created_at, updated_at, version
            from platform_social_links
            where id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> socialLink(rs),
            linkId
        ).stream().findFirst();
    }

    public int nextSocialLinkSortOrder() {
        Integer value = jdbc.queryForObject(
            """
            select coalesce(max(sort_order), 0) + 1
            from platform_social_links
            where deleted_at is null
            """,
            Integer.class
        );
        return value == null ? 1 : value;
    }

    private List<PlatformSocialLink> listSocialLinks() {
        return jdbc.query(
            """
            select id, display_name, url, logo_media_asset_id, sort_order, status,
                   created_at, updated_at, version
            from platform_social_links
            where deleted_at is null
            order by sort_order asc, created_at asc
            """,
            (rs, rowNum) -> socialLink(rs)
        );
    }

    private PlatformProfile withSocialLinks(PlatformProfile profile) {
        return new PlatformProfile(
            profile.platformName(),
            profile.uen(),
            profile.address(),
            profile.phone(),
            profile.email(),
            profile.website(),
            profile.logoMediaAssetId(),
            profile.createdAt(),
            profile.updatedAt(),
            profile.version(),
            listSocialLinks()
        );
    }

    private static PlatformProfile profile(ResultSet rs, List<PlatformSocialLink> socialLinks) throws SQLException {
        return new PlatformProfile(
            rs.getString("platform_name"),
            rs.getString("uen"),
            rs.getString("address"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("website"),
            rs.getObject("logo_media_asset_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("version"),
            socialLinks
        );
    }

    private static PlatformSocialLink socialLink(ResultSet rs) throws SQLException {
        return new PlatformSocialLink(
            rs.getObject("id", UUID.class),
            rs.getString("display_name"),
            rs.getString("url"),
            rs.getObject("logo_media_asset_id", UUID.class),
            rs.getInt("sort_order"),
            rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("version")
        );
    }
}
