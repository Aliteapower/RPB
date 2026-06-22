package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationTodayViewResponse(
    boolean success,
    UUID storeId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate businessDate,
    String storeTimezone,
    String statusFilter,
    List<ItemResponse> items
) {

    public ReservationTodayViewResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResponse(
        UUID reservationId,
        String reservationCode,
        String status,
        int partySize,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant reservedStartAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant reservedEndAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant holdUntilAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDate businessDate,
        String customerName,
        String customerNickname,
        String phoneMasked,
        String note
    ) {
    }
}
