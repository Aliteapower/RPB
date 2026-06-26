package com.rpb.reservation.queuedisplay.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.CallScreenAdSet;
import com.rpb.reservation.queuedisplay.application.CallScreenSetting;
import com.rpb.reservation.queuedisplay.application.CallScreenTextSlide;
import com.rpb.reservation.queuedisplay.application.CallScreenTextSlideCommand;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

public interface CallScreenAdminRepository {
    Optional<CallScreenSetting> findSetting(StoreScope scope);

    CallScreenSetting upsertDefaultSetting(StoreScope scope, UUID adSetId);

    CallScreenSetting updateSetting(
        StoreScope scope,
        UUID activeAdSetId,
        String adMode,
        String status,
        int slideDurationSeconds,
        int statePollSeconds,
        boolean showWaitingPreview
    );

    Optional<CallScreenAdSet> findDefaultTextSet(UUID tenantId, String seedKey);

    Optional<CallScreenAdSet> findAdSet(UUID tenantId, UUID adSetId);

    List<CallScreenAdSet> listAdSets(UUID tenantId);

    CallScreenAdSet cloneSeedTextSet(UUID tenantId, String seedKey);

    CallScreenAdSet createTextAdSet(UUID tenantId, String name, String status, List<CallScreenTextSlideCommand> slides);

    CallScreenAdSet updateTextAdSet(UUID tenantId, UUID adSetId, String name, String status, List<CallScreenTextSlideCommand> slides);
}

@Repository
class JdbcCallScreenAdminRepository implements CallScreenAdminRepository {
    private final JdbcTemplate jdbc;

    JdbcCallScreenAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CallScreenSetting> findSetting(StoreScope scope) {
        return jdbc.query(
            """
            select active_ad_set_id, ad_mode, status, slide_duration_seconds, state_poll_seconds,
                   show_waiting_preview, version
            from store_call_screen_settings
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> setting(rs),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    @Override
    public CallScreenSetting upsertDefaultSetting(StoreScope scope, UUID adSetId) {
        jdbc.update(
            """
            insert into store_call_screen_settings (
                tenant_id, store_id, active_ad_set_id, ad_mode, status,
                slide_duration_seconds, state_poll_seconds, show_waiting_preview
            )
            values (?, ?, ?, 'text', 'active', 5, 3, true)
            on conflict (tenant_id, store_id) where deleted_at is null do update
            set active_ad_set_id = coalesce(store_call_screen_settings.active_ad_set_id, excluded.active_ad_set_id),
                ad_mode = 'text',
                updated_at = now()
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            adSetId
        );
        return findSetting(scope).orElseThrow();
    }

