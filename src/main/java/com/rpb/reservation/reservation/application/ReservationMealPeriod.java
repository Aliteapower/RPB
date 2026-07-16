package com.rpb.reservation.reservation.application;

import java.time.LocalTime;
import java.util.UUID;

public record ReservationMealPeriod(
    UUID id,
    String periodKey,
    String displayName,
    LocalTime startLocalTime,
    LocalTime endLocalTime,
    boolean crossesNextDay,
    int slotIntervalMinutes,
    String status,
    int sortOrder,
    int version
) {
    public ReservationMealPeriod {
        if (id == null) {
            throw new IllegalArgumentException("meal_period_id_required");
        }
        if (periodKey == null || periodKey.isBlank()) {
            throw new IllegalArgumentException("meal_period_key_required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("meal_period_display_name_required");
        }
        if (startLocalTime == null || endLocalTime == null) {
            throw new IllegalArgumentException("meal_period_time_required");
        }
        if (slotIntervalMinutes < 5 || slotIntervalMinutes > 240) {
            throw new IllegalArgumentException("meal_period_interval_invalid");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("meal_period_status_required");
        }
        periodKey = periodKey.trim();
        displayName = displayName.trim();
        status = status.trim();
    }

    public boolean active() {
        return "active".equals(status);
    }
}
