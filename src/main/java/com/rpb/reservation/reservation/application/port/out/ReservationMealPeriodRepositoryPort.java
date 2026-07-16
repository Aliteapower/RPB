package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.ReservationMealPeriodCommand;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationMealPeriodRepositoryPort {
    Optional<Boolean> findUsePlatformSeed(StoreScope scope);

    List<ReservationMealPeriod> findPlatformSeedPeriods();

    List<ReservationMealPeriod> findStorePeriods(StoreScope scope);

    default List<ReservationMealPeriod> replacePlatformSeedPeriods(List<ReservationMealPeriodCommand> periods) {
        throw new UnsupportedOperationException("replace_platform_seed_periods_not_supported");
    }

    default void upsertStoreMealPeriodSetting(StoreScope scope, boolean usePlatformSeed) {
        throw new UnsupportedOperationException("upsert_store_meal_period_setting_not_supported");
    }

    default List<ReservationMealPeriod> replaceStorePeriods(StoreScope scope, List<ReservationMealPeriodCommand> periods) {
        throw new UnsupportedOperationException("replace_store_periods_not_supported");
    }

    default List<ReservationMealPeriod> copyPlatformSeedPeriodsToStore(StoreScope scope) {
        throw new UnsupportedOperationException("copy_platform_seed_periods_to_store_not_supported");
    }

    static ReservationMealPeriodRepositoryPort platformDefault() {
        return new ReservationMealPeriodRepositoryPort() {
            @Override
            public Optional<Boolean> findUsePlatformSeed(StoreScope scope) {
                return Optional.of(true);
            }

            @Override
            public List<ReservationMealPeriod> findPlatformSeedPeriods() {
                return List.of(
                    new ReservationMealPeriod(
                        UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026001"),
                        "lunch",
                        "午餐",
                        LocalTime.of(11, 0),
                        LocalTime.of(15, 0),
                        false,
                        30,
                        "active",
                        10,
                        0
                    ),
                    new ReservationMealPeriod(
                        UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026002"),
                        "dinner",
                        "晚餐",
                        LocalTime.of(17, 0),
                        LocalTime.of(0, 30),
                        true,
                        30,
                        "active",
                        20,
                        0
                    )
                );
            }

            @Override
            public List<ReservationMealPeriod> findStorePeriods(StoreScope scope) {
                return List.of();
            }
        };
    }
}