    @Override
    public CallScreenSetting updateSetting(
        StoreScope scope,
        UUID activeAdSetId,
        String adMode,
        String status,
        int slideDurationSeconds,
        int statePollSeconds,
        boolean showWaitingPreview
    ) {
        jdbc.update(
            """
            update store_call_screen_settings
            set active_ad_set_id = ?,
                ad_mode = ?,
                status = ?,
                slide_duration_seconds = ?,
                state_poll_seconds = ?,
                show_waiting_preview = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            activeAdSetId,
            adMode,
            status,
            slideDurationSeconds,
            statePollSeconds,
            showWaitingPreview,
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return findSetting(scope).orElseThrow();
    }

    @Override
    public Optional<CallScreenAdSet> findDefaultTextSet(UUID tenantId, String seedKey) {
        return jdbc.query(
            """
            select ad_set.id, ad_set.name, ad_set.ad_type, ad_set.status, ad_set.version
            from tenant_call_screen_ad_sets ad_set
            join platform_call_screen_ad_seed_sets seed on seed.id = ad_set.source_seed_set_id
            where ad_set.tenant_id = ?
              and seed.seed_key = ?
              and ad_set.ad_type = 'text'
              and ad_set.deleted_at is null
            order by ad_set.created_at asc
            limit 1
            """,
            (rs, rowNum) -> adSet(rs, tenantId),
            tenantId,
            seedKey
        ).stream().findFirst();
    }

    @Override
    public Optional<CallScreenAdSet> findAdSet(UUID tenantId, UUID adSetId) {
        return jdbc.query(
            """
            select id, name, ad_type, status, version
            from tenant_call_screen_ad_sets
            where tenant_id = ?
              and id = ?
              and ad_type = 'text'
              and deleted_at is null
            """,
            (rs, rowNum) -> adSet(rs, tenantId),
            tenantId,
            adSetId
        ).stream().findFirst();
    }

    @Override
    public List<CallScreenAdSet> listAdSets(UUID tenantId) {
        return jdbc.query(
            """
            select id, name, ad_type, status, version
            from tenant_call_screen_ad_sets
            where tenant_id = ?
              and deleted_at is null
              and ad_type = 'text'
            order by created_at asc, name asc
            """,
            (rs, rowNum) -> adSet(rs, tenantId),
            tenantId
        );
    }

    @Override
    public CallScreenAdSet cloneSeedTextSet(UUID tenantId, String seedKey) {
        UUID seedSetId = jdbc.queryForObject(
            """
            select id
            from platform_call_screen_ad_seed_sets
            where seed_key = ?
              and status = 'active'
              and ad_type = 'text'
              and deleted_at is null
            """,
            UUID.class,
            seedKey
        );
        UUID adSetId = jdbc.queryForObject(
            """
            insert into tenant_call_screen_ad_sets (tenant_id, source_seed_set_id, name, ad_type, status)
            values (?, ?, '默认叫号屏文案', 'text', 'active')
            returning id
            """,
            UUID.class,
            tenantId,
            seedSetId
        );
        jdbc.update(
            """
            insert into tenant_call_screen_text_slides (
                tenant_id, ad_set_id, source_seed_slide_id, title, subtitle, tagline, sort_order, status
            )
            select ?, ?, slide.id, slide.title, slide.subtitle, slide.tagline, slide.sort_order, slide.status
            from platform_call_screen_ad_seed_slides slide
            where slide.seed_set_id = ?
              and slide.status = 'active'
              and slide.deleted_at is null
            order by slide.sort_order asc
            """,
            tenantId,
            adSetId,
            seedSetId
        );
        return findAdSet(tenantId, adSetId).orElseThrow();
    }

    @Override
    public CallScreenAdSet createTextAdSet(UUID tenantId, String name, String status, List<CallScreenTextSlideCommand> slides) {
        UUID adSetId = jdbc.queryForObject(
            """
            insert into tenant_call_screen_ad_sets (tenant_id, name, ad_type, status)
            values (?, ?, 'text', ?)
            returning id
            """,
            UUID.class,
            tenantId,
            name,
            status
        );
        replaceSlides(tenantId, adSetId, slides);
        return findAdSet(tenantId, adSetId).orElseThrow();
    }

    @Override
    public CallScreenAdSet updateTextAdSet(UUID tenantId, UUID adSetId, String name, String status, List<CallScreenTextSlideCommand> slides) {
        jdbc.update(
            """
            update tenant_call_screen_ad_sets
            set name = ?,
                status = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and ad_type = 'text'
              and deleted_at is null
            """,
            name,
            status,
            tenantId,
            adSetId
        );
        replaceSlides(tenantId, adSetId, slides);
        return findAdSet(tenantId, adSetId).orElseThrow();
    }

    private void replaceSlides(UUID tenantId, UUID adSetId, List<CallScreenTextSlideCommand> slides) {
        jdbc.update(
            """
            update tenant_call_screen_text_slides
            set deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and ad_set_id = ?
              and deleted_at is null
            """,
            tenantId,
            adSetId
        );
        for (CallScreenTextSlideCommand slide : slides) {
            jdbc.update(
                """
                insert into tenant_call_screen_text_slides (
                    tenant_id, ad_set_id, title, subtitle, tagline, sort_order, status
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                adSetId,
                slide.title(),
                slide.subtitle(),
                slide.tagline(),
                slide.sortOrder(),
                slide.status()
            );
        }
    }

    private CallScreenAdSet adSet(ResultSet rs, UUID tenantId) throws SQLException {
        UUID adSetId = rs.getObject("id", UUID.class);
        return new CallScreenAdSet(
            adSetId,
            rs.getString("name"),
            rs.getString("ad_type"),
            rs.getString("status"),
            textSlides(tenantId, adSetId),
            rs.getInt("version")
        );
    }

    private List<CallScreenTextSlide> textSlides(UUID tenantId, UUID adSetId) {
        return jdbc.query(
            """
            select id, title, subtitle, tagline, sort_order, status, version
            from tenant_call_screen_text_slides
            where tenant_id = ?
              and ad_set_id = ?
              and deleted_at is null
            order by sort_order asc, created_at asc
            """,
            (rs, rowNum) -> new CallScreenTextSlide(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("tagline"),
                rs.getInt("sort_order"),
                rs.getString("status"),
                rs.getInt("version")
            ),
            tenantId,
            adSetId
        );
    }

    private static CallScreenSetting setting(ResultSet rs) throws SQLException {
        return new CallScreenSetting(
            rs.getObject("active_ad_set_id", UUID.class),
            rs.getString("ad_mode"),
            rs.getString("status"),
            rs.getInt("slide_duration_seconds"),
            rs.getInt("state_poll_seconds"),
            rs.getBoolean("show_waiting_preview"),
            rs.getInt("version")
        );
    }
}
