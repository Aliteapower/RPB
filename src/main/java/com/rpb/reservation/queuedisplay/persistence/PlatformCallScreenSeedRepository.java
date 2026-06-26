package com.rpb.reservation.queuedisplay.persistence;

import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedSet;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedSlide;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedSlideCommand;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSet;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSlide;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSlideCommand;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

public interface PlatformCallScreenSeedRepository {
    Optional<PlatformCallScreenSeedSet> findBySeedKey(String seedKey);

    Optional<PlatformCallScreenMediaSeedSet> findMediaBySeedKey(String seedKey);

    PlatformCallScreenSeedSet updateTextSeed(
        UUID seedSetId,
        String displayName,
        String status,
        List<PlatformCallScreenSeedSlideCommand> slides
    );

    PlatformCallScreenMediaSeedSet updateMediaSeed(
        UUID seedSetId,
        String displayName,
        String status,
        List<PlatformCallScreenMediaSeedSlideCommand> slides
    );
}

@Repository
class JdbcPlatformCallScreenSeedRepository implements PlatformCallScreenSeedRepository {
    private final JdbcTemplate jdbc;

    JdbcPlatformCallScreenSeedRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlatformCallScreenSeedSet> findBySeedKey(String seedKey) {
        return jdbc.query(
            """
            select id, seed_key, display_name, ad_type, status, version
            from platform_call_screen_ad_seed_sets
            where seed_key = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> seedSet(rs),
            seedKey
        ).stream().findFirst();
    }

    @Override
    public Optional<PlatformCallScreenMediaSeedSet> findMediaBySeedKey(String seedKey) {
        return jdbc.query(
            """
            select id, seed_key, display_name, ad_type, status, version
            from platform_call_screen_ad_seed_sets
            where seed_key = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> mediaSeedSet(rs),
            seedKey
        ).stream().findFirst();
    }

    @Override
    public PlatformCallScreenSeedSet updateTextSeed(
        UUID seedSetId,
        String displayName,
        String status,
        List<PlatformCallScreenSeedSlideCommand> slides
    ) {
        jdbc.update(
            """
            update platform_call_screen_ad_seed_sets
            set display_name = ?,
                status = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and ad_type = 'text'
              and deleted_at is null
            """,
            displayName,
            status,
            seedSetId
        );
        jdbc.update(
            """
            update platform_call_screen_ad_seed_slides
            set deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where seed_set_id = ?
              and deleted_at is null
            """,
            seedSetId
        );
        for (PlatformCallScreenSeedSlideCommand slide : slides) {
            jdbc.update(
                """
                insert into platform_call_screen_ad_seed_slides (
                    seed_set_id, title, subtitle, tagline, sort_order, status
                )
                values (?, ?, ?, ?, ?, ?)
                """,
                seedSetId,
                slide.title(),
                slide.subtitle(),
                slide.tagline(),
                slide.sortOrder(),
                slide.status()
            );
        }
        return findById(seedSetId).orElseThrow();
    }

    @Override
    public PlatformCallScreenMediaSeedSet updateMediaSeed(
        UUID seedSetId,
        String displayName,
        String status,
        List<PlatformCallScreenMediaSeedSlideCommand> slides
    ) {
        jdbc.update(
            """
            update platform_call_screen_ad_seed_sets
            set display_name = ?,
                status = ?,
                ad_type = 'media',
                updated_at = now(),
                version = version + 1
            where id = ?
              and ad_type in ('image', 'media')
              and deleted_at is null
            """,
            displayName,
            status,
            seedSetId
        );
        jdbc.update(
            """
            update platform_call_screen_media_seed_slides
            set deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where seed_set_id = ?
              and deleted_at is null
            """,
            seedSetId
        );
        for (PlatformCallScreenMediaSeedSlideCommand slide : slides) {
            jdbc.update(
                """
                insert into platform_call_screen_media_seed_slides (
                    seed_set_id, media_asset_id, media_kind, title, alt_text, sort_order, status
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                seedSetId,
                slide.mediaAssetId(),
                slide.mediaKind(),
                slide.title(),
                slide.altText(),
                slide.sortOrder(),
                slide.status()
            );
        }
        return findMediaById(seedSetId).orElseThrow();
    }

    private Optional<PlatformCallScreenSeedSet> findById(UUID seedSetId) {
        return jdbc.query(
            """
            select id, seed_key, display_name, ad_type, status, version
            from platform_call_screen_ad_seed_sets
            where id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> seedSet(rs),
            seedSetId
        ).stream().findFirst();
    }

    private Optional<PlatformCallScreenMediaSeedSet> findMediaById(UUID seedSetId) {
        return jdbc.query(
            """
            select id, seed_key, display_name, ad_type, status, version
            from platform_call_screen_ad_seed_sets
            where id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> mediaSeedSet(rs),
            seedSetId
        ).stream().findFirst();
    }

    private PlatformCallScreenSeedSet seedSet(ResultSet rs) throws SQLException {
        UUID seedSetId = rs.getObject("id", UUID.class);
        return new PlatformCallScreenSeedSet(
            seedSetId,
            rs.getString("seed_key"),
            rs.getString("display_name"),
            rs.getString("ad_type"),
            rs.getString("status"),
            slides(seedSetId),
            rs.getInt("version")
        );
    }

    private PlatformCallScreenMediaSeedSet mediaSeedSet(ResultSet rs) throws SQLException {
        UUID seedSetId = rs.getObject("id", UUID.class);
        return new PlatformCallScreenMediaSeedSet(
            seedSetId,
            rs.getString("seed_key"),
            rs.getString("display_name"),
            rs.getString("ad_type"),
            rs.getString("status"),
            mediaSlides(seedSetId),
            rs.getInt("version")
        );
    }

    private List<PlatformCallScreenSeedSlide> slides(UUID seedSetId) {
        return jdbc.query(
            """
            select id, title, subtitle, tagline, sort_order, status, version
            from platform_call_screen_ad_seed_slides
            where seed_set_id = ?
              and deleted_at is null
            order by sort_order asc, created_at asc
            """,
            (rs, rowNum) -> new PlatformCallScreenSeedSlide(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("tagline"),
                rs.getInt("sort_order"),
                rs.getString("status"),
                rs.getInt("version")
            ),
            seedSetId
        );
    }

    private List<PlatformCallScreenMediaSeedSlide> mediaSlides(UUID seedSetId) {
        return jdbc.query(
            """
            select slide.id,
                   slide.media_asset_id,
                   slide.media_kind,
                   slide.title,
                   slide.alt_text,
                   slide.sort_order,
                   slide.status,
                   slide.version
            from platform_call_screen_media_seed_slides slide
            where slide.seed_set_id = ?
              and slide.deleted_at is null
            order by slide.sort_order asc, slide.created_at asc
            """,
            (rs, rowNum) -> {
                UUID assetId = rs.getObject("media_asset_id", UUID.class);
                return new PlatformCallScreenMediaSeedSlide(
                    rs.getObject("id", UUID.class),
                    assetId,
                    rs.getString("media_kind"),
                    CallScreenMediaService.platformMediaUrl(assetId),
                    rs.getString("title"),
                    rs.getString("alt_text"),
                    rs.getInt("sort_order"),
                    rs.getString("status"),
                    rs.getInt("version")
                );
            },
            seedSetId
        );
    }
}
