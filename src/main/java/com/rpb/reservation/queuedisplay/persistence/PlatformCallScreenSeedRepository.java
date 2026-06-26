package com.rpb.reservation.queuedisplay.persistence;

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

    PlatformCallScreenSeedSet updateTextSeed(
        UUID seedSetId,
        String displayName,
        String status,
        List<PlatformCallScreenSeedSlideCommand> slides
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
              and ad_type = 'text'
            """,
            (rs, rowNum) -> seedSet(rs),
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

    private Optional<PlatformCallScreenSeedSet> findById(UUID seedSetId) {
        return jdbc.query(
            """
            select id, seed_key, display_name, ad_type, status, version
            from platform_call_screen_ad_seed_sets
            where id = ?
              and deleted_at is null
              and ad_type = 'text'
            """,
            (rs, rowNum) -> seedSet(rs),
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

}
