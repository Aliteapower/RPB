package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationMealPeriodCommand;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public record ReservationMealPeriodRequest(
    String periodKey,
    String displayName,
    String startLocalTime,
    String endLocalTime,
    Boolean crossesNextDay,
    Integer slotIntervalMinutes,
    String status,
    Integer sortOrder,
    Integer version
) {
    public ReservationMealPeriodCommand toCommand() {
        return new ReservationMealPeriodCommand(
            periodKey,
            displayName,
            parseTime(startLocalTime),
            parseTime(endLocalTime),
            crossesNextDay,
            slotIntervalMinutes,
            status,
            sortOrder,
            version
        );
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ReservationMealPeriodApiException(ReservationMealPeriodApiErrorCode.REQUEST_INVALID);
        }
    }
}
