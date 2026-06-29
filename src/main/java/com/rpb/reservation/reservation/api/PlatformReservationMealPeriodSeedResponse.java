package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import java.util.List;

public record PlatformReservationMealPeriodSeedResponse(
    boolean success,
    List<ReservationMealPeriodResponse> periods
) {
    public static PlatformReservationMealPeriodSeedResponse from(List<ReservationMealPeriod> periods) {
        return new PlatformReservationMealPeriodSeedResponse(
            true,
            periods.stream().map(ReservationMealPeriodResponse::from).toList()
        );
    }
}
