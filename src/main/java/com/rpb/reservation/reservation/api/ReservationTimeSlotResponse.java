package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationTimeSlot;
import java.util.UUID;

public record ReservationTimeSlotResponse(
    UUID periodId,
    String periodKey,
    String displayName,
    String businessDate,
    String time,
    String startAt,
    boolean nextDay,
    boolean selectable
) {
    public static ReservationTimeSlotResponse from(ReservationTimeSlot slot) {
        return new ReservationTimeSlotResponse(
            slot.periodId(),
            slot.periodKey(),
            slot.displayName(),
            slot.businessDate().toString(),
            slot.time().toString(),
            slot.startAt().toString(),
            slot.nextDay(),
            slot.selectable()
        );
    }
}
