package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.BillableStore;
import com.rpb.reservation.platformbilling.application.PlatformBillingServiceErrorCode;
import com.rpb.reservation.platformbilling.application.PlatformBillingServiceException;
import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionItem;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionItemUpdate;
import com.rpb.reservation.platformbilling.application.SubscriptionQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductSubscriptionItemRepository implements ProductSubscriptionItemRepository {
    private final JdbcTemplate jdbc;

    public JdbcProductSubscriptionItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BillableStore> listActiveStores(UUID tenantId) {
        return jdbc.query(
            """
            select id, store_code, display_name
            from stores
            where tenant_id = ?
              and status = 'active'
              and deleted_at is null
            order by lower(store_code), created_at, id
            """,
            (rs, rowNum) -> new BillableStore(
                rs.getObject("id", UUID.class),
                rs.getString("store_code"),
                rs.getString("display_name")
            ),
            tenantId
        );
    }

    @Override
    public List<ProductSubscriptionItem> listByTenantId(UUID tenantId) {
        return jdbc.query(
            """
            select
                item.id,
                item.subscription_id,
                item.tenant_id,
                item.app_key,
                item.scope_type,
                item.store_id,
                store.store_code,
                store.display_name as store_name,
                store.operating_entity_id,
                entity.display_name as operating_entity_name,
                item.billing_cycle,
                item.current_period_start,
                item.current_period_end,
                item.quantity,
                item.unit_amount,
                item.amount,
                item.currency,
                item.status,
                item.payment_note,
                item.created_at,
                item.updated_at,
                item.version
            from tenant_product_subscription_items item
            left join stores store
              on store.id = item.store_id
             and store.tenant_id = item.tenant_id
             and store.deleted_at is null
            left join operating_entities entity
              on entity.id = store.operating_entity_id
             and entity.tenant_id = store.tenant_id
             and entity.deleted_at is null
            where item.tenant_id = ?
            order by item.app_key, lower(coalesce(store.store_code, '')), item.created_at, item.id
            """,
            (rs, rowNum) -> item(rs),
            tenantId
        );
    }

    @Override
    public Optional<ProductSubscriptionItem> findByTenantSubscriptionAndId(UUID tenantId, UUID subscriptionId, UUID itemId) {
        return jdbc.query(
            """
            select
                item.id,
                item.subscription_id,
                item.tenant_id,
                item.app_key,
                item.scope_type,
                item.store_id,
                store.store_code,
                store.display_name as store_name,
                store.operating_entity_id,
                entity.display_name as operating_entity_name,
                item.billing_cycle,
                item.current_period_start,
                item.current_period_end,
                item.quantity,
                item.unit_amount,
                item.amount,
                item.currency,
                item.status,
                item.payment_note,
                item.created_at,
                item.updated_at,
                item.version
            from tenant_product_subscription_items item
            join stores store
              on store.id = item.store_id
             and store.tenant_id = item.tenant_id
             and store.status = 'active'
             and store.deleted_at is null
            left join operating_entities entity
              on entity.id = store.operating_entity_id
             and entity.tenant_id = store.tenant_id
             and entity.deleted_at is null
            where item.tenant_id = ?
              and item.subscription_id = ?
              and item.id = ?
              and item.scope_type = 'store'
              and item.status in ('active', 'suspended')
            """,
            (rs, rowNum) -> item(rs),
            tenantId,
            subscriptionId,
            itemId
        ).stream().findFirst();
    }

    @Override
    public void replaceStoreItems(ProductSubscription subscription, List<BillableStore> billableStores, SubscriptionQuote quote) {
        jdbc.update(
            "delete from tenant_product_subscription_items where tenant_id = ? and subscription_id = ?",
            subscription.tenantId(),
            subscription.id()
        );
        if (quote == null || billableStores == null || billableStores.isEmpty()) {
            return;
        }

        List<BigDecimal> allocatedAmounts = allocate(quote.finalAmount(), billableStores.size());
        List<Object[]> batchArgs = new ArrayList<>();
        for (int index = 0; index < billableStores.size(); index++) {
            BillableStore store = billableStores.get(index);
            batchArgs.add(new Object[] {
                subscription.id(),
                subscription.tenantId(),
                subscription.appKey(),
                store.storeId(),
                subscription.billingCycle(),
                subscription.currentPeriodStart(),
                subscription.currentPeriodEnd(),
                1,
                quote.unitAmount(),
                allocatedAmounts.get(index),
                quote.currency(),
                subscription.status(),
                subscription.paymentNote()
            });
        }
        jdbc.batchUpdate(
            """
            insert into tenant_product_subscription_items (
                subscription_id, tenant_id, app_key, scope_type, store_id,
                billing_cycle, current_period_start, current_period_end,
                quantity, unit_amount, amount, currency, status, payment_note
            )
            values (?, ?, ?, 'store', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            batchArgs
        );
    }

    @Override
    public ProductSubscriptionItem updateStoreItem(ProductSubscriptionItemUpdate update) {
        int updatedRows = jdbc.update(
            """
            update tenant_product_subscription_items
            set billing_cycle = ?,
                status = ?,
                current_period_start = ?,
                current_period_end = ?,
                unit_amount = ?,
                amount = ?,
                currency = ?,
                payment_note = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and subscription_id = ?
              and id = ?
              and scope_type = 'store'
              and version = ?
            """,
            update.billingCycle(),
            update.status(),
            update.currentPeriodStart(),
            update.currentPeriodEnd(),
            update.unitAmount(),
            update.amount(),
            update.currency(),
            update.paymentNote(),
            update.tenantId(),
            update.subscriptionId(),
            update.itemId(),
            update.expectedVersion()
        );
        if (updatedRows != 1) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.VERSION_CONFLICT);
        }
        return findByTenantSubscriptionAndId(update.tenantId(), update.subscriptionId(), update.itemId()).orElseThrow();
    }

    @Override
    public void updateStatus(UUID tenantId, UUID subscriptionId, String status) {
        jdbc.update(
            """
            update tenant_product_subscription_items
            set status = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and subscription_id = ?
            """,
            status,
            tenantId,
            subscriptionId
        );
    }

    private static List<BigDecimal> allocate(BigDecimal total, int count) {
        if (count <= 0) {
            return List.of();
        }
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO : total;
        BigDecimal base = safeTotal.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int index = 0; index < count; index++) {
            BigDecimal amount = index == count - 1 ? safeTotal.subtract(running) : base;
            amounts.add(amount);
            running = running.add(amount);
        }
        return amounts;
    }

    private static ProductSubscriptionItem item(ResultSet rs) throws SQLException {
        return new ProductSubscriptionItem(
            rs.getObject("id", UUID.class),
            rs.getObject("subscription_id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("app_key"),
            rs.getString("scope_type"),
            rs.getObject("store_id", UUID.class),
            rs.getString("store_code"),
            rs.getString("store_name"),
            rs.getObject("operating_entity_id", UUID.class),
            rs.getString("operating_entity_name"),
            rs.getString("billing_cycle"),
            rs.getObject("current_period_start", OffsetDateTime.class),
            rs.getObject("current_period_end", OffsetDateTime.class),
            rs.getInt("quantity"),
            rs.getBigDecimal("unit_amount"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("status"),
            rs.getString("payment_note"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("version")
        );
    }
}
