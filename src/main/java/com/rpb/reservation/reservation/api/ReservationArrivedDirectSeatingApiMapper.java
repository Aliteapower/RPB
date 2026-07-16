package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingResult;
import com.rpb.reservation.reservation.application.command.SeatArrivedReservationCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationArrivedDirectSeatingApiMapper {

    public SeatArrivedReservationCommand toCommand(
        SeatArrivedReservationRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new SeatArrivedReservationCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            request.tableId(),
            request.tableGroupId(),
            request.temporaryTableIds(),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.overrideReasonCode()),
            trimToNull(request.overrideNote()),
            trimToNull(request.note())
        );
    }

    public SeatArrivedReservationResponse toResponse(ReservationArrivedDirectSeatingResult result) {
        return new SeatArrivedReservationResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.seatingId(),
            result.seatingStatus(),
            apiResourceType(result.resourceType()),
            result.resourceId(),
            result.alreadySeated(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String apiResourceType(String resourceType) {
        if ("dining_table".equals(resourceType)) {
            return "table";
        }
        return resourceType;
    }

    private static String idempotencyStatus(ReservationArrivedDirectSeatingResult result) {
        if (hasText(result.idempotencyStatus())) {
            return result.idempotencyStatus().trim();
        }
        return "completed";
    }

    private static String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
