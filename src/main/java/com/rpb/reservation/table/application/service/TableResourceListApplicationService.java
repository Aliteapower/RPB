package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TableResourceListApplicationService {
    private static final String DINING_TABLE_TYPE = "dining_table";
    private static final String TABLE_GROUP_TYPE = "table_group";
    private static final String STATUS_UNAVAILABLE = "status_unavailable";

    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final CleaningRepositoryPort cleaningRepository;

    public TableResourceListApplicationService(
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        SeatingRepositoryPort seatingRepository,
        CleaningRepositoryPort cleaningRepository
    ) {
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.seatingRepository = seatingRepository;
        this.cleaningRepository = cleaningRepository;
    }

    public TableResourceListResult listResources(TableResourceListQuery query) {
        try {
            PartySize partySize = query.partySize() == null ? null : new PartySize(query.partySize());
            List<TableResourceItem> resources = new ArrayList<>();

            resources.addAll(
                diningTableRepository.findVisibleResourceRows(query.scope(), query.status(), partySize)
                    .stream()
                    .map(table -> toDiningTableItem(query, table))
                    .toList()
            );

            if (query.includeGroups()) {
                resources.addAll(
                    tableGroupRepository.findVisibleGroups(query.scope(), query.status(), partySize)
                        .stream()
                        .map(group -> toTableGroupItem(query, group))
                        .toList()
                );
            }

            return TableResourceListResult.success(resources);
        } catch (RuntimeException exception) {
            return TableResourceListResult.failure(TableResourceListError.PERSISTENCE_ERROR);
        }
    }

    private TableResourceItem toDiningTableItem(TableResourceListQuery query, DiningTableResourceRow table) {
        boolean selectable = DiningTableStatus.AVAILABLE.code().equals(table.status());

        return new TableResourceItem(
            DINING_TABLE_TYPE,
            table.resourceId(),
            table.code(),
            table.displayName(),
            table.areaName(),
            table.capacityMin(),
            table.capacityMax(),
            table.status(),
            selectable,
            selectable ? null : STATUS_UNAVAILABLE,
            List.of(),
            currentSeatingId(table.status(), DINING_TABLE_TYPE, table.resourceId(), query),
            currentCleaningId(DINING_TABLE_TYPE, table.resourceId(), query)
        );
    }

    private TableResourceItem toTableGroupItem(TableResourceListQuery query, TableGroup group) {
        boolean selectable = group.status() == TableGroupStatus.ACTIVE;

        return new TableResourceItem(
            TABLE_GROUP_TYPE,
            group.id().value(),
            group.groupCode(),
            group.groupCode(),
            null,
            group.capacity().min(),
            group.capacity().max(),
            group.status().code(),
            selectable,
            selectable ? null : STATUS_UNAVAILABLE,
            memberTableCodes(query, group),
            currentSeatingId(group.status().code(), TABLE_GROUP_TYPE, group.id().value(), query),
            currentCleaningId(TABLE_GROUP_TYPE, group.id().value(), query)
        );
    }

    private UUID currentSeatingId(String status, String resourceType, UUID resourceId, TableResourceListQuery query) {
        if (!DiningTableStatus.OCCUPIED.code().equals(status) && !"occupied".equals(status)) {
            return null;
        }

        return seatingRepository.findActiveOccupancy(query.scope(), resourceType, resourceId)
            .map(seating -> seating.id().value())
            .orElse(null);
    }

    private UUID currentCleaningId(String resourceType, UUID resourceId, TableResourceListQuery query) {
        return cleaningRepository.findActiveByResource(query.scope(), resourceType, resourceId)
            .map(cleaning -> cleaning.id().value())
            .orElse(null);
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
}
