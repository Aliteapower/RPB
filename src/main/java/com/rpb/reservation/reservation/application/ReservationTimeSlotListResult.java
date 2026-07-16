package com.rpb.reservation.reservation.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservationTimeSlotListResult(
    boolean success,
    ReservationTodayViewError error,
    UUID storeId,
    LocalDate businessDate,
    String timezone,
    List<ReservationTimeSlot> slots
) {
    public ReservationTimeSlotListResult {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public static ReservationTimeSlotListResult success(
        UUID storeId,
        LocalDate businessDate,
        String timezone,
        List<ReservationTimeSlot> slots
    ) {
        return new ReservationTimeSlotListResult(true, null, storeId, businessDate, timezone, slots);
    }

    public static ReservationTimeSlotListResult failure(ReservationTodayViewError error) {
        return new ReservationTimeSlotListResult(false, error, null, null, null, List.of());
    }
}
