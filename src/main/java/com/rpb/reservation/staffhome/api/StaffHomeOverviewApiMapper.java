package com.rpb.reservation.staffhome.api;

import com.rpb.reservation.staffhome.application.StaffHomeOverview;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewResult;
import org.springframework.stereotype.Component;

@Component
public class StaffHomeOverviewApiMapper {

    public StaffHomeOverviewResponse toResponse(StaffHomeOverviewResult result) {
        StaffHomeOverview overview = result.overview();
        return new StaffHomeOverviewResponse(
            true,
            overview.storeId(),
            overview.businessDate().toString(),
            overview.storeTimezone(),
            reservation(overview.reservation()),
            queue(overview.queue()),
            tables(overview.tables()),
            overview.partySizeGroups().stream()
                .map(StaffHomeOverviewApiMapper::partySizeGroup)
                .toList()
        );
    }

    private static StaffHomeOverviewResponse.ReservationMetricsResponse reservation(
        StaffHomeOverview.ReservationMetrics metrics
    ) {
        return new StaffHomeOverviewResponse.ReservationMetricsResponse(
            metrics.totalReservations(),
            metrics.totalPartySize(),
            metrics.arrivedReservations(),
            metrics.arrivedPartySize(),
            metrics.seatedReservations(),
            metrics.seatedPartySize(),
            metrics.cancelledReservations()
        );
    }

    private static StaffHomeOverviewResponse.QueueMetricsResponse queue(
        StaffHomeOverview.QueueMetrics metrics
    ) {
        return new StaffHomeOverviewResponse.QueueMetricsResponse(
            metrics.waitingTickets(),
            metrics.waitingPartySize(),
            metrics.calledTickets(),
            metrics.calledPartySize(),
            metrics.seatedTickets(),
            metrics.skippedTickets(),
            metrics.cancelledTickets(),
            metrics.expiredTickets()
        );
    }

    private static StaffHomeOverviewResponse.TableMetricsResponse tables(
        StaffHomeOverview.TableMetrics metrics
    ) {
        return new StaffHomeOverviewResponse.TableMetricsResponse(
            metrics.totalTables(),
            metrics.availableTables(),
            metrics.reservedTables(),
            metrics.occupiedTables(),
            metrics.cleaningTables(),
            metrics.temporaryGroups()
        );
    }

    private static StaffHomeOverviewResponse.PartySizeGroupMetricsResponse partySizeGroup(
        StaffHomeOverview.PartySizeGroupMetrics metrics
    ) {
        return new StaffHomeOverviewResponse.PartySizeGroupMetricsResponse(
            metrics.label(),
            metrics.groups(),
            metrics.partySize()
        );
    }
}
