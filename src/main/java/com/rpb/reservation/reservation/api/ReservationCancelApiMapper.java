package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCancelResult;
import com.rpb.reservation.reservation.application.command.CancelReservationCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationCancelApiMapper {

    public CancelReservationCommand toCommand(
        CancelReservationRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CancelReservationCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            request.cancelledAt(),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public CancelReservationResponse toResponse(ReservationCancelResult result) {
        return new CancelReservationResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.status(),
            result.cancelledAt(),
            result.cancellationReasonCode(),
            result.alreadyCancelled(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(ReservationCancelResult result) {
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
