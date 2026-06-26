package com.rpb.reservation.queuedisplay.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.QueueDisplayAdSlide;
import com.rpb.reservation.queuedisplay.application.QueueDisplayAds;
import com.rpb.reservation.queuedisplay.application.QueueDisplayCurrentCall;
import com.rpb.reservation.queuedisplay.application.QueueDisplayWaitingPreviewItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

public interface QueueDisplayRepository {
    Optional<QueueDisplayCurrentCall> findCurrentCall(StoreScope scope, LocalDate businessDate, Instant now);

    List<QueueDisplayWaitingPreviewItem> findWaitingPreview(StoreScope scope, LocalDate businessDate, int limit);

    int countWaiting(StoreScope scope, LocalDate businessDate);

    QueueDisplayAds findActiveAds(StoreScope scope);
}

@Repository
class JdbcQueueDisplayRepository implements QueueDisplayRepository {
    private final JdbcTemplate jdbc;

    JdbcQueueDisplayRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<QueueDisplayCurrentCall> findCurrentCall(StoreScope scope, LocalDate businessDate, Instant now) {
        return jdbc.query(
            """
            select ticket.id,
                   ticket.ticket_number,
                   queue_group.group_code,
                   coalesce(ticket_customer.display_name, reservation_customer.display_name) as customer_name,
                   ticket.party_size,
                   ticket.called_at,
                   ticket.expires_at
            from queue_tickets ticket
            join queue_groups queue_group on queue_group.id = ticket.queue_group_id
             and queue_group.tenant_id = ticket.tenant_id
             and queue_group.store_id = ticket.store_id
             and queue_group.deleted_at is null
            left join reservations reservation on reservation.id = ticket.reservation_id
             and reservation.tenant_id = ticket.tenant_id
             and reservation.store_id = ticket.store_id
             and reservation.deleted_at is null
            left join customers ticket_customer on ticket_customer.id = ticket.customer_id
             and ticket_customer.tenant_id = ticket.tenant_id
             and ticket_customer.deleted_at is null
            left join customers reservation_customer on reservation_customer.id = reservation.customer_id
             and reservation_customer.tenant_id = ticket.tenant_id
             and reservation_customer.deleted_at is null
            where ticket.tenant_id = ?
              and ticket.store_id = ?
              and ticket.business_date = ?
              and ticket.status = 'called'
              and ticket.called_at is not null
              and ticket.expires_at >= ?
              and ticket.deleted_at is null
            order by ticket.called_at desc, ticket.ticket_number desc
            limit 1
            """,
            (rs, rowNum) -> currentCall(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate,
            Timestamp.from(now)
        ).stream().findFirst();
    }

    @Override
    public List<QueueDisplayWaitingPreviewItem> findWaitingPreview(StoreScope scope, LocalDate businessDate, int limit) {
        return jdbc.query(
            """
            select ticket.ticket_number,
                   queue_group.group_code,
                   coalesce(ticket_customer.display_name, reservation_customer.display_name) as customer_name,
                   ticket.party_size
            from queue_tickets ticket
            join queue_groups queue_group on queue_group.id = ticket.queue_group_id
             and queue_group.tenant_id = ticket.tenant_id
             and queue_group.store_id = ticket.store_id
             and queue_group.deleted_at is null
            left join reservations reservation on reservation.id = ticket.reservation_id
             and reservation.tenant_id = ticket.tenant_id
             and reservation.store_id = ticket.store_id
             and reservation.deleted_at is null
            left join customers ticket_customer on ticket_customer.id = ticket.customer_id
             and ticket_customer.tenant_id = ticket.tenant_id
             and ticket_customer.deleted_at is null
            left join customers reservation_customer on reservation_customer.id = reservation.customer_id
             and reservation_customer.tenant_id = ticket.tenant_id
             and reservation_customer.deleted_at is null
            where ticket.tenant_id = ?
              and ticket.store_id = ?
              and ticket.business_date = ?
              and ticket.status in ('waiting', 'rejoined')
              and ticket.deleted_at is null
            order by ticket.queue_position asc nulls last, ticket.created_at asc, ticket.ticket_number asc
            limit ?
            """,
            (rs, rowNum) -> waitingItem(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate,
            limit
        );
    }

    @Override
    public int countWaiting(StoreScope scope, LocalDate businessDate) {
        return jdbc.queryForObject(
            """
            select count(*)
            from queue_tickets
            where tenant_id = ?
              and store_id = ?
              and business_date = ?
              and status in ('waiting', 'rejoined')
              and deleted_at is null
            """,
            Integer.class,
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate
        );
    }

    @Override
    public QueueDisplayAds findActiveAds(StoreScope scope) {
        List<QueueDisplayAds> configured = jdbc.query(
            """
            select setting.ad_mode,
                   setting.slide_duration_seconds,
                   setting.state_poll_seconds,
                   ad_set.id as ad_set_id,
                   ad_set.ad_type
            from store_call_screen_settings setting
            join tenant_call_screen_ad_sets ad_set on ad_set.id = setting.active_ad_set_id
             and ad_set.tenant_id = setting.tenant_id
             and ad_set.status = 'active'
             and ad_set.deleted_at is null
            where setting.tenant_id = ?
              and setting.store_id = ?
              and setting.status = 'active'
              and setting.deleted_at is null
            limit 1
            """,
            (rs, rowNum) -> {
                UUID adSetId = rs.getObject("ad_set_id", UUID.class);
                String adType = normalizeAdType(rs.getString("ad_type"));
                List<QueueDisplayAdSlide> slides = "media".equals(adType)
                    ? findTenantMediaSlides(scope, adSetId)
                    : findTenantTextSlides(scope, adSetId);
                return new QueueDisplayAds(
                    adType,
                    rs.getInt("slide_duration_seconds"),
                    rs.getInt("state_poll_seconds"),
                    slides
                );
            },
            scope.tenantId().value(),
            scope.storeId().value()
        );
        if (!configured.isEmpty() && !configured.get(0).slides().isEmpty()) {
            return configured.get(0);
        }
        return new QueueDisplayAds("text", 5, 3, findPlatformSeedSlides());
    }

    private List<QueueDisplayAdSlide> findTenantTextSlides(StoreScope scope, UUID adSetId) {
        return jdbc.query(
            """
            select id, title, subtitle, tagline
            from tenant_call_screen_text_slides
            where tenant_id = ?
              and ad_set_id = ?
              and status = 'active'
              and deleted_at is null
            order by sort_order asc
            """,
            (rs, rowNum) -> new QueueDisplayAdSlide(rs.getString("id"), rs.getString("title"), rs.getString("subtitle"), rs.getString("tagline")),
            scope.tenantId().value(),
            adSetId
        );
    }

    private List<QueueDisplayAdSlide> findTenantMediaSlides(StoreScope scope, UUID adSetId) {
        return jdbc.query(
            """
            select slide.id,
                   slide.media_asset_id,
                   slide.media_kind,
                   slide.title,
                   slide.alt_text
            from tenant_call_screen_media_slides slide
            join call_screen_media_assets asset on asset.id = slide.media_asset_id
             and asset.owner_scope = 'tenant'
             and asset.tenant_id = slide.tenant_id
             and asset.media_kind = slide.media_kind
             and asset.status = 'active'
             and asset.deleted_at is null
            where slide.tenant_id = ?
              and slide.ad_set_id = ?
              and slide.status = 'active'
              and slide.deleted_at is null
            order by slide.sort_order asc
            """,
            (rs, rowNum) -> QueueDisplayAdSlide.media(
                rs.getString("id"),
                rs.getString("media_kind"),
                "/api/v1/stores/" + scope.storeId().value() + "/queue-display/media/" + rs.getString("media_asset_id"),
                rs.getString("alt_text"),
                rs.getString("title")
            ),
            scope.tenantId().value(),
            adSetId
        );
    }

    private List<QueueDisplayAdSlide> findPlatformSeedSlides() {
        return jdbc.query(
            """
            select slide.id, slide.title, slide.subtitle, slide.tagline
            from platform_call_screen_ad_seed_sets seed
            join platform_call_screen_ad_seed_slides slide on slide.seed_set_id = seed.id
             and slide.status = 'active'
             and slide.deleted_at is null
            where seed.seed_key = 'restaurant_default'
              and seed.status = 'active'
              and seed.ad_type = 'text'
              and seed.deleted_at is null
            order by slide.sort_order asc
            """,
            (rs, rowNum) -> new QueueDisplayAdSlide(rs.getString("id"), rs.getString("title"), rs.getString("subtitle"), rs.getString("tagline"))
        );
    }

    private static String normalizeAdType(String adType) {
        if ("image".equals(adType) || "media".equals(adType)) {
            return "media";
        }
        return "text";
    }

    private static QueueDisplayCurrentCall currentCall(ResultSet rs) throws SQLException {
        return new QueueDisplayCurrentCall(
            rs.getObject("id", UUID.class),
            rs.getInt("ticket_number"),
            rs.getString("group_code"),
            rs.getString("customer_name"),
            rs.getInt("party_size"),
            rs.getTimestamp("called_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
        );
    }

    private static QueueDisplayWaitingPreviewItem waitingItem(ResultSet rs) throws SQLException {
        return new QueueDisplayWaitingPreviewItem(
            rs.getInt("ticket_number"),
            rs.getString("group_code"),
            rs.getString("customer_name"),
            rs.getInt("party_size")
        );
    }
}
