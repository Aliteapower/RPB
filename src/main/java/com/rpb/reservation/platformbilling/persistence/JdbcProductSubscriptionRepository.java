package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformBillingServiceErrorCode;
import com.rpb.reservation.platformbilling.application.PlatformBillingServiceException;
import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionDraft;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductSubscriptionRepository implements ProductSubscriptionRepository {
    private final JdbcTemplate jdbc;

    public JdbcProductSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsActiveTenant(UUID tenantId) {
        Integer count = jdbc.queryForObject(
            "select count(*) from tenants where id = ? and deleted_at is null",
            Integer.class,
            tenantId
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<PlatformProductLine> findProductLine(String appKey) {
        return jdbc.query(
            """
            select app_key, app_name, status, default_entry_route, description,
                   sort_order, created_at, updated_at
            from platform_apps
            where app_key = ?
            """,
            JdbcPlatformProductLineRepository::mapProductLine,
            appKey
        ).stream().findFirst();
    }

    @Override
    public List<ProductSubscription> listByTenantId(UUID tenantId) {
        return jdbc.query(baseSelect() + " where subscription.tenant_id = ? order by app.sort_order, app.app_key",
            JdbcProductSubscriptionRepository::mapSubscription,
            tenantId
        );
    }

    @Override
    public Optional<ProductSubscription> findByTenantIdAndId(UUID tenantId, UUID subscriptionId) {
        return jdbc.query(baseSelect() + " where subscription.tenant_id = ? and subscription.id = ?",
            JdbcProductSubscriptionRepository::mapSubscription,
            tenantId,
            subscriptionId
        ).stream().findFirst();
    }

    @Override
    public Optional<ProductSubscription> findByTenantIdAndAppKey(UUID tenantId, String appKey) {
        return jdbc.query(baseSelect() + " where subscription.tenant_id = ? and subscription.app_key = ?",
            JdbcProductSubscriptionRepository::mapSubscription,
            tenantId,
            appKey
        ).stream().findFirst();
    }

    @Override
    public ProductSubscription create(ProductSubscriptionDraft draft) {
        UUID id = jdbc.queryForObject(
            """
            insert into tenant_product_subscriptions (
                tenant_id, app_key, billing_cycle, status,
                current_period_start, current_period_end, amount, currency,
                payment_note, operator_user_id
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            returning id
            """,
            UUID.class,
            draft.tenantId(),
            draft.appKey(),
            draft.billingCycle(),
            draft.status(),
            draft.currentPeriodStart(),
            draft.currentPeriodEnd(),
            draft.amount(),
            draft.currency(),
            draft.paymentNote(),
            draft.operatorUserId()
        );
        return findByTenantIdAndId(draft.tenantId(), id).orElseThrow();
    }

    @Override
    public ProductSubscription update(ProductSubscriptionUpdate update) {
        int updatedRows = jdbc.update(
            """
            update tenant_product_subscriptions
            set billing_cycle = ?,
                status = ?,
                current_period_start = ?,
                current_period_end = ?,
                amount = ?,
                currency = ?,
                payment_note = ?,
                operator_user_id = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and version = ?
            """,
            update.billingCycle(),
            update.status(),
            update.currentPeriodStart(),
            update.currentPeriodEnd(),
            update.amount(),
            update.currency(),
            update.paymentNote(),
            update.operatorUserId(),
            update.tenantId(),
            update.subscriptionId(),
            update.expectedVersion()
        );
        if (updatedRows != 1) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.VERSION_CONFLICT);
        }
        return findByTenantIdAndId(update.tenantId(), update.subscriptionId()).orElseThrow();
    }

    private static String baseSelect() {
        return """
            select subscription.id,
                   subscription.tenant_id,
                   subscription.app_key,
                   app.app_name as product_line_name,
                   subscription.billing_cycle,
                   subscription.status,
                   subscription.current_period_start,
                   subscription.current_period_end,
                   subscription.amount,
                   subscription.currency,
                   subscription.payment_note,
                   coalesce(entitlement.status, 'disabled') as entitlement_status,
                   entitlement.valid_until as entitlement_valid_until,
                   subscription.created_at,
                   subscription.updated_at,
                   subscription.version
            from tenant_product_subscriptions subscription
            join platform_apps app on app.app_key = subscription.app_key
            left join tenant_app_entitlements entitlement
              on entitlement.tenant_id = subscription.tenant_id
             and entitlement.app_key = subscription.app_key
            """;
    }

    private static ProductSubscription mapSubscription(ResultSet resultSet, int rowNum) throws SQLException {
        String status = resultSet.getString("status");
        return new ProductSubscription(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("tenant_id", UUID.class),
            resultSet.getString("app_key"),
            resultSet.getString("product_line_name"),
            resultSet.getString("billing_cycle"),
            status,
            status,
            resultSet.getObject("current_period_start", java.time.OffsetDateTime.class),
            resultSet.getObject("current_period_end", java.time.OffsetDateTime.class),
            resultSet.getBigDecimal("amount"),
            resultSet.getString("currency"),
            resultSet.getString("payment_note"),
            resultSet.getString("entitlement_status"),
            resultSet.getObject("entitlement_valid_until", java.time.OffsetDateTime.class),
            resultSet.getObject("created_at", java.time.OffsetDateTime.class),
            resultSet.getObject("updated_at", java.time.OffsetDateTime.class),
            resultSet.getInt("version")
        );
    }
}
