package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.value.PartySize;
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
import org.springframework.stereotype.Service;

@Service
public class TableResourceListApplicationService {
    private static final String DINING_TABLE_TYPE = "dining_table";
    private static final String TABLE_GROUP_TYPE = "table_group";
    private static final String STATUS_UNAVAILABLE = "status_unavailable";

    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;

    public TableResourceListApplicationService(
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository
    ) {
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
    }

    public TableResourceListResult listResources(TableResourceListQuery query) {
        try {
            PartySize partySize = query.partySize() == null ? null : new PartySize(query.partySize());
            List<TableResourceItem> resources = new ArrayList<>();

            resources.addAll(
                diningTableRepository.findVisibleResources(query.scope(), query.status(), partySize)
                    .stream()
                    .map(this::toDiningTableItem)
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

    private TableResourceItem toDiningTableItem(DiningTable table) {
        boolean selectable = table.status() == DiningTableStatus.AVAILABLE;

        return new TableResourceItem(
            DINING_TABLE_TYPE,
            table.id().value(),
            table.tableCode(),
            table.tableCode(),
            null,
            table.capacity().min(),
            table.capacity().max(),
            table.status().code(),
            selectable,
            selectable ? null : STATUS_UNAVAILABLE,
            List.of()
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
            memberTableCodes(query, group)
        );
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
