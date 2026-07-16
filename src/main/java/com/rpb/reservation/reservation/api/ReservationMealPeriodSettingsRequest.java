package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationMealPeriodCommand;
import java.util.List;

public record ReservationMealPeriodSettingsRequest(
    Boolean usePlatformSeed,
    Boolean copyPlatformSeed,
    List<ReservationMealPeriodRequest> periods
) {
    public List<ReservationMealPeriodCommand> commands() {
        if (periods == null) {
            return List.of();
        }
        return periods.stream().map(ReservationMealPeriodRequest::toCommand).toList();
    }
}
