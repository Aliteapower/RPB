package com.rpb.reservation.reservation.application;

import java.util.List;

public record StoreReservationMealPeriodSettings(
    boolean usePlatformSeed,
    List<ReservationMealPeriod> platformPeriods,
    List<ReservationMealPeriod> storePeriods,
    List<ReservationMealPeriod> effectivePeriods
) {
    public StoreReservationMealPeriodSettings {
        platformPeriods = platformPeriods == null ? List.of() : List.copyOf(platformPeriods);
        storePeriods = storePeriods == null ? List.of() : List.copyOf(storePeriods);
        effectivePeriods = effectivePeriods == null ? List.of() : List.copyOf(effectivePeriods);
    }
}
