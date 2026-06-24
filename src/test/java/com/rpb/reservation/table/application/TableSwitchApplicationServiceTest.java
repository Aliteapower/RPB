package com.rpb.reservation.table.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.command.SwitchTableCommand;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.application.service.TableSwitchApplicationService;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableSwitchApplicationServiceTest {

    @Test
    void switchesOccupiedSeatingFromTableToAvailableTableAndReleasesPreviousTableForImmediateUse() {
        Scenario scenario = Scenario.readyTableToTable();

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetTable());

        assertThat(result.success()).isTrue();
        assertThat(result.seatingId()).isEqualTo(scenario.seating.id().value());
        assertThat(result.fromResourceType()).isEqualTo("dining_table");
        assertThat(result.fromResourceId()).isEqualTo(scenario.sourceTable.id().value());
        assertThat(result.fromResourceStatus()).isEqualTo("available");
        assertThat(result.toResourceType()).isEqualTo("dining_table");
        assertThat(result.toResourceId()).isEqualTo(scenario.targetTable.id().value());
        assertThat(result.toResourceStatus()).isEqualTo("occupied");
        assertThat(result.cleaningId()).isNull();
        assertThat(result.seatingStatus()).isEqualTo("occupied");
        assertThat(scenario.cleaningRepository.saved).isEmpty();
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsExactly(DiningTableStatus.AVAILABLE, DiningTableStatus.OCCUPIED);
        assertThat(scenario.seatingRepository.savedResources).extracting(SeatingResource::status)
            .containsExactly("released", "active");
        assertThat(scenario.seatingRepository.savedResources.getLast().resourceId()).isEqualTo(scenario.targetTable.id().value());
        assertThat(scenario.idempotencyRepository.completed.getFirst().action()).isEqualTo("switch_table");
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .containsExactly("table.switch.completed", "table.available", "table.occupied");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::transitionCode)
            .contains("table.switch.release_source", "dining_table.available", "table.switch.assign_target", "dining_table.occupy");
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode)
            .contains("table.switch.completed");
    }

    @Test
    void switchesOccupiedSeatingFromTableToAvailableGroupAndOccupiesAllMemberTables() {
        Scenario scenario = Scenario.readyTableToGroup();

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetGroup());

        assertThat(result.success()).isTrue();
        assertThat(result.toResourceType()).isEqualTo("table_group");
        assertThat(result.toResourceId()).isEqualTo(scenario.targetGroup.id().value());
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::id)
            .containsExactly(scenario.sourceTable.id(), scenario.groupTableA.id(), scenario.groupTableB.id());
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsExactly(DiningTableStatus.AVAILABLE, DiningTableStatus.OCCUPIED, DiningTableStatus.OCCUPIED);
        assertThat(scenario.seatingRepository.savedResources.getLast().resourceType()).isEqualTo("table_group");
    }

    @Test
    void groupSwitchUsesConfiguredGroupCapacityInsteadOfSingleMemberTableCapacity() {
        Scenario scenario = Scenario.readyTableToGroup();
        scenario.seatingRepository.seatings.put(
            scenario.seating.id().value(),
            new Seating(
                scenario.seating.id(),
                scenario.scope,
                scenario.seating.sourceType(),
                scenario.seating.sourceId(),
                scenario.seating.seatingCode(),
                scenario.seating.manualOverrideReasonCode(),
                scenario.seating.note(),
                new PartySize(8),
                SeatingStatus.OCCUPIED
            )
        );

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetGroup());

        assertThat(result.success()).isTrue();
        assertThat(result.toResourceType()).isEqualTo("table_group");
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsExactly(DiningTableStatus.AVAILABLE, DiningTableStatus.OCCUPIED, DiningTableStatus.OCCUPIED);
    }

    @Test
    void switchAllowsAvailableTableEvenWhenPartySizeIsBelowConfiguredMinimum() {
        Scenario scenario = Scenario.readyTableToTable();
        scenario.seatingRepository.seatings.put(
            scenario.seating.id().value(),
            new Seating(
                scenario.seating.id(),
                scenario.scope,
                scenario.seating.sourceType(),
                scenario.seating.sourceId(),
                scenario.seating.seatingCode(),
                scenario.seating.manualOverrideReasonCode(),
                scenario.seating.note(),
                new PartySize(2),
                SeatingStatus.OCCUPIED
            )
        );
        scenario.diningTableRepository.tables.put(
            scenario.targetTable.id().value(),
            new DiningTable(
                scenario.targetTable.id(),
                scenario.scope,
                scenario.areaId,
                scenario.targetTable.tableCode(),
                new CapacityRange(4, 6),
                DiningTableStatus.AVAILABLE,
                true
            )
        );

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetTable());

        assertThat(result.success()).isTrue();
        assertThat(result.toResourceId()).isEqualTo(scenario.targetTable.id().value());
        assertThat(scenario.cleaningRepository.saved).isEmpty();
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsExactly(DiningTableStatus.AVAILABLE, DiningTableStatus.OCCUPIED);
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutDuplicateMutation() {
        Scenario scenario = Scenario.readyTableToTable();
        String hash = TableSwitchApplicationService.requestHash(scenario.commandToTargetTable());
        scenario.idempotencyRepository.existing = completedRecord(
            hash,
            scenario.seating.id().value(),
            scenario.sourceTable.id().value(),
            scenario.targetTable.id().value()
        );

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetTable());

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.fromResourceStatus()).isEqualTo("available");
        assertThat(result.cleaningId()).isNull();
        assertThat(scenario.cleaningRepository.saved).isEmpty();
        assertThat(scenario.diningTableRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestIsRejectedWithoutMutation() {
        Scenario scenario = Scenario.readyTableToTable();
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            scenario.seating.id().value(),
            scenario.sourceTable.id().value(),
            scenario.targetTable.id().value()
        );

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetTable());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TableSwitchError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.cleaningRepository.saved).isEmpty();
        assertThat(scenario.diningTableRepository.saved).isEmpty();
    }

    @Test
    void targetOccupiedTableIsRejectedBeforeResourceSwitch() {
        Scenario scenario = Scenario.readyTableToTable();
        scenario.diningTableRepository.tables.put(
            scenario.targetTable.id().value(),
            scenario.table(scenario.targetTable.id(), "A02", DiningTableStatus.OCCUPIED)
        );

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetTable());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TableSwitchError.TABLE_NOT_AVAILABLE);
        assertThat(scenario.seatingRepository.savedResources).isEmpty();
        assertThat(scenario.cleaningRepository.saved).isEmpty();
    }

    @Test
    void sourceSeatingMustRemainOccupied() {
        Scenario scenario = Scenario.readyTableToTable();
        scenario.seatingRepository.seatings.put(
            scenario.seating.id().value(),
            new Seating(
                scenario.seating.id(),
                scenario.scope,
                scenario.seating.sourceType(),
                scenario.seating.sourceId(),
                scenario.seating.seatingCode(),
                scenario.seating.manualOverrideReasonCode(),
                scenario.seating.note(),
                scenario.seating.partySizeSnapshot(),
                SeatingStatus.CLEANING_TRIGGERED
            )
        );

        TableSwitchResult result = scenario.service().switchTable(scenario.commandToTargetTable());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(TableSwitchError.SEATING_NOT_OCCUPIED);
        assertThat(scenario.cleaningRepository.saved).isEmpty();
    }

    private static IdempotencyRecord completedRecord(
        String requestHash,
        UUID seatingId,
        UUID fromResourceId,
        UUID toResourceId
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-switch"),
            "staff",
            "switch_table",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "seating",
            seatingId,
            """
                {"seatingId":"%s","fromResourceType":"dining_table","fromResourceId":"%s","fromResourceStatus":"available","toResourceType":"dining_table","toResourceId":"%s","toResourceStatus":"occupied","cleaningId":null,"seatingStatus":"occupied"}
                """.formatted(seatingId, fromResourceId, toResourceId)
        );
    }

    private static final class Scenario {
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final UUID areaId = UUID.randomUUID();
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "zh-CN", "active");
        final StorePolicy policy = new StorePolicy(UUID.randomUUID(), scope, 15, 3, 90, "same_group_tail", "default");
        final DiningTable sourceTable = table(new TableId(UUID.randomUUID()), "A01", DiningTableStatus.OCCUPIED);
        final DiningTable targetTable = table(new TableId(UUID.randomUUID()), "A02", DiningTableStatus.AVAILABLE);
        final DiningTable groupTableA = table(new TableId(UUID.randomUUID()), "B01", DiningTableStatus.AVAILABLE);
        final DiningTable groupTableB = table(new TableId(UUID.randomUUID()), "B02", DiningTableStatus.AVAILABLE);
        final TableGroup targetGroup = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "VIP-1",
            "fixed",
            new CapacityRange(2, 8),
            TableGroupStatus.ACTIVE
        );
        final Seating seating = new Seating(
            new SeatingId(UUID.randomUUID()),
            scope,
            "reservation",
            UUID.randomUUID(),
            "S-1",
            new PartySize(4),
            SeatingStatus.OCCUPIED
        );
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository();
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository();
        final FakeTableLockRepository tableLockRepository = new FakeTableLockRepository();
        final FakeSeatingRepository seatingRepository = new FakeSeatingRepository();
        final FakeCleaningRepository cleaningRepository = new FakeCleaningRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();

        static Scenario readyTableToTable() {
            Scenario scenario = new Scenario();
            scenario.seedBase();
            scenario.diningTableRepository.tables.put(scenario.targetTable.id().value(), scenario.targetTable);
            return scenario;
        }

        static Scenario readyTableToGroup() {
            Scenario scenario = new Scenario();
            scenario.seedBase();
            scenario.tableGroupRepository.groups.put(scenario.targetGroup.id().value(), scenario.targetGroup);
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.targetGroup.id(),
                scenario.groupTableA.id(),
                "primary"
            ));
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.targetGroup.id(),
                scenario.groupTableB.id(),
                "secondary"
            ));
            scenario.diningTableRepository.tables.put(scenario.groupTableA.id().value(), scenario.groupTableA);
            scenario.diningTableRepository.tables.put(scenario.groupTableB.id().value(), scenario.groupTableB);
            return scenario;
        }

        void seedBase() {
            diningTableRepository.tables.put(sourceTable.id().value(), sourceTable);
            seatingRepository.seatings.put(seating.id().value(), seating);
            seatingRepository.resources.put(
                UUID.randomUUID(),
                new SeatingResource(
                    UUID.randomUUID(),
                    scope,
                    seating.id(),
                    "dining_table",
                    sourceTable.id().value(),
                    "active"
                )
            );
        }

        TableSwitchApplicationService service() {
            return new TableSwitchApplicationService(
                storeRepository,
                diningTableRepository,
                tableGroupRepository,
                tableLockRepository,
                seatingRepository,
                cleaningRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository
            );
        }

        SwitchTableCommand commandToTargetTable() {
            return new SwitchTableCommand(
                tenantId.value(),
                storeId.value(),
                seating.id().value(),
                targetTable.id().value(),
                null,
                "idem-switch",
                actorId,
                "staff",
                "guest_requested",
                "move away from entrance"
            );
        }

        SwitchTableCommand commandToTargetGroup() {
            return new SwitchTableCommand(
                tenantId.value(),
                storeId.value(),
                seating.id().value(),
                null,
                targetGroup.id().value(),
                "idem-switch",
                actorId,
                "staff",
                "guest_requested",
                "needs bigger table"
            );
        }

        DiningTable table(TableId id, String code, DiningTableStatus status) {
            return new DiningTable(id, scope, areaId, code, new CapacityRange(2, 6), status, true);
        }
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        private final Scenario scenario;

        FakeStoreRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<Store> findById(StoreScope scope) {
            return scenario.scope.equals(scope) ? Optional.of(scenario.store) : Optional.empty();
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return scenario.scope.equals(scope) ? Optional.of(scenario.policy) : Optional.empty();
        }

        @Override
        public Store save(StoreScope scope, Store store) {
            return store;
        }

        @Override
        public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
            return policy;
        }
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        final Map<UUID, DiningTable> tables = new HashMap<>();
        final List<DiningTable> saved = new ArrayList<>();

        @Override
        public Optional<DiningTable> findById(StoreScope scope, TableId tableId) {
            return Optional.ofNullable(tables.get(tableId.value())).filter(table -> table.scope().equals(scope));
        }

        @Override
        public List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId) {
            return tables.values().stream().filter(table -> table.scope().equals(scope) && table.areaId().equals(areaId)).toList();
        }

        @Override
        public List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return List.of();
        }

        @Override
        public DiningTable save(StoreScope scope, DiningTable table) {
            saved.add(table);
            tables.put(table.id().value(), table);
            return table;
        }
    }

    private static final class FakeTableGroupRepository implements TableGroupRepositoryPort {
        final Map<UUID, TableGroup> groups = new HashMap<>();
        final List<TableGroupMember> members = new ArrayList<>();

        @Override
        public Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId) {
            return Optional.ofNullable(groups.get(tableGroupId.value())).filter(group -> group.scope().equals(scope));
        }

        @Override
        public List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId) {
            return members.stream().filter(member -> member.scope().equals(scope) && member.tableGroupId().equals(tableGroupId)).toList();
        }

        @Override
        public List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId) {
            return List.of();
        }

        @Override
        public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return List.of();
        }

        @Override
        public TableGroup save(StoreScope scope, TableGroup tableGroup) {
            groups.put(tableGroup.id().value(), tableGroup);
            return tableGroup;
        }

        @Override
        public TableGroupMember saveMember(StoreScope scope, TableGroupMember member) {
            members.add(member);
            return member;
        }
    }

    private static final class FakeTableLockRepository implements TableLockRepositoryPort {
        boolean hasConflict;

        @Override
        public Optional<TableLock> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveConflict(StoreScope scope, String resourceType, UUID resourceId, OffsetDateTime at) {
            return hasConflict;
        }

        @Override
        public TableLock save(StoreScope scope, TableLock lock) {
            return lock;
        }

        @Override
        public TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt) {
            return null;
        }
    }

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        final Map<UUID, Seating> seatings = new HashMap<>();
        final Map<UUID, SeatingResource> resources = new HashMap<>();
        final List<SeatingResource> savedResources = new ArrayList<>();

        @Override
        public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
            return Optional.ofNullable(seatings.get(seatingId.value())).filter(seating -> seating.scope().equals(scope));
        }

        @Override
        public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
            return seatings.values().stream()
                .filter(seating -> seating.scope().equals(scope) && seating.sourceType().equals(sourceType) && sourceId.equals(seating.sourceId()))
                .findFirst();
        }

        @Override
        public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return resources.values().stream()
                .anyMatch(resource -> resource.scope().equals(scope)
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId)
                    && "active".equals(resource.status()));
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return resources.values().stream()
                .filter(resource -> resource.scope().equals(scope)
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId)
                    && "active".equals(resource.status()))
                .findFirst()
                .flatMap(resource -> findById(scope, resource.seatingId()));
        }

        @Override
        public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
            return resources.values().stream()
                .filter(resource -> resource.scope().equals(scope)
                    && resource.seatingId().equals(seatingId)
                    && "active".equals(resource.status()))
                .findFirst();
        }

        @Override
        public Seating save(StoreScope scope, Seating seating) {
            seatings.put(seating.id().value(), seating);
            return seating;
        }

        @Override
        public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
            savedResources.add(resource);
            resources.put(resource.id(), resource);
            return resource;
        }
    }

    private static final class FakeCleaningRepository implements CleaningRepositoryPort {
        final Map<UUID, Cleaning> cleanings = new HashMap<>();
        final List<Cleaning> saved = new ArrayList<>();
        Cleaning activeByResource;

        @Override
        public Optional<Cleaning> findById(StoreScope scope, CleaningId cleaningId) {
            return Optional.ofNullable(cleanings.get(cleaningId.value())).filter(cleaning -> cleaning.scope().equals(scope));
        }

        @Override
        public Optional<Cleaning> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.ofNullable(activeByResource)
                .filter(cleaning -> cleaning.scope().equals(scope)
                    && cleaning.resourceType().equals(resourceType)
                    && cleaning.resourceId().equals(resourceId));
        }

        @Override
        public Optional<Cleaning> findBySeating(StoreScope scope, SeatingId seatingId) {
            return cleanings.values().stream()
                .filter(cleaning -> cleaning.scope().equals(scope) && cleaning.seatingId().equals(seatingId))
                .findFirst();
        }

        @Override
        public Cleaning save(StoreScope scope, Cleaning cleaning) {
            saved.add(cleaning);
            cleanings.put(cleaning.id().value(), cleaning);
            return cleaning;
        }
    }

    private static final class FakeBusinessEventRepository implements BusinessEventRepositoryPort {
        final List<BusinessEvent> events = new ArrayList<>();

        @Override
        public BusinessEvent append(StoreScope scope, BusinessEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public BusinessEvent append(TenantScope scope, BusinessEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public BusinessEvent append(PlatformScope scope, BusinessEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public List<BusinessEvent> findByTarget(StoreScope scope, String targetType, UUID targetId) {
            return events.stream().filter(event -> event.targetType().equals(targetType) && event.targetId().equals(targetId)).toList();
        }

        @Override
        public List<BusinessEvent> findTimeline(StoreScope scope, TimeRange timeRange) {
            return events;
        }
    }

    private static final class FakeStateTransitionLogRepository implements StateTransitionLogRepositoryPort {
        final List<StateTransitionLog> logs = new ArrayList<>();

        @Override
        public StateTransitionLog append(StoreScope scope, StateTransitionLog transitionLog) {
            logs.add(transitionLog);
            return transitionLog;
        }

        @Override
        public StateTransitionLog append(TenantScope scope, StateTransitionLog transitionLog) {
            logs.add(transitionLog);
            return transitionLog;
        }

        @Override
        public StateTransitionLog append(PlatformScope scope, StateTransitionLog transitionLog) {
            logs.add(transitionLog);
            return transitionLog;
        }

        @Override
        public List<StateTransitionLog> findByTarget(StoreScope scope, String targetType, UUID targetId) {
            return logs.stream().filter(log -> log.targetType().equals(targetType) && log.targetId().equals(targetId)).toList();
        }

        @Override
        public Optional<StateTransitionLog> findLatest(StoreScope scope, String targetType, UUID targetId) {
            return logs.stream().filter(log -> log.targetType().equals(targetType) && log.targetId().equals(targetId)).reduce((first, second) -> second);
        }
    }

    private static final class FakeAuditLogRepository implements AuditLogRepositoryPort {
        final List<AuditLog> logs = new ArrayList<>();

        @Override
        public AuditLog append(StoreScope scope, AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }

        @Override
        public AuditLog append(TenantScope scope, AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }

        @Override
        public AuditLog append(PlatformScope scope, AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }

        @Override
        public List<AuditLog> findByTarget(StoreScope scope, String targetType, UUID targetId) {
            return logs.stream().filter(log -> log.targetType().equals(targetType) && targetId.equals(log.targetId())).toList();
        }

        @Override
        public List<AuditLog> findByOperation(StoreScope scope, String operationCode, TimeRange timeRange) {
            return logs.stream().filter(log -> log.operationCode().equals(operationCode)).toList();
        }
    }

    private static final class FakeIdempotencyRepository implements IdempotencyRepositoryPort {
        IdempotencyRecord existing;
        final List<IdempotencyRecord> started = new ArrayList<>();
        final List<IdempotencyRecord> completed = new ArrayList<>();
        final List<IdempotencyRecord> failed = new ArrayList<>();

        @Override
        public Optional<IdempotencyRecord> findByScopeActionKey(StoreScope scope, String source, String action, IdempotencyKey key) {
            return Optional.ofNullable(existing);
        }

        @Override
        public Optional<IdempotencyRecord> findByScopeActionKey(TenantScope scope, String source, String action, IdempotencyKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<IdempotencyRecord> findByScopeActionKey(PlatformScope scope, String source, String action, IdempotencyKey key) {
            return Optional.empty();
        }

        @Override
        public IdempotencyRecord start(StoreScope scope, String source, String action, IdempotencyKey key, String requestHash, OffsetDateTime expiresAt) {
            IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID(),
                key,
                source,
                action,
                requestHash,
                IdempotencyStatus.STARTED,
                null,
                null,
                null
            );
            started.add(record);
            existing = record;
            return record;
        }

        @Override
        public IdempotencyRecord complete(StoreScope scope, IdempotencyRecord record, String targetType) {
            IdempotencyRecord completedRecord = new IdempotencyRecord(
                record.id(),
                record.idempotencyKey(),
                record.source(),
                record.action(),
                record.requestHash(),
                IdempotencyStatus.COMPLETED,
                targetType,
                record.targetId(),
                record.responseSnapshot()
            );
            completed.add(completedRecord);
            existing = completedRecord;
            return completedRecord;
        }

        @Override
        public IdempotencyRecord fail(StoreScope scope, IdempotencyRecord record, String failureReason) {
            IdempotencyRecord failedRecord = new IdempotencyRecord(
                record.id(),
                record.idempotencyKey(),
                record.source(),
                record.action(),
                record.requestHash(),
                IdempotencyStatus.FAILED,
                record.targetType(),
                record.targetId(),
                "{\"failure_reason\":\"" + failureReason + "\"}"
            );
            failed.add(failedRecord);
            existing = failedRecord;
            return failedRecord;
        }
    }
}
