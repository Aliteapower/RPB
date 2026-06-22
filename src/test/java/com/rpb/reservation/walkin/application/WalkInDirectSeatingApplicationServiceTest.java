package com.rpb.reservation.walkin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.application.command.SeatWalkInDirectlyCommand;
import com.rpb.reservation.walkin.application.service.WalkInDirectSeatingApplicationService;
import com.rpb.reservation.walkin.application.port.out.WalkInRepositoryPort;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.value.WalkInId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalkInDirectSeatingApplicationServiceTest {

    @Test
    void seatsWalkInWithNoPhoneCustomerAndAutoSelectedTable() {
        Scenario scenario = Scenario.ready();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.walkInId()).isNotNull();
        assertThat(result.seatingId()).isNotNull();
        assertThat(result.resourceType()).isEqualTo("dining_table");
        assertThat(result.resourceId()).isEqualTo(scenario.recommendedTable.id().value());
        assertThat(result.partySizeSnapshot()).isEqualTo(2);
        assertThat(scenario.customerRepository.saved).hasSize(1);
        assertThat(scenario.customerRepository.saved.getFirst().phone().isPresent()).isFalse();
        assertThat(scenario.walkInRepository.saved).hasSize(1);
        assertThat(scenario.seatingRepository.saved).hasSize(1);
        assertThat(scenario.seatingRepository.savedResources).hasSize(1);
        assertThat(scenario.tableLockRepository.saved).hasSize(1);
        assertThat(scenario.diningTableRepository.saved.getLast().status()).isEqualTo(DiningTableStatus.OCCUPIED);
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
    }

    @Test
    void seatsWalkInWithSpecifiedTable() {
        Scenario scenario = Scenario.ready();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.commandWithTable(scenario.recommendedTable.id().value()));

        assertThat(result.success()).isTrue();
        assertThat(result.resourceType()).isEqualTo("dining_table");
        assertThat(result.resourceId()).isEqualTo(scenario.recommendedTable.id().value());
    }

    @Test
    void seatsWalkInWithExistingTableGroup() {
        Scenario scenario = Scenario.ready();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.commandWithGroup(scenario.group.id().value()));

        assertThat(result.success()).isTrue();
        assertThat(result.resourceType()).isEqualTo("table_group");
        assertThat(result.resourceId()).isEqualTo(scenario.group.id().value());
        assertThat(scenario.seatingRepository.savedResources.getFirst().resourceType()).isEqualTo("table_group");
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutCreatingNewOccupancy() {
        Scenario scenario = Scenario.ready();
        String hash = WalkInDirectSeatingApplicationService.requestHash(scenario.autoCommand());
        UUID replayWalkInId = UUID.randomUUID();
        UUID replaySeatingId = UUID.randomUUID();
        scenario.idempotencyRepository.existing = completedRecord(hash, replayWalkInId, replaySeatingId, scenario.recommendedTable.id().value());

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.walkInId()).isEqualTo(replayWalkInId);
        assertThat(result.seatingId()).isEqualTo(replaySeatingId);
        assertThat(scenario.walkInRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.tableLockRepository.saved).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready();
        String hash = WalkInDirectSeatingApplicationService.requestHash(scenario.autoCommand());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-1001"),
            "staff",
            "seat_walk_in_directly",
            hash,
            IdempotencyStatus.STARTED,
            null,
            null,
            null
        );

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.COMMAND_IN_PROGRESS);
        assertThat(scenario.walkInRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready();
        String hash = WalkInDirectSeatingApplicationService.requestHash(scenario.autoCommand());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-1001"),
            "staff",
            "seat_walk_in_directly",
            hash,
            IdempotencyStatus.FAILED,
            null,
            null,
            "{\"failure_reason\":\"repository_save_failed\"}"
        );

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.walkInRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready();
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            UUID.randomUUID(),
            UUID.randomUUID(),
            scenario.recommendedTable.id().value()
        );

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.walkInRepository.saved).isEmpty();
    }

    @Test
    void invalidPartySizeIsRejectedAsApplicationError() {
        Scenario scenario = Scenario.ready();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommandWithPartySize(0));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.INVALID_PARTY_SIZE);
        assertThat(scenario.walkInRepository.saved).isEmpty();
    }

    @Test
    void tableAndGroupCannotBothBePresent() {
        Scenario scenario = Scenario.ready();
        SeatWalkInDirectlyCommand command = new SeatWalkInDirectlyCommand(
            scenario.tenantId.value(),
            scenario.storeId.value(),
            2,
            null,
            "Guest",
            null,
            null,
            scenario.recommendedTable.id().value(),
            scenario.group.id().value(),
            "idem-both",
            scenario.actorId,
            "staff",
            null,
            null
        );

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.INVALID_RESOURCE_SELECTION);
    }

    @Test
    void noAvailableTableIsRejected() {
        Scenario scenario = Scenario.ready();
        scenario.diningTableRepository.candidates.clear();
        scenario.tableGroupRepository.candidates.clear();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.NO_ASSIGNABLE_TABLE);
    }

    @Test
    void tableCapacityInsufficientIsRejected() {
        Scenario scenario = Scenario.ready();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommandWithPartySize(9));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.PARTY_SIZE_OUTSIDE_CAPACITY);
    }

    @Test
    void activeTableLockConflictIsRejected() {
        Scenario scenario = Scenario.ready();
        scenario.tableLockRepository.conflict = true;

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.TABLE_LOCK_CONFLICT);
    }

    @Test
    void inactiveTableIsRejected() {
        Scenario scenario = Scenario.ready();
        DiningTable inactive = scenario.tableWithStatus(DiningTableStatus.INACTIVE);
        scenario.diningTableRepository.tables.put(inactive.id().value(), inactive);

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.commandWithTable(inactive.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.TABLE_RESOURCE_UNAVAILABLE);
    }

    @Test
    void invalidTableGroupIsRejected() {
        Scenario scenario = Scenario.ready();
        TableGroup invalidGroup = new TableGroup(
            scenario.group.id(),
            scenario.scope,
            "G-INVALID",
            "fixed",
            new CapacityRange(1, 6),
            TableGroupStatus.INACTIVE
        );
        scenario.tableGroupRepository.groups.put(invalidGroup.id().value(), invalidGroup);

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.commandWithGroup(invalidGroup.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.INVALID_TABLE_GROUP);
    }

    @Test
    void manualOverrideMissingIsRejectedWhenSelectedTableIsNotRecommended() {
        Scenario scenario = Scenario.ready();
        DiningTable selected = scenario.secondAvailableTable();
        scenario.diningTableRepository.tables.put(selected.id().value(), selected);

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.commandWithTable(selected.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.MANUAL_OVERRIDE_REQUIRED);
    }

    @Test
    void manualOverrideWithReasonIsAcceptedAndWrittenToAuditMetadata() {
        Scenario scenario = Scenario.ready();
        DiningTable selected = scenario.secondAvailableTable();
        scenario.diningTableRepository.tables.put(selected.id().value(), selected);

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.commandWithTableAndOverride(
            selected.id().value(),
            "override.large-party",
            null
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.resourceId()).isEqualTo(selected.id().value());
        assertThat(scenario.auditLogRepository.logs)
            .anySatisfy(log -> assertThat(log.metadata()).contains("override.large-party"));
        assertThat(scenario.seatingRepository.saved.getFirst().manualOverrideReasonCode()).isEqualTo("override.large-party");
    }

    @Test
    void writesEventTransitionAuditAndDoesNotCreateReservationQueueCleaningOrTurnover() {
        Scenario scenario = Scenario.ready();

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isTrue();
        assertThat(scenario.businessEventRepository.events)
            .extracting(BusinessEvent::eventType)
            .contains("walk_in.created", "seating.created", "table.locked", "table.occupied");
        assertThat(scenario.stateTransitionLogRepository.logs).isNotEmpty();
        assertThat(scenario.auditLogRepository.logs)
            .extracting(AuditLog::operationCode)
            .contains("walk_in_direct_seating.completed");
        assertThat(scenario.reservationCreated).isFalse();
        assertThat(scenario.queueTicketCreated).isFalse();
        assertThat(scenario.cleaningStarted).isFalse();
        assertThat(scenario.turnoverCreated).isFalse();
    }

    @Test
    void auditWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready();
        scenario.auditLogRepository.failOnAppend = true;

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.AUDIT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void repositorySaveFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready();
        scenario.walkInRepository.failOnSave = true;

        WalkInDirectSeatingResult result = scenario.service().seatWalkInDirectly(scenario.autoCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(WalkInDirectSeatingError.REPOSITORY_SAVE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    private static IdempotencyRecord completedRecord(String requestHash, UUID walkInId, UUID seatingId, UUID resourceId) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-1001"),
            "staff",
            "seat_walk_in_directly",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "seating",
            seatingId,
            """
                {"walkInId":"%s","seatingId":"%s","resourceType":"dining_table","resourceId":"%s","partySizeSnapshot":2}
                """.formatted(walkInId, seatingId, resourceId)
        );
    }

    private static final class Scenario {
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final UUID areaId = UUID.randomUUID();
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "en-SG", "active");
        final StorePolicy policy = new StorePolicy(UUID.randomUUID(), scope, 15, 3, 90, "same_group_tail", "default");
        final DiningTable recommendedTable = new DiningTable(
            new TableId(UUID.randomUUID()),
            scope,
            areaId,
            "T-01",
            new CapacityRange(1, 4),
            DiningTableStatus.AVAILABLE,
            true
        );
        final TableGroup group = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "G-01",
            "fixed",
            new CapacityRange(3, 8),
            TableGroupStatus.ACTIVE
        );
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeCustomerRepository customerRepository = new FakeCustomerRepository();
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository();
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository();
        final FakeTableLockRepository tableLockRepository = new FakeTableLockRepository();
        final FakeWalkInRepository walkInRepository = new FakeWalkInRepository();
        final FakeSeatingRepository seatingRepository = new FakeSeatingRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean reservationCreated;
        boolean queueTicketCreated;
        boolean cleaningStarted;
        boolean turnoverCreated;

        static Scenario ready() {
            Scenario scenario = new Scenario();
            scenario.diningTableRepository.tables.put(scenario.recommendedTable.id().value(), scenario.recommendedTable);
            scenario.diningTableRepository.candidates.add(scenario.recommendedTable);
            scenario.tableGroupRepository.groups.put(scenario.group.id().value(), scenario.group);
            scenario.tableGroupRepository.candidates.add(scenario.group);
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                scenario.recommendedTable.id(),
                "primary"
            ));
            return scenario;
        }

        WalkInDirectSeatingApplicationService service() {
            return new WalkInDirectSeatingApplicationService(
                storeRepository,
                customerRepository,
                diningTableRepository,
                tableGroupRepository,
                tableLockRepository,
                walkInRepository,
                seatingRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository
            );
        }

        SeatWalkInDirectlyCommand autoCommand() {
            return autoCommandWithPartySize(2);
        }

        SeatWalkInDirectlyCommand autoCommandWithPartySize(Integer partySize) {
            return new SeatWalkInDirectlyCommand(
                tenantId.value(),
                storeId.value(),
                partySize,
                null,
                "No Phone Guest",
                null,
                null,
                null,
                null,
                "idem-1001",
                actorId,
                "staff",
                null,
                null
            );
        }

        SeatWalkInDirectlyCommand commandWithTable(UUID tableId) {
            return commandWithTableAndOverride(tableId, null, null);
        }

        SeatWalkInDirectlyCommand commandWithTableAndOverride(UUID tableId, String reasonCode, String note) {
            return new SeatWalkInDirectlyCommand(
                tenantId.value(),
                storeId.value(),
                2,
                null,
                "Guest",
                null,
                null,
                tableId,
                null,
                "idem-" + tableId,
                actorId,
                "staff",
                reasonCode,
                note
            );
        }

        SeatWalkInDirectlyCommand commandWithGroup(UUID tableGroupId) {
            return new SeatWalkInDirectlyCommand(
                tenantId.value(),
                storeId.value(),
                4,
                null,
                "Group Guest",
                null,
                null,
                null,
                tableGroupId,
                "idem-group",
                actorId,
                "staff",
                null,
                null
            );
        }

        DiningTable tableWithStatus(DiningTableStatus status) {
            return new DiningTable(
                new TableId(UUID.randomUUID()),
                scope,
                areaId,
                "T-" + status.code(),
                new CapacityRange(1, 4),
                status,
                true
            );
        }

        DiningTable secondAvailableTable() {
            return new DiningTable(
                new TableId(UUID.randomUUID()),
                scope,
                areaId,
                "T-02",
                new CapacityRange(1, 4),
                DiningTableStatus.AVAILABLE,
                true
            );
        }
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        private final Scenario scenario;

        FakeStoreRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<Store> findById(StoreScope scope) {
            return scope.equals(scenario.scope) ? Optional.of(scenario.store) : Optional.empty();
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return scope.equals(scenario.scope) ? Optional.of(scenario.policy) : Optional.empty();
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

    private static final class FakeCustomerRepository implements CustomerRepositoryPort {
        final List<Customer> saved = new ArrayList<>();

        @Override
        public Optional<Customer> findById(TenantScope scope, CustomerId customerId) {
            return saved.stream().filter(customer -> customer.id().equals(customerId)).findFirst();
        }

        @Override
        public Optional<Customer> findByCode(TenantScope scope, String customerCode) {
            return saved.stream().filter(customer -> customer.customerCode().equals(customerCode)).findFirst();
        }

        @Override
        public Optional<Customer> findByPhone(TenantScope scope, E164Phone phone) {
            return saved.stream().filter(customer -> customer.phone().equals(phone)).findFirst();
        }

        @Override
        public List<Customer> searchNoPhoneCandidates(TenantScope scope, String lookupText) {
            return List.of();
        }

        @Override
        public Customer save(TenantScope scope, Customer customer) {
            saved.add(customer);
            return customer;
        }
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        final Map<UUID, DiningTable> tables = new HashMap<>();
        final List<DiningTable> candidates = new ArrayList<>();
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
            return candidates.stream()
                .filter(table -> table.scope().equals(scope))
                .toList();
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
        final List<TableGroup> candidates = new ArrayList<>();

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
            return candidates.stream()
                .filter(group -> group.scope().equals(scope))
                .toList();
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
        boolean conflict;
        final List<TableLock> saved = new ArrayList<>();

        @Override
        public Optional<TableLock> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveConflict(StoreScope scope, String resourceType, UUID resourceId, OffsetDateTime at) {
            return conflict;
        }

        @Override
        public TableLock save(StoreScope scope, TableLock lock) {
            saved.add(lock);
            return lock;
        }

        @Override
        public TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt) {
            return saved.stream().filter(lock -> lock.id().equals(tableLockId)).findFirst().orElseThrow();
        }
    }

    private static final class FakeWalkInRepository implements WalkInRepositoryPort {
        boolean failOnSave;
        final List<WalkIn> saved = new ArrayList<>();

        @Override
        public Optional<WalkIn> findById(StoreScope scope, WalkInId walkInId) {
            return saved.stream().filter(walkIn -> walkIn.id().equals(walkInId)).findFirst();
        }

        @Override
        public Optional<WalkIn> findByCode(StoreScope scope, String walkInCode) {
            return Optional.empty();
        }

        @Override
        public List<WalkIn> findArrivals(StoreScope scope, BusinessDate businessDate, String statusCode) {
            return saved.stream().filter(walkIn -> walkIn.status().equals(statusCode)).toList();
        }

        @Override
        public WalkIn save(StoreScope scope, WalkIn walkIn) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            saved.add(walkIn);
            return walkIn;
        }
    }

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        final List<Seating> saved = new ArrayList<>();
        final List<SeatingResource> savedResources = new ArrayList<>();

        @Override
        public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
            return saved.stream().filter(seating -> seating.id().equals(seatingId)).findFirst();
        }

        @Override
        public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
            return saved.stream().filter(seating -> sourceType.equals(seating.sourceType()) && sourceId.equals(seating.sourceId())).findFirst();
        }

        @Override
        public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return savedResources.stream().anyMatch(resource -> resourceType.equals(resource.resourceType()) && resourceId.equals(resource.resourceId()));
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.empty();
        }

        @Override
        public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
            return savedResources.stream()
                .filter(resource -> resource.seatingId().equals(seatingId) && resource.status().equals("active"))
                .findFirst();
        }

        @Override
        public Seating save(StoreScope scope, Seating seating) {
            saved.add(seating);
            return seating;
        }

        @Override
        public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
            savedResources.add(resource);
            return resource;
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
        boolean failOnAppend;
        final List<AuditLog> logs = new ArrayList<>();

        @Override
        public AuditLog append(StoreScope scope, AuditLog auditLog) {
            if (failOnAppend) {
                throw new IllegalStateException("audit failed");
            }
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
