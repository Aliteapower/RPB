package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationTableAssignmentResponse(
    boolean success,
    UUID reservationId,
    UUID tableId,
    String tableCode,
    String assignmentStatus,
    ApiIdempotencyResponse idempotency
) {
}
