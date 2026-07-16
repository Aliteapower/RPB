package com.rpb.reservation.staffhome.api;

import java.util.List;
import java.util.UUID;

public record StaffHomeOverviewResponse(
    boolean success,
    UUID storeId,
    String businessDate,
    String storeTimezone,
    ReservationMetricsResponse reservation,
    QueueMetricsResponse queue,
    TableMetricsResponse tables,
    List<PartySizeGroupMetricsResponse> partySizeGroups
) {

    public StaffHomeOverviewResponse {
        partySizeGroups = partySizeGroups == null ? List.of() : List.copyOf(partySizeGroups);
    }

    public record ReservationMetricsResponse(
        int totalReservations,
        int totalPartySize,
        int arrivedReservations,
        int arrivedPartySize,
        int seatedReservations,
        int seatedPartySize,
        int cancelledReservations
    ) {
    }

    public record QueueMetricsResponse(
        int waitingTickets,
        int waitingPartySize,
        int calledTickets,
        int calledPartySize,
        int seatedTickets,
        int skippedTickets,
        int cancelledTickets,
        int expiredTickets
    ) {
    }

    public record TableMetricsResponse(
        int totalTables,
        int availableTables,
        int reservedTables,
        int occupiedTables,
        int cleaningTables,
        int temporaryGroups
    ) {
    }

    public record PartySizeGroupMetricsResponse(
        String label,
        int groups,
        int partySize
    ) {
    }
}
