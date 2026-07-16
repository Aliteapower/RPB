package com.rpb.reservation.reservation.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.ReservationMealPeriodCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationMealPeriodJdbcRepository implements ReservationMealPeriodRepositoryPort {
    private final JdbcTemplate jdbc;

    public ReservationMealPeriodJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Boolean> findUsePlatformSeed(StoreScope scope) {
        return jdbc.query(
            """
            select use_platform_seed
            from store_reservation_meal_period_settings
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> rs.getBoolean("use_platform_seed"),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    @Override
    public List<ReservationMealPeriod> findPlatformSeedPeriods() {
        return jdbc.query(
            """
            select id, period_key, display_name, start_local_time, end_local_time,
                   crosses_next_day, slot_interval_minutes, status, sort_order, version
            from platform_reservation_meal_period_seeds
            where deleted_at is null
            order by sort_order, period_key
            """,
            ReservationMealPeriodJdbcRepository::toMealPeriod
        );
    }

    @Override
    public List<ReservationMealPeriod> findStorePeriods(StoreScope scope) {
        return jdbc.query(
            """
            select id, period_key, display_name, start_local_time, end_local_time,
                   crosses_next_day, slot_interval_minutes, status, sort_order, version
            from store_reservation_meal_periods
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            order by sort_order, period_key
            """,
            ReservationMealPeriodJdbcRepository::toMealPeriod,
            scope.tenantId().value(),
            scope.storeId().value()
        );
    }

    @Override
    public List<ReservationMealPeriod> replacePlatformSeedPeriods(List<ReservationMealPeriodCommand> periods) {
        jdbc.update(
            """
            update platform_reservation_meal_period_seeds
            set deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where deleted_at is null
            """
        );
        for (ReservationMealPeriodCommand period : periods) {
            jdbc.update(
                """
                insert into platform_reservation_meal_period_seeds (
                    period_key, display_name, start_local_time, end_local_time,
                    crosses_next_day, slot_interval_minutes, status, sort_order, deleted_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, null)
                on conflict (period_key) do update
                set display_name = excluded.display_name,
                    start_local_time = excluded.start_local_time,
                    end_local_time = excluded.end_local_time,
                    crosses_next_day = excluded.crosses_next_day,
                    slot_interval_minutes = excluded.slot_interval_minutes,
                    status = excluded.status,
                    sort_order = excluded.sort_order,
                    deleted_at = null,
                    updated_at = now(),
                    version = platform_reservation_meal_period_seeds.version + 1
                """,
                period.periodKey(),
                period.displayName(),
                period.startLocalTime(),
                period.endLocalTime(),
                period.crossesNextDay(),
                period.slotIntervalMinutes(),
                period.status(),
                period.sortOrder()
            );
        }
        return findPlatformSeedPeriods();
    }

    @Override
    public void upsertStoreMealPeriodSetting(StoreScope scope, boolean usePlatformSeed) {
        jdbc.update(
            """
            insert into store_reservation_meal_period_settings (tenant_id, store_id, use_platform_seed)
            values (?, ?, ?)
            on conflict (tenant_id, store_id) do update
            set use_platform_seed = excluded.use_platform_seed,
                deleted_at = null,
                updated_at = now(),
                version = store_reservation_meal_period_settings.version + 1
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            usePlatformSeed
        );
    }

    @Override
    public List<ReservationMealPeriod> replaceStorePeriods(StoreScope scope, List<ReservationMealPeriodCommand> periods) {
        softDeleteStorePeriods(scope);
        for (ReservationMealPeriodCommand period : periods) {
            insertStorePeriod(scope, period, null);
        }
        return findStorePeriods(scope);
    }

    @Override
    public List<ReservationMealPeriod> copyPlatformSeedPeriodsToStore(StoreScope scope) {
        softDeleteStorePeriods(scope);
        jdbc.update(
            """
            insert into store_reservation_meal_periods (
                tenant_id, store_id, source_seed_id, period_key, display_name,
                start_local_time, end_local_time, crosses_next_day,
                slot_interval_minutes, status, sort_order
            )
            select ?, ?, seed.id, seed.period_key, seed.display_name,
                   seed.start_local_time, seed.end_local_time, seed.crosses_next_day,
                   seed.slot_interval_minutes, seed.status, seed.sort_order
            from platform_reservation_meal_period_seeds seed
            where seed.status = 'active'
              and seed.deleted_at is null
            order by seed.sort_order, seed.period_key
            """,
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return findStorePeriods(scope);
    }

    private void softDeleteStorePeriods(StoreScope scope) {
        jdbc.update(
            """
            update store_reservation_meal_periods
            set deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            scope.tenantId().value(),
            scope.storeId().value()
        );
    }

    private void insertStorePeriod(StoreScope scope, ReservationMealPeriodCommand period, java.util.UUID sourceSeedId) {
        jdbc.update(
            """
            insert into store_reservation_meal_periods (
                tenant_id, store_id, source_seed_id, period_key, display_name,
                start_local_time, end_local_time, crosses_next_day,
                slot_interval_minutes, status, sort_order
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            sourceSeedId,
            period.periodKey(),
            period.displayName(),
            period.startLocalTime(),
            period.endLocalTime(),
            period.crossesNextDay(),
            period.slotIntervalMinutes(),
            period.status(),
            period.sortOrder()
        );
    }

    private static ReservationMealPeriod toMealPeriod(ResultSet rs, int rowNum) throws SQLException {
        return new ReservationMealPeriod(
            rs.getObject("id", java.util.UUID.class),
            rs.getString("period_key"),
            rs.getString("display_name"),
            rs.getObject("start_local_time", LocalTime.class),
            rs.getObject("end_local_time", LocalTime.class),
            rs.getBoolean("crosses_next_day"),
            rs.getInt("slot_interval_minutes"),
            rs.getString("status"),
            rs.getInt("sort_order"),
            rs.getInt("version")
        );
    }
}
