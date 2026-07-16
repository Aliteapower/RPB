package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.StoreReservationMealPeriodSettings;
import java.util.List;

public record ReservationMealPeriodSettingsResponse(
    boolean success,
    boolean usePlatformSeed,
    List<ReservationMealPeriodResponse> platformPeriods,
    List<ReservationMealPeriodResponse> storePeriods,
    List<ReservationMealPeriodResponse> effectivePeriods
) {
    public static ReservationMealPeriodSettingsResponse from(StoreReservationMealPeriodSettings settings) {
        return new ReservationMealPeriodSettingsResponse(
            true,
            settings.usePlatformSeed(),
            settings.platformPeriods().stream().map(ReservationMealPeriodResponse::from).toList(),
            settings.storePeriods().stream().map(ReservationMealPeriodResponse::from).toList(),
            settings.effectivePeriods().stream().map(ReservationMealPeriodResponse::from).toList()
        );
    }
}
