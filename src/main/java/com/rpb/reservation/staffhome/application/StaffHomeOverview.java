package com.rpb.reservation.staffhome.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StaffHomeOverview(
    UUID storeId,
    LocalDate businessDate,
    String storeTimezone,
    ReservationMetrics reservation,
    QueueMetrics queue,
    TableMetrics tables,
    List<PartySizeGroupMetrics> partySizeGroups
) {

    public StaffHomeOverview {
        partySizeGroups = partySizeGroups == null ? List.of() : List.copyOf(partySizeGroups);
    }

    public record ReservationMetrics(
        int totalReservations,
        int totalPartySize,
        int arrivedReservations,
        int arrivedPartySize,
        int seatedReservations,
        int seatedPartySize,
        int cancelledReservations
    ) {
    }

    public record QueueMetrics(
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

    public record TableMetrics(
        int totalTables,
        int availableTables,
        int reservedTables,
        int occupiedTables,
        int cleaningTables,
        int temporaryGroups
    ) {
    }

    public record PartySizeGroupMetrics(
        String label,
        int groups,
        int partySize
    ) {
    }
}
