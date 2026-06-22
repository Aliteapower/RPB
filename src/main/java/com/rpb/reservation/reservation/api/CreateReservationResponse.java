package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateReservationResponse(
    boolean success,
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
    CustomerResponse customer,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerResponse(
        UUID id,
        String displayName,
        String phoneE164
    ) {
    }
}
