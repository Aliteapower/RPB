package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.ProductSubscriptionHistoryEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductSubscriptionHistoryRepository implements ProductSubscriptionHistoryRepository {
    private final JdbcTemplate jdbc;
    private final ProductSubscriptionHistorySnapshotReader snapshots;
    public JdbcProductSubscriptionHistoryRepository(JdbcTemplate jdbc, ProductSubscriptionHistorySnapshotReader snapshots) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
    }

    @Override public List<ProductSubscriptionHistoryEvent> list(UUID tenantId, UUID subscriptionId) {
        return jdbc.query("""
            select id, event_type, billing_cycle, status, period_start, period_end,
                   amount, currency, payment_note, created_at, event_payload
            from tenant_product_subscription_events
            where tenant_id = ? and subscription_id = ?
            order by created_at desc, id desc
            """, (rs, row) -> {
                var snapshot = snapshots.read(rs.getString("event_payload"));
                return new ProductSubscriptionHistoryEvent(rs.getObject("id", UUID.class), rs.getString("event_type"),
                    rs.getString("billing_cycle"), rs.getString("status"), rs.getObject("period_start", OffsetDateTime.class),
                    rs.getObject("period_end", OffsetDateTime.class), rs.getBigDecimal("amount"), rs.getString("currency"),
                    rs.getString("payment_note"), rs.getObject("created_at", OffsetDateTime.class), snapshot.available(), snapshot.stores());
            }, tenantId, subscriptionId);
    }
}
