package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.ProductSubscriptionEventDraft;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductSubscriptionEventRepository implements ProductSubscriptionEventRepository {
    private final JdbcTemplate jdbc;

    public JdbcProductSubscriptionEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findSubscriptionIdByIdempotencyKey(
        UUID tenantId,
        String appKey,
        String eventType,
        String idempotencyKey
    ) {
        return jdbc.query(
            """
            select subscription_id
            from tenant_product_subscription_events
            where tenant_id = ?
              and app_key = ?
              and event_type = ?
              and idempotency_key = ?
            """,
            (resultSet, rowNum) -> resultSet.getObject("subscription_id", UUID.class),
            tenantId,
            appKey,
            eventType,
            idempotencyKey
        ).stream().findFirst();
    }

    @Override
    public void append(ProductSubscriptionEventDraft draft) {
        jdbc.update(
            """
            insert into tenant_product_subscription_events (
                subscription_id, tenant_id, app_key, event_type,
                billing_cycle, status, period_start, period_end,
                amount, currency, payment_note, idempotency_key,
                operator_user_id, event_payload
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """,
            draft.subscriptionId(),
            draft.tenantId(),
            draft.appKey(),
            draft.eventType(),
            draft.billingCycle(),
            draft.status(),
            draft.periodStart(),
            draft.periodEnd(),
            draft.amount(),
            draft.currency(),
            draft.paymentNote(),
            draft.idempotencyKey(),
            draft.operatorUserId(),
            draft.eventPayloadJson() == null || draft.eventPayloadJson().isBlank() ? "{}" : draft.eventPayloadJson()
        );
    }
}
