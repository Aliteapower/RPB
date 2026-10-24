package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.appgate.domain.StoreAppSubscription;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Authorization projection only: no subscription mutation or commercial service dependency. */
@Repository
public class JdbcStoreAppSubscriptionRepository implements StoreAppSubscriptionRepository {
    private final JdbcTemplate jdbc;

    public JdbcStoreAppSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, StoreAppSubscription> findByTenantAndStore(UUID tenantId, UUID storeId) {
        return jdbc.query("""
            select subscription.app_key, subscription.status as subscription_status,
                   case when store.id is not null then item.status end as store_status,
                   coalesce(item.current_period_end, subscription.current_period_end) as valid_until
            from tenant_product_subscriptions subscription
            left join tenant_product_subscription_items item
              on item.subscription_id = subscription.id
             and item.tenant_id = subscription.tenant_id
             and item.app_key = subscription.app_key
             and item.scope_type = 'store'
             and item.store_id = ?
            left join stores store
              on store.id = item.store_id and store.tenant_id = item.tenant_id
             and store.status = 'active' and store.deleted_at is null
            where subscription.tenant_id = ?
              and subscription.billing_cycle <> 'legacy_grant'
              and exists (
                  select 1 from tenant_product_subscription_items scoped
                  where scoped.subscription_id = subscription.id
                    and scoped.tenant_id = subscription.tenant_id
                    and scoped.app_key = subscription.app_key
                    and scoped.scope_type = 'store'
              )
            """, rs -> {
                Map<String, StoreAppSubscription> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("app_key"), new StoreAppSubscription(
                        rs.getString("subscription_status"), rs.getString("store_status"),
                        rs.getObject("valid_until", OffsetDateTime.class)));
                }
                return result;
            }, storeId, tenantId);
    }
}
