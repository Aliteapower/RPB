package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCheckInResult;
import com.rpb.reservation.reservation.application.command.CheckInReservationCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationCheckInApiMapper {

    public CheckInReservationCommand toCommand(
        CheckInReservationRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CheckInReservationCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            request.arrivedAt(),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public CheckInReservationResponse toResponse(ReservationCheckInResult result) {
        return new CheckInReservationResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.status(),
            result.arrivedAt(),
            result.alreadyArrived(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(ReservationCheckInResult result) {
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
