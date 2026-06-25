package com.rpb.reservation.staffhome.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.queue.application.port.out.QueueTicketOverviewMetric;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationTodayViewError;
import com.rpb.reservation.reservation.application.ReservationTodayViewItem;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import com.rpb.reservation.reservation.application.query.ReservationTodayViewQuery;
import com.rpb.reservation.reservation.application.service.ReservationTodayViewApplicationService;
import com.rpb.reservation.staffhome.application.StaffHomeOverview;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewError;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewQuery;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewResult;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.TableResourceItem;
import com.rpb.reservation.table.application.TableResourceListQuery;
import com.rpb.reservation.table.application.TableResourceListResult;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffHomeOverviewApplicationService {
    private static final String RESERVATION_STATUS_ALL = "all";
    private static final String RESOURCE_DINING_TABLE = "dining_table";
    private static final String RESOURCE_TABLE_GROUP = "table_group";
    private static final String GROUP_TYPE_TEMPORARY = "temporary";
    private static final List<String> DEFAULT_PARTY_SIZE_GROUPS = List.of("1-2", "3-4", "5-6", "7+");

    private final ReservationTodayViewApplicationService reservationTodayViewService;
    private final TableResourceListApplicationService tableResourceListService;
    private final QueueTicketRepositoryPort queueTicketRepository;

    public StaffHomeOverviewApplicationService(
        ReservationTodayViewApplicationService reservationTodayViewService,
        TableResourceListApplicationService tableResourceListService,
        QueueTicketRepositoryPort queueTicketRepository
    ) {
        this.reservationTodayViewService = reservationTodayViewService;
        this.tableResourceListService = tableResourceListService;
        this.queueTicketRepository = queueTicketRepository;
    }

    @Transactional(readOnly = true)
    public StaffHomeOverviewResult getOverview(StaffHomeOverviewQuery query) {
        if (!validQuery(query)) {
            return StaffHomeOverviewResult.failure(StaffHomeOverviewError.INVALID_QUERY);
        }

        ReservationTodayViewResult reservations = reservationTodayViewService.getToday(new ReservationTodayViewQuery(
            query.tenantId(),
            query.storeId(),
            query.actorId(),
            query.actorType(),
            query.businessDate(),
            RESERVATION_STATUS_ALL
        ));
        if (!reservations.success()) {
            return StaffHomeOverviewResult.failure(mapReservationError(reservations.error()));
        }

        StoreScope scope = new StoreScope(new TenantId(query.tenantId()), new StoreId(query.storeId()));
        BusinessDate businessDate = new BusinessDate(reservations.businessDate());
        TableResourceListResult tableResources = tableResourceListService.listResources(new TableResourceListQuery(
            scope,
            null,
            null,
            true,
            businessDate
        ));
        if (!tableResources.success()) {
            return StaffHomeOverviewResult.failure(StaffHomeOverviewError.PERSISTENCE_ERROR);
        }

        try {
            List<QueueTicketOverviewMetric> queueMetrics =
                queueTicketRepository.findOverviewMetrics(scope, businessDate);
            return StaffHomeOverviewResult.success(new StaffHomeOverview(
                reservations.storeId(),
                reservations.businessDate(),
                reservations.storeTimezone(),
                reservationMetrics(reservations.items()),
                queueMetrics(queueMetrics),
                tableMetrics(tableResources.resources()),
                partySizeGroups(queueMetrics)
            ));
        } catch (RuntimeException exception) {
            return StaffHomeOverviewResult.failure(StaffHomeOverviewError.PERSISTENCE_ERROR);
        }
    }

    private static StaffHomeOverview.ReservationMetrics reservationMetrics(List<ReservationTodayViewItem> items) {
        return new StaffHomeOverview.ReservationMetrics(
            items.size(),
            sumReservationPartySize(items, null),
            countReservations(items, "arrived"),
            sumReservationPartySize(items, "arrived"),
            countReservations(items, "seated"),
            sumReservationPartySize(items, "seated"),
            countReservations(items, "cancelled")
        );
    }

    private static StaffHomeOverview.QueueMetrics queueMetrics(List<QueueTicketOverviewMetric> metrics) {
        return new StaffHomeOverview.QueueMetrics(
            countTickets(metrics, "waiting"),
            sumQueuePartySize(metrics, "waiting"),
            countTickets(metrics, "called"),
            sumQueuePartySize(metrics, "called"),
            countTickets(metrics, "seated"),
            countTickets(metrics, "skipped"),
            countTickets(metrics, "cancelled"),
            countTickets(metrics, "expired")
        );
    }

    private static StaffHomeOverview.TableMetrics tableMetrics(List<TableResourceItem> resources) {
        List<TableResourceItem> tables = resources.stream()
            .filter(resource -> RESOURCE_DINING_TABLE.equals(resource.resourceType()))
            .toList();
        return new StaffHomeOverview.TableMetrics(
            tables.size(),
            (int) tables.stream().filter(resource -> "available".equals(resource.status()) && resource.selectable()).count(),
            (int) tables.stream().filter(resource -> "reserved".equals(resource.status())).count(),
            (int) tables.stream().filter(resource -> "occupied".equals(resource.status())).count(),
            (int) tables.stream().filter(resource -> "cleaning".equals(resource.status())).count(),
            (int) resources.stream()
                .filter(resource -> RESOURCE_TABLE_GROUP.equals(resource.resourceType()))
                .filter(resource -> GROUP_TYPE_TEMPORARY.equals(resource.groupType()))
                .count()
        );
    }

    private static List<StaffHomeOverview.PartySizeGroupMetrics> partySizeGroups(List<QueueTicketOverviewMetric> metrics) {
        Map<String, MutablePartySizeGroup> groups = new LinkedHashMap<>();
        DEFAULT_PARTY_SIZE_GROUPS.forEach(label -> groups.put(label, new MutablePartySizeGroup(label)));

        metrics.stream()
            .filter(metric -> "waiting".equals(metric.status()) || "called".equals(metric.status()))
            .forEach(metric -> {
                String label = hasText(metric.partySizeGroup()) ? metric.partySizeGroup().trim() : "未分组";
                MutablePartySizeGroup group = groups.computeIfAbsent(label, MutablePartySizeGroup::new);
                group.groups += Math.toIntExact(metric.ticketCount());
                group.partySize += metric.partySizeTotal();
            });

        return groups.values().stream()
            .map(group -> new StaffHomeOverview.PartySizeGroupMetrics(group.label, group.groups, group.partySize))
            .toList();
    }

    private static StaffHomeOverviewError mapReservationError(ReservationTodayViewError error) {
        return switch (error) {
            case INVALID_BUSINESS_DATE -> StaffHomeOverviewError.INVALID_BUSINESS_DATE;
            case STORE_NOT_FOUND -> StaffHomeOverviewError.STORE_NOT_FOUND;
            case STORE_SCOPE_MISMATCH -> StaffHomeOverviewError.STORE_SCOPE_MISMATCH;
            case STORE_ACCESS_DENIED -> StaffHomeOverviewError.STORE_ACCESS_DENIED;
            case PERSISTENCE_ERROR -> StaffHomeOverviewError.PERSISTENCE_ERROR;
            default -> StaffHomeOverviewError.INVALID_QUERY;
        };
    }

    private static int countReservations(List<ReservationTodayViewItem> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.status())).count();
    }

    private static int sumReservationPartySize(List<ReservationTodayViewItem> items, String status) {
        return items.stream()
            .filter(item -> status == null || status.equals(item.status()))
            .mapToInt(ReservationTodayViewItem::partySize)
            .sum();
    }

    private static int countTickets(List<QueueTicketOverviewMetric> metrics, String status) {
        return Math.toIntExact(metrics.stream()
            .filter(metric -> status.equals(metric.status()))
            .mapToLong(QueueTicketOverviewMetric::ticketCount)
            .sum());
    }

    private static int sumQueuePartySize(List<QueueTicketOverviewMetric> metrics, String status) {
        return metrics.stream()
            .filter(metric -> status.equals(metric.status()))
            .mapToInt(QueueTicketOverviewMetric::partySizeTotal)
            .sum();
    }

    private static boolean validQuery(StaffHomeOverviewQuery query) {
        return query != null
            && query.tenantId() != null
            && query.storeId() != null
            && query.actorId() != null
            && hasText(query.actorType());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class MutablePartySizeGroup {
        private final String label;
        private int groups;
        private int partySize;

        private MutablePartySizeGroup(String label) {
            this.label = label;
        }
    }
}
