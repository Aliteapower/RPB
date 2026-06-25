package com.rpb.reservation.table.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.persistence.entity.TableGroupEntity;
import com.rpb.reservation.table.persistence.entity.TableGroupMemberEntity;
import com.rpb.reservation.table.persistence.mapper.TableGroupMapper;
import com.rpb.reservation.table.persistence.repository.TableGroupJpaRepository;
import com.rpb.reservation.table.persistence.repository.TableGroupMemberJpaRepository;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TableGroupPersistenceAdapter implements TableGroupRepositoryPort {

    private final TableGroupJpaRepository groupRepository;
    private final TableGroupMemberJpaRepository memberRepository;
    private final TableGroupMapper mapper;

    public TableGroupPersistenceAdapter(
        TableGroupJpaRepository groupRepository,
        TableGroupMemberJpaRepository memberRepository,
        TableGroupMapper mapper
    ) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId) {
        return groupRepository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            tableGroupId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId) {
        return memberRepository.findByTenantIdAndStoreIdAndTableGroupIdAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            tableGroupId.value()
        ).stream().map(this::toMemberDomain).toList();
    }

    @Override
    public List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId) {
        return memberRepository.findActiveMembersForTable(
            scope.tenantId().value(),
            scope.storeId().value(),
            tableId.value()
        ).stream()
            .map(TableGroupMemberEntity::getTableGroupId)
            .distinct()
            .map(groupId -> findById(scope, new TableGroupId(groupId)))
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public List<TableGroup> findActiveTemporaryGroupsForTable(StoreScope scope, TableId tableId) {
        return memberRepository.findActiveTemporaryMembersForTable(
            scope.tenantId().value(),
            scope.storeId().value(),
            tableId.value()
        ).stream()
            .map(TableGroupMemberEntity::getTableGroupId)
            .distinct()
            .map(groupId -> findById(scope, new TableGroupId(groupId)))
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public List<TableGroup> findActiveTemporaryGroupsForTable(
        StoreScope scope,
        TableId tableId,
        OffsetDateTime businessStartAt,
        OffsetDateTime businessEndAt
    ) {
        if (businessStartAt == null || businessEndAt == null) {
            return findActiveTemporaryGroupsForTable(scope, tableId);
        }
        return memberRepository.findActiveTemporaryMembersForTableInBusinessWindow(
            scope.tenantId().value(),
            scope.storeId().value(),
            tableId.value(),
            businessStartAt,
            businessEndAt
        ).stream()
            .map(TableGroupMemberEntity::getTableGroupId)
            .distinct()
            .map(groupId -> findById(scope, new TableGroupId(groupId)))
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
        return groupRepository.findAvailableCandidates(
            scope.tenantId().value(),
            scope.storeId().value(),
            partySize.value()
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<TableGroup> findVisibleGroups(StoreScope scope, String status, PartySize partySize) {
        UUID tenantId = scope.tenantId().value();
        UUID storeId = scope.storeId().value();
        List<TableGroupEntity> entities;

        if (status != null && partySize != null) {
            entities = groupRepository.findVisibleGroupsByStatusAndPartySize(
                tenantId,
                storeId,
                status,
                partySize.value()
            );
        } else if (status != null) {
            entities = groupRepository.findVisibleGroupsByStatus(tenantId, storeId, status);
        } else if (partySize != null) {
            entities = groupRepository.findVisibleGroupsByPartySize(tenantId, storeId, partySize.value());
        } else {
            entities = groupRepository.findVisibleGroupsWithoutFilters(tenantId, storeId);
        }

        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<TableGroup> findVisibleGroups(
        StoreScope scope,
        String status,
        PartySize partySize,
        OffsetDateTime businessStartAt,
        OffsetDateTime businessEndAt
    ) {
        if (businessStartAt == null || businessEndAt == null) {
            return findVisibleGroups(scope, status, partySize);
        }
        UUID tenantId = scope.tenantId().value();
        UUID storeId = scope.storeId().value();
        List<TableGroupEntity> entities;

        if (status != null && partySize != null) {
            entities = groupRepository.findVisibleGroupsByStatusAndPartySizeForBusinessWindow(
                tenantId,
                storeId,
                status,
                partySize.value(),
                businessStartAt,
                businessEndAt
            );
        } else if (status != null) {
            entities = groupRepository.findVisibleGroupsByStatusForBusinessWindow(
                tenantId,
                storeId,
                status,
                businessStartAt,
                businessEndAt
            );
        } else if (partySize != null) {
            entities = groupRepository.findVisibleGroupsByPartySizeForBusinessWindow(
                tenantId,
                storeId,
                partySize.value(),
                businessStartAt,
                businessEndAt
            );
        } else {
            entities = groupRepository.findVisibleGroupsWithoutFiltersForBusinessWindow(
                tenantId,
                storeId,
                businessStartAt,
                businessEndAt
            );
        }

        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public TableGroup save(StoreScope scope, TableGroup tableGroup) {
        TableGroupEntity mapped = mapper.toEntity(tableGroup);
        TableGroupEntity entity = groupRepository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            tableGroup.id().value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(existing -> existingEntity(mapped, existing))
            .orElseGet(() -> newEntity(mapped));
        return mapper.toDomain(groupRepository.save(entity));
    }

    @Override
    public TableGroupMember saveMember(StoreScope scope, TableGroupMember member) {
        OffsetDateTime now = OffsetDateTime.now();
        TableGroupMemberEntity entity = TableGroupMemberEntity.of(
            member.id(),
            scope.tenantId().value(),
            scope.storeId().value(),
            member.tableGroupId().value(),
            member.tableId().value(),
            member.memberRole(),
            now,
            null
        );
        return toMemberDomain(memberRepository.save(entity));
    }

    @Override
    public boolean existsActiveGroupCode(StoreScope scope, String groupCode) {
        return groupRepository.existsByTenantIdAndStoreIdAndGroupCodeAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            groupCode
        );
    }

    @Override
    public void softDeleteGroupAndMembers(StoreScope scope, TableGroupId tableGroupId, OffsetDateTime deletedAt) {
        memberRepository.softDeleteMembersForGroup(
            scope.tenantId().value(),
            scope.storeId().value(),
            tableGroupId.value(),
            deletedAt
        );
        groupRepository.softDeleteGroup(
            scope.tenantId().value(),
            scope.storeId().value(),
            tableGroupId.value(),
            deletedAt
        );
    }

    private TableGroupMember toMemberDomain(TableGroupMemberEntity entity) {
        return new TableGroupMember(
            entity.getId(),
            new StoreScope(new TenantId(entity.getTenantId()), entity.getStoreId()),
            new TableGroupId(entity.getTableGroupId()),
            new TableId(entity.getTableId()),
            entity.getMemberRole()
        );
    }

    private static TableGroupEntity existingEntity(TableGroupEntity mapped, TableGroupEntity existing) {
        return TableGroupEntity.of(
            mapped.getId(),
            mapped.getTenantId(),
            mapped.getStoreId(),
            mapped.getGroupCode(),
            mapped.getGroupType(),
            mapped.getStatus(),
            mapped.getDisplayName(),
            mapped.getCapacityMin(),
            mapped.getCapacityMax(),
            existing.getActiveFromAt(),
            existing.getActiveUntilAt(),
            existing.getCreatedAt(),
            mapped.getUpdatedAt(),
            existing.getDeletedAt(),
            existing.getVersion()
        );
    }

    private static TableGroupEntity newEntity(TableGroupEntity mapped) {
        return TableGroupEntity.of(
            mapped.getId(),
            mapped.getTenantId(),
            mapped.getStoreId(),
            mapped.getGroupCode(),
            mapped.getGroupType(),
            mapped.getStatus(),
            mapped.getDisplayName(),
            mapped.getCapacityMin(),
            mapped.getCapacityMax(),
            mapped.getActiveFromAt(),
            mapped.getActiveUntilAt(),
            mapped.getCreatedAt(),
            mapped.getUpdatedAt(),
            mapped.getDeletedAt(),
            null
        );
    }
}
