package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationApiMapper {

    public CreateReservationCommand toCommand(
        CreateReservationRequest request,
        UUID storeId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CreateReservationCommand(
            actor.tenantId(),
            storeId,
            request.partySize(),
            request.reservedStartAt(),
            request.reservedEndAt(),
            request.customerId(),
            trimToNull(request.customerName()),
            trimToNull(request.customerNickname()),
            trimToNull(request.phoneE164()),
            trimToNull(request.note()),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            null,
            "staff",
            null,
            request.tableId(),
            request.tableGroupId()
        );
    }

    public CreateReservationResponse toResponse(ReservationCreateResult result, CreateReservationRequest request) {
        return new CreateReservationResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.status(),
            result.partySize(),
            result.reservedStartAt(),
            result.reservedEndAt(),
            result.holdUntilAt(),
            result.businessDate(),
            new CreateReservationResponse.CustomerResponse(
                result.customerId(),
                trimToNull(request.customerName()),
                trimToNull(request.phoneE164())
            ),
            List.of("reservation.created", "reservation.confirmed"),
            ApiIdempotencyResponse.completed(result.replayed())
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
