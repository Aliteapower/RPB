package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationTimeSlotListResult;
import java.util.List;
import java.util.UUID;

public record ReservationTimeSlotListResponse(
    boolean success,
    UUID storeId,
    String businessDate,
    String timezone,
    List<ReservationTimeSlotResponse> slots
) {
    public static ReservationTimeSlotListResponse from(ReservationTimeSlotListResult result) {
        return new ReservationTimeSlotListResponse(
            true,
            result.storeId(),
            result.businessDate().toString(),
            result.timezone(),
            result.slots().stream().map(ReservationTimeSlotResponse::from).toList()
        );
    }
}
