package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationArrivedToQueueResult;
import com.rpb.reservation.reservation.application.command.QueueArrivedReservationCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationArrivedToQueueApiMapper {

    public QueueArrivedReservationCommand toCommand(
        QueueArrivedReservationRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new QueueArrivedReservationCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.partySizeGroup()),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public QueueArrivedReservationResponse toResponse(ReservationArrivedToQueueResult result) {
        return new QueueArrivedReservationResponse(
            true,
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.queueTicketId(),
            result.queueTicketNumber(),
            result.queueTicketStatus(),
            result.queueGroupId(),
            result.queueGroupCode(),
            result.partySize(),
            result.partySizeGroup(),
            result.businessDate(),
            result.queuePosition(),
            result.alreadyQueued(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(ReservationArrivedToQueueResult result) {
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
