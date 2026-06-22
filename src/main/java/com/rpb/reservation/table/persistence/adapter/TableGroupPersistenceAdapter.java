package com.rpb.reservation.table.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
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
    public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
        return groupRepository.findAvailableCandidates(
            scope.tenantId().value(),
            scope.storeId().value(),
            partySize.value()
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public TableGroup save(StoreScope scope, TableGroup tableGroup) {
        return mapper.toDomain(groupRepository.save(mapper.toEntity(tableGroup)));
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

    private TableGroupMember toMemberDomain(TableGroupMemberEntity entity) {
        return new TableGroupMember(
            entity.getId(),
            new StoreScope(new TenantId(entity.getTenantId()), entity.getStoreId()),
            new TableGroupId(entity.getTableGroupId()),
            new TableId(entity.getTableId()),
            entity.getMemberRole()
        );
    }
}
