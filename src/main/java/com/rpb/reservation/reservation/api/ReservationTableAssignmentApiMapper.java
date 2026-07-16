package com.rpb.reservation.reservation.api;

import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.reservation.application.AssignableReservationTable;
import com.rpb.reservation.reservation.application.AssignableReservationTablesResult;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentResult;
import com.rpb.reservation.reservation.application.command.AssignReservationTableCommand;
import com.rpb.reservation.reservation.application.query.AssignableReservationTablesQuery;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationTableAssignmentApiMapper {

    public AssignableReservationTablesQuery toQuery(UUID storeId, UUID reservationId, CurrentActor actor) {
        return new AssignableReservationTablesQuery(
            actor.tenantId(),
            storeId,
            reservationId,
            actor.actorId(),
            actor.actorType()
        );
    }

    public AssignReservationTableCommand toCommand(
        AssignReservationTableRequest request,
        UUID storeId,
        UUID reservationId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new AssignReservationTableCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            request.tableId(),
            idempotencyKey.trim(),
            actor.actorId(),
            actor.actorType(),
            OperationSource.fromActorType(actor.actorType())
        );
    }

    public AssignableReservationTablesResponse toResponse(AssignableReservationTablesResult result) {
        return new AssignableReservationTablesResponse(
            true,
            result.reservationId(),
            result.partySize(),
            result.tables().stream().map(this::toResponse).toList()
        );
    }

    public ReservationTableAssignmentResponse toResponse(ReservationTableAssignmentResult result) {
        return new ReservationTableAssignmentResponse(
            true,
            result.reservationId(),
            result.tableId(),
            result.tableCode(),
            result.assignmentStatus(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private AssignableReservationTablesResponse.AssignableReservationTableResponse toResponse(
        AssignableReservationTable table
    ) {
        return new AssignableReservationTablesResponse.AssignableReservationTableResponse(
            table.tableId(),
            table.tableCode(),
            table.displayName(),
            table.areaName(),
            table.capacityMin(),
            table.capacityMax()
        );
    }

    private static String idempotencyStatus(ReservationTableAssignmentResult result) {
        return result.idempotencyStatus() == null || result.idempotencyStatus().isBlank()
            ? "completed"
            : result.idempotencyStatus().trim();
    }
}
