package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminSettings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantAdminSettingsRepository {
    private final JdbcTemplate jdbc;

    public TenantAdminSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TenantAdminSettings> find(StoreScope scope) {
        return jdbc.query(
            """
            select store.display_name as store_name,
                   store.timezone,
                   store.locale,
                   store.date_format,
                   store.time_format,
                   store.currency,
                   coalesce(policy.reservation_hold_minutes, 15) as reservation_hold_minutes,
                   coalesce(policy.queue_call_hold_minutes, 3) as queue_call_hold_minutes,
                   coalesce(policy.expected_dining_minutes, 90) as expected_dining_minutes
            from stores store
            left join store_policies policy on policy.tenant_id = store.tenant_id
             and policy.store_id = store.id
             and policy.effective_to_at is null
             and policy.deleted_at is null
            where store.tenant_id = ?
              and store.id = ?
              and store.deleted_at is null
            """,
            (rs, rowNum) -> settings(rs),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    public void updateStore(
        StoreScope scope,
        String storeName,
        String timezone,
        String locale,
        String dateFormat,
        String timeFormat,
        String currency
    ) {
        jdbc.update(
            """
            update stores
            set display_name = ?,
                timezone = ?,
                locale = ?,
                date_format = ?,
                time_format = ?,
                currency = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            """,
            storeName,
            timezone,
            locale,
            dateFormat,
            timeFormat,
            currency,
            scope.tenantId().value(),
            scope.storeId().value()
        );
    }

    public void upsertCurrentPolicy(
        StoreScope scope,
        int reservationHoldMinutes,
        int queueCallHoldMinutes,
        int expectedDiningMinutes
    ) {
        int updated = jdbc.update(
            """
            update store_policies
            set reservation_hold_minutes = ?,
                queue_call_hold_minutes = ?,
                expected_dining_minutes = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and effective_to_at is null
              and deleted_at is null
            """,
            reservationHoldMinutes,
            queueCallHoldMinutes,
            expectedDiningMinutes,
            scope.tenantId().value(),
            scope.storeId().value()
        );
        if (updated > 0) {
            return;
        }

        jdbc.update(
            """
            insert into store_policies (
                tenant_id, store_id, reservation_hold_minutes,
                queue_call_hold_minutes, expected_dining_minutes
            )
            values (?, ?, ?, ?, ?)
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            reservationHoldMinutes,
            queueCallHoldMinutes,
            expectedDiningMinutes
        );
    }

    private static TenantAdminSettings settings(ResultSet rs) throws SQLException {
        return new TenantAdminSettings(
            rs.getString("store_name"),
            rs.getString("timezone"),
            rs.getString("locale"),
            rs.getString("date_format"),
            rs.getString("time_format"),
            rs.getString("currency"),
            rs.getInt("reservation_hold_minutes"),
            rs.getInt("queue_call_hold_minutes"),
            rs.getInt("expected_dining_minutes")
        );
    }
}
