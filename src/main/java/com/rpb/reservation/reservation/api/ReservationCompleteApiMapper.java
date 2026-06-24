package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCompleteResult;
import com.rpb.reservation.reservation.application.command.CompleteReservationCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationCompleteApiMapper {

    public CompleteReservationCommand toCommand(
        CompleteReservationRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CompleteReservationCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            idempotencyKey == null ? null : idempotencyKey.trim(),
            actor.actorId(),
            actor.actorType(),
            request.completedAt(),
            request.reasonCode(),
            request.note()
        );
    }

    public CompleteReservationResponse toResponse(ReservationCompleteResult result) {
        return new CompleteReservationResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.status(),
            result.completedAt(),
            result.seatingId(),
            result.seatingStatus(),
            result.alreadyCompleted(),
            result.events(),
            ApiIdempotencyResponse.completed(result.replayed())
        );
    }
}
