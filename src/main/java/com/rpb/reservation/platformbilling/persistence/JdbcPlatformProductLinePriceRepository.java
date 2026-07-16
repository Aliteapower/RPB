package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformBillingServiceErrorCode;
import com.rpb.reservation.platformbilling.application.PlatformBillingServiceException;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePrice;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePriceUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformProductLinePriceRepository implements PlatformProductLinePriceRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcPlatformProductLinePriceRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public List<PlatformProductLinePrice> findByAppKeys(Collection<String> appKeys) {
        if (appKeys == null || appKeys.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query(
            """
            select app_key, billing_cycle, amount, currency, status, version
            from platform_product_line_prices
            where app_key in (:appKeys)
            order by app_key, billing_cycle
            """,
            Map.of("appKeys", appKeys),
            JdbcPlatformProductLinePriceRepository::mapPrice
        );
    }

    @Override
    public List<PlatformProductLinePrice> replacePrices(String appKey, List<PlatformProductLinePriceUpdate> prices) {
        if (appKey == null || appKey.isBlank() || prices == null || prices.isEmpty()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        for (PlatformProductLinePriceUpdate price : prices) {
            upsert(appKey.trim(), normalized(price));
        }
        return findByAppKeys(List.of(appKey.trim()));
    }

    @Override
    public Optional<PlatformProductLinePrice> findActivePrice(String appKey, String billingCycle) {
        return jdbc.query(
            """
            select app_key, billing_cycle, amount, currency, status, version
            from platform_product_line_prices
            where app_key = ?
              and billing_cycle = ?
              and status = 'active'
            """,
            JdbcPlatformProductLinePriceRepository::mapPrice,
            appKey,
            billingCycle
        ).stream().findFirst();
    }

    private void upsert(String appKey, PlatformProductLinePriceUpdate price) {
        Optional<PlatformProductLinePrice> current = jdbc.query(
            """
            select app_key, billing_cycle, amount, currency, status, version
            from platform_product_line_prices
            where app_key = ?
              and billing_cycle = ?
            """,
            JdbcPlatformProductLinePriceRepository::mapPrice,
            appKey,
            price.billingCycle()
        ).stream().findFirst();

        if (current.isPresent()) {
            if (price.version() != null && current.get().version() != price.version()) {
                throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.VERSION_CONFLICT);
            }
            jdbc.update(
                """
                update platform_product_line_prices
                set amount = ?,
                    currency = ?,
                    status = ?,
                    updated_at = now(),
                    version = version + 1
                where app_key = ?
                  and billing_cycle = ?
                """,
                price.amount(),
                price.currency(),
                price.status(),
                appKey,
                price.billingCycle()
            );
            return;
        }

        jdbc.update(
            """
            insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
            values (?, ?, ?, ?, ?)
            """,
            appKey,
            price.billingCycle(),
            price.amount(),
            price.currency(),
            price.status()
        );
    }

    private static PlatformProductLinePriceUpdate normalized(PlatformProductLinePriceUpdate price) {
        if (price == null || price.billingCycle() == null || price.amount() == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String cycle = price.billingCycle().trim();
        if (!"monthly".equals(cycle) && !"yearly".equals(cycle)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        if (price.amount().signum() < 0) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String currency = price.currency() == null || price.currency().isBlank() ? "SGD" : price.currency().trim().toUpperCase();
        if (currency.length() != 3) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String status = price.status() == null || price.status().isBlank() ? "active" : price.status().trim();
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return new PlatformProductLinePriceUpdate(cycle, price.amount(), currency, status, price.version());
    }

    private static PlatformProductLinePrice mapPrice(ResultSet resultSet, int rowNum) throws SQLException {
        return new PlatformProductLinePrice(
            resultSet.getString("app_key"),
            resultSet.getString("billing_cycle"),
            resultSet.getBigDecimal("amount"),
            resultSet.getString("currency"),
            resultSet.getString("status"),
            resultSet.getInt("version")
        );
    }
}
