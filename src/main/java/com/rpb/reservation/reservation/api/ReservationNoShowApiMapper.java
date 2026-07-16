package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationNoShowResult;
import com.rpb.reservation.reservation.application.command.MarkReservationNoShowCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationNoShowApiMapper {

    public MarkReservationNoShowCommand toCommand(
        MarkReservationNoShowRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new MarkReservationNoShowCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            idempotencyKey == null ? null : idempotencyKey.trim(),
            actor.actorId(),
            actor.actorType(),
            request.noShowAt(),
            request.reasonCode(),
            request.note()
        );
    }

    public MarkReservationNoShowResponse toResponse(ReservationNoShowResult result) {
        return new MarkReservationNoShowResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.status(),
            result.noShowAt(),
            result.noShowReasonCode(),
            result.alreadyNoShow(),
            result.events(),
            ApiIdempotencyResponse.completed(result.replayed())
        );
    }
}
