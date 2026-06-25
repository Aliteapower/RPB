package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceTarget;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.table.application.DiningTableResourceRow;
import com.rpb.reservation.table.application.TableResourceItem;
import com.rpb.reservation.table.application.TableResourceListError;
import com.rpb.reservation.table.application.TableResourceListQuery;
import com.rpb.reservation.table.application.TableResourceListResult;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TableResourceListApplicationService {
    private static final String DINING_TABLE_TYPE = "dining_table";
    private static final String TABLE_GROUP_TYPE = "table_group";
    private static final String STATUS_UNAVAILABLE = "status_unavailable";
    private static final String RESERVATION_PREASSIGNED = "reservation_preassigned";
    private static final String TEMPORARY_GROUP_MEMBER = "temporary_group_member";
    private static final String TEMPORARY_GROUP_TYPE = "temporary";
    private static final ZoneId DEFAULT_STORE_ZONE = ZoneId.of("Asia/Singapore");
    private static final Set<String> RESERVATION_PREASSIGNMENT_HOLD_STATUSES = Set.of("confirmed", "arrived");
    private static final Set<String> DATE_SCOPED_AVAILABLE_TABLE_STATUSES = Set.of(
        DiningTableStatus.AVAILABLE.code(),
        DiningTableStatus.LOCKED.code(),
        DiningTableStatus.RESERVED.code(),
        DiningTableStatus.OCCUPIED.code(),
        DiningTableStatus.CLEANING.code()
    );
    private static final Set<TableGroupStatus> DATE_SCOPED_AVAILABLE_GROUP_STATUSES = Set.of(
        TableGroupStatus.ACTIVE,
        TableGroupStatus.LOCKED,
        TableGroupStatus.OCCUPIED
    );

    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final CleaningRepositoryPort cleaningRepository;
    private final ReservationPreassignmentRepositoryPort preassignmentRepository;
    private final StoreRepositoryPort storeRepository;
    private final Clock clock;

    @Autowired
    public TableResourceListApplicationService(
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        SeatingRepositoryPort seatingRepository,
        CleaningRepositoryPort cleaningRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        StoreRepositoryPort storeRepository,
        Clock clock
    ) {
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.seatingRepository = seatingRepository;
        this.cleaningRepository = cleaningRepository;
        this.preassignmentRepository = preassignmentRepository;
        this.storeRepository = storeRepository;
        this.clock = clock;
    }

    public TableResourceListApplicationService(
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        SeatingRepositoryPort seatingRepository,
        CleaningRepositoryPort cleaningRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository
    ) {
        this(
            diningTableRepository,
            tableGroupRepository,
            seatingRepository,
            cleaningRepository,
            preassignmentRepository,
            null,
            Clock.systemUTC()
        );
    }

    public TableResourceListResult listResources(TableResourceListQuery query) {
        try {
            PartySize partySize = query.partySize() == null ? null : new PartySize(query.partySize());
            List<TableResourceItem> resources = new ArrayList<>();
            Map<ReservationResourceTarget, ReservationResourceAssignment> preassignedAssignments = preassignedAssignments(query);
            boolean liveOperationalDate = liveOperationalDate(query);
            String repositoryStatus = repositoryStatus(query, liveOperationalDate);
            String groupRepositoryStatus = groupRepositoryStatus(query, liveOperationalDate);
            BusinessWindow businessWindow = businessWindow(query);

            resources.addAll(
                diningTableRepository.findVisibleResourceRows(query.scope(), repositoryStatus, partySize)
                    .stream()
                    .map(table -> toDiningTableItem(query, table, preassignedAssignments, liveOperationalDate, businessWindow))
                    .toList()
            );

            if (query.includeGroups()) {
                resources.addAll(
                    tableGroupRepository.findVisibleGroups(
                        query.scope(),
                        groupRepositoryStatus,
                        partySize,
                        businessWindow.startAt(),
                        businessWindow.endAt()
                    )
                        .stream()
                        .map(group -> toTableGroupItem(query, group, preassignedAssignments, liveOperationalDate))
                        .toList()
                );
            }

            return TableResourceListResult.success(applyStatusFilter(query, resources));
        } catch (RuntimeException exception) {
            return TableResourceListResult.failure(TableResourceListError.PERSISTENCE_ERROR);
        }
    }

    private TableResourceItem toDiningTableItem(
        TableResourceListQuery query,
        DiningTableResourceRow table,
        Map<ReservationResourceTarget, ReservationResourceAssignment> preassignedAssignments,
        boolean liveOperationalDate,
        BusinessWindow businessWindow
    ) {
        ReservationResourceAssignment preassignment = preassignedAssignments.get(new ReservationResourceTarget(DINING_TABLE_TYPE, table.resourceId()));
        String baseStatus = effectiveDiningTableStatus(table.status(), liveOperationalDate);
        boolean preassigned = holdsTable(preassignment)
            && DiningTableStatus.AVAILABLE.code().equals(baseStatus);
        boolean temporaryGroupMember = !preassigned
            && DiningTableStatus.AVAILABLE.code().equals(baseStatus)
            && liveOperationalDate
            && !tableGroupRepository.findActiveTemporaryGroupsForTable(
                query.scope(),
                new TableId(table.resourceId()),
                businessWindow.startAt(),
                businessWindow.endAt()
            ).isEmpty();
        String status = preassigned ? DiningTableStatus.RESERVED.code() : baseStatus;
        boolean selectable = DiningTableStatus.AVAILABLE.code().equals(status) && !temporaryGroupMember;
        Seating currentSeating = currentSeating(status, DINING_TABLE_TYPE, table.resourceId(), query, liveOperationalDate);
        ReservationResourceAssignment visiblePreassignment = visiblePreassignment(preassignment, currentSeating, preassigned);

        return new TableResourceItem(
            DINING_TABLE_TYPE,
            null,
            table.resourceId(),
            table.code(),
            table.displayName(),
            table.areaName(),
            table.capacityMin(),
            table.capacityMax(),
            status,
            selectable,
            selectable ? null : preassigned ? RESERVATION_PREASSIGNED : temporaryGroupMember ? TEMPORARY_GROUP_MEMBER : STATUS_UNAVAILABLE,
            List.of(),
            currentSeatingId(currentSeating),
            currentCleaningId(DINING_TABLE_TYPE, table.resourceId(), query, liveOperationalDate),
            currentReservationId(currentSeating),
            currentPartySize(currentSeating),
            visiblePreassignment == null ? null : visiblePreassignment.reservationId(),
            visiblePreassignment == null ? null : visiblePreassignment.reservationCode(),
            visiblePreassignment == null ? null : visiblePreassignment.customerName(),
            visiblePreassignment == null ? null : visiblePreassignment.customerPhoneMasked(),
            visiblePreassignment == null ? null : visiblePreassignment.reservationStatus(),
            visiblePreassignment == null ? null : visiblePreassignment.partySize(),
            visiblePreassignment == null ? null : visiblePreassignment.reservedStartAt(),
            visiblePreassignment == null ? null : visiblePreassignment.reservedEndAt(),
            visiblePreassignment == null ? null : visiblePreassignment.resourceCode(),
            visiblePreassignment == null ? null : visiblePreassignment.queueTicketId(),
            visiblePreassignment == null ? null : visiblePreassignment.queueTicketNumber(),
            visiblePreassignment == null ? null : visiblePreassignment.queueTicketStatus()
        );
    }

    private TableResourceItem toTableGroupItem(
        TableResourceListQuery query,
        TableGroup group,
        Map<ReservationResourceTarget, ReservationResourceAssignment> preassignedAssignments,
        boolean liveOperationalDate
    ) {
        ReservationResourceAssignment preassignment = preassignedAssignments.get(new ReservationResourceTarget(TABLE_GROUP_TYPE, group.id().value()));
        TableGroupStatus baseStatus = effectiveTableGroupStatus(group.status(), liveOperationalDate);
        Seating currentSeating = activeOccupancy(TABLE_GROUP_TYPE, group.id().value(), query, liveOperationalDate);
        boolean occupied = currentSeating != null;
        boolean preassigned = !occupied
            && holdsTable(preassignment)
            && baseStatus == TableGroupStatus.ACTIVE;
        String status = occupied ? TableGroupStatus.OCCUPIED.code() : preassigned ? DiningTableStatus.RESERVED.code() : baseStatus.code();
        boolean selectable = !occupied && isSelectableGroup(group, baseStatus) && !preassigned;
        ReservationResourceAssignment visiblePreassignment = visiblePreassignment(preassignment, currentSeating, preassigned);

        return new TableResourceItem(
            TABLE_GROUP_TYPE,
            group.groupType(),
            group.id().value(),
            group.groupCode(),
            group.groupCode(),
            null,
            group.capacity().min(),
            group.capacity().max(),
            status,
            selectable,
            selectable ? null : preassigned ? RESERVATION_PREASSIGNED : STATUS_UNAVAILABLE,
            memberTableCodes(query, group),
            currentSeatingId(currentSeating),
            currentCleaningId(TABLE_GROUP_TYPE, group.id().value(), query, liveOperationalDate),
            currentReservationId(currentSeating),
            currentPartySize(currentSeating),
            visiblePreassignment == null ? null : visiblePreassignment.reservationId(),
            visiblePreassignment == null ? null : visiblePreassignment.reservationCode(),
            visiblePreassignment == null ? null : visiblePreassignment.customerName(),
            visiblePreassignment == null ? null : visiblePreassignment.customerPhoneMasked(),
            visiblePreassignment == null ? null : visiblePreassignment.reservationStatus(),
            visiblePreassignment == null ? null : visiblePreassignment.partySize(),
            visiblePreassignment == null ? null : visiblePreassignment.reservedStartAt(),
            visiblePreassignment == null ? null : visiblePreassignment.reservedEndAt(),
            visiblePreassignment == null ? null : visiblePreassignment.resourceCode(),
            visiblePreassignment == null ? null : visiblePreassignment.queueTicketId(),
            visiblePreassignment == null ? null : visiblePreassignment.queueTicketNumber(),
            visiblePreassignment == null ? null : visiblePreassignment.queueTicketStatus()
        );
    }

    private Map<ReservationResourceTarget, ReservationResourceAssignment> preassignedAssignments(TableResourceListQuery query) {
        if (query.businessDate() == null) {
            return Map.of();
        }
        return preassignmentRepository.findActiveResourceAssignmentsForDate(query.scope(), query.businessDate()).stream()
            .collect(Collectors.toMap(
                ReservationResourceAssignment::target,
                Function.identity(),
                (first, ignored) -> first
            ));
    }

    private static boolean holdsTable(ReservationResourceAssignment preassignment) {
        return preassignment != null
            && RESERVATION_PREASSIGNMENT_HOLD_STATUSES.contains(preassignment.reservationStatus());
    }

    private static ReservationResourceAssignment visiblePreassignment(
        ReservationResourceAssignment preassignment,
        Seating currentSeating,
        boolean holdsResource
    ) {
        if (preassignment == null) {
            return null;
        }
        if (holdsResource) {
            return preassignment;
        }
        if (currentSeating == null) {
            return null;
        }
        if ("reservation".equals(currentSeating.sourceType())
            && preassignment.reservationId().equals(currentSeating.sourceId())) {
            return preassignment;
        }
        if ("queue_ticket".equals(currentSeating.sourceType())
            && preassignment.queueTicketId() != null
            && preassignment.queueTicketId().equals(currentSeating.sourceId())) {
            return preassignment;
        }
        return null;
    }

    private static String repositoryStatus(TableResourceListQuery query, boolean liveOperationalDate) {
        if (!liveOperationalDate) {
            return null;
        }
        if (query.businessDate() != null && DiningTableStatus.RESERVED.code().equals(query.status())) {
            return null;
        }
        return query.status();
    }

    private static String groupRepositoryStatus(TableResourceListQuery query, boolean liveOperationalDate) {
        if (liveOperationalDate && TableGroupStatus.OCCUPIED.code().equals(query.status())) {
            return null;
        }
        return repositoryStatus(query, liveOperationalDate);
    }

    private static List<TableResourceItem> applyStatusFilter(
        TableResourceListQuery query,
        List<TableResourceItem> resources
    ) {
        if (query.status() == null) {
            return resources;
        }
        return resources.stream()
            .filter(resource -> query.status().equals(resource.status()))
            .toList();
    }

    private Seating currentSeating(String status, String resourceType, UUID resourceId, TableResourceListQuery query, boolean liveOperationalDate) {
        if (!liveOperationalDate) {
            return null;
        }
        if (!DiningTableStatus.OCCUPIED.code().equals(status) && !"occupied".equals(status)) {
            return null;
        }

        return activeOccupancy(resourceType, resourceId, query, liveOperationalDate);
    }

    private Seating activeOccupancy(String resourceType, UUID resourceId, TableResourceListQuery query, boolean liveOperationalDate) {
        if (!liveOperationalDate) {
            return null;
        }
        return seatingRepository.findActiveOccupancy(query.scope(), resourceType, resourceId)
            .filter(seating -> seating.status() == SeatingStatus.OCCUPIED)
            .orElse(null);
    }

    private static UUID currentSeatingId(Seating seating) {
        return seating == null ? null : seating.id().value();
    }

    private static UUID currentReservationId(Seating seating) {
        return seating != null && "reservation".equals(seating.sourceType()) ? seating.sourceId() : null;
    }

    private static Integer currentPartySize(Seating seating) {
        return seating == null ? null : seating.partySizeSnapshot().value();
    }

    private UUID currentCleaningId(String resourceType, UUID resourceId, TableResourceListQuery query, boolean liveOperationalDate) {
        if (!liveOperationalDate) {
            return null;
        }
        return cleaningRepository.findActiveByResource(query.scope(), resourceType, resourceId)
            .map(cleaning -> cleaning.id().value())
            .orElse(null);
    }

    private boolean liveOperationalDate(TableResourceListQuery query) {
        if (query.businessDate() == null) {
            return true;
        }

        return query.businessDate().value().equals(currentBusinessDate(query));
    }

    private LocalDate currentBusinessDate(TableResourceListQuery query) {
        return LocalDate.now(clock.withZone(storeZoneId(query)));
    }

    private BusinessWindow businessWindow(TableResourceListQuery query) {
        ZoneId zoneId = storeZoneId(query);
        LocalDate businessDate = query.businessDate() == null
            ? LocalDate.now(clock.withZone(zoneId))
            : query.businessDate().value();
        return new BusinessWindow(
            new BusinessDate(businessDate),
            businessDate.atStartOfDay(zoneId).toOffsetDateTime(),
            businessDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime()
        );
    }

    private ZoneId storeZoneId(TableResourceListQuery query) {
        return storeRepository == null
            ? DEFAULT_STORE_ZONE
            : storeRepository.findById(query.scope())
                .map(Store::timezone)
                .map(TableResourceListApplicationService::zoneId)
                .orElse(DEFAULT_STORE_ZONE);
    }

    private static String effectiveDiningTableStatus(String status, boolean liveOperationalDate) {
        if (liveOperationalDate || !DATE_SCOPED_AVAILABLE_TABLE_STATUSES.contains(status)) {
            return status;
        }
        return DiningTableStatus.AVAILABLE.code();
    }

    private static TableGroupStatus effectiveTableGroupStatus(TableGroupStatus status, boolean liveOperationalDate) {
        if (liveOperationalDate || !DATE_SCOPED_AVAILABLE_GROUP_STATUSES.contains(status)) {
            return status;
        }
        return TableGroupStatus.ACTIVE;
    }

    private static boolean isSelectableGroup(TableGroup group, TableGroupStatus status) {
        return status == TableGroupStatus.ACTIVE
            || (TEMPORARY_GROUP_TYPE.equals(group.groupType()) && status == TableGroupStatus.CREATED);
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private List<String> memberTableCodes(TableResourceListQuery query, TableGroup group) {
        return tableGroupRepository.findActiveMembers(query.scope(), group.id())
            .stream()
            .map(TableGroupMember::tableId)
            .map(tableId -> diningTableRepository.findById(query.scope(), tableId))
            .flatMap(java.util.Optional::stream)
            .map(DiningTable::tableCode)
            .toList();
    }

    private record BusinessWindow(BusinessDate businessDate, OffsetDateTime startAt, OffsetDateTime endAt) {
    }
}
