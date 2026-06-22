package com.rpb.reservation.cleaning.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.cleaning.application.command.CompleteCleaningCommand;
import com.rpb.reservation.cleaning.application.command.StartCleaningCommand;
import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.application.service.CleaningApplicationService;
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
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
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

class CleaningApplicationServiceTest {

    @Test
    void startsCleaningFromSeatingIdWithTableResource() {
        Scenario scenario = Scenario.readyTable();

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.cleaningId()).isNotNull();
        assertThat(result.seatingId()).isEqualTo(scenario.seating.id().value());
        assertThat(result.resourceType()).isEqualTo("dining_table");
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(result.previousTableStatus()).isEqualTo("occupied");
        assertThat(result.currentTableStatus()).isEqualTo("cleaning");
        assertThat(result.cleaningStatus()).isEqualTo("cleaning");
        assertThat(scenario.cleaningRepository.saved.getFirst().status()).isEqualTo(CleaningStatus.CLEANING);
        assertThat(scenario.diningTableRepository.saved.getLast().status()).isEqualTo(DiningTableStatus.CLEANING);
        assertThat(scenario.seatingRepository.savedResources.getLast().status()).isEqualTo("released");
        assertThat(scenario.idempotencyRepository.completed.getFirst().action()).isEqualTo("start_cleaning");
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .contains("cleaning.started", "table.cleaning");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::transitionCode)
            .contains("cleaning.start", "dining_table.cleaning");
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode)
            .contains("cleaning.start.completed");
    }

    @Test
    void startsCleaningFromSeatingIdWithTableGroupResource() {
        Scenario scenario = Scenario.readyGroup();

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.resourceType()).isEqualTo("table_group");
        assertThat(result.resourceId()).isEqualTo(scenario.group.id().value());
        assertThat(scenario.cleaningRepository.saved.getFirst().resourceType()).isEqualTo("table_group");
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsOnly(DiningTableStatus.CLEANING);
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .contains("cleaning.started", "table.cleaning");
    }

    @Test
    void completesCleaningByCleaningIdWithTableResource() {
        Scenario scenario = Scenario.readyTableCleaning();

        CleaningApplicationResult result = scenario.service().completeCleaning(scenario.completeCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.cleaningId()).isEqualTo(scenario.cleaning.id().value());
        assertThat(result.resourceType()).isEqualTo("dining_table");
        assertThat(result.previousTableStatus()).isEqualTo("cleaning");
        assertThat(result.currentTableStatus()).isEqualTo("available");
        assertThat(result.cleaningStatus()).isEqualTo("released");
        assertThat(scenario.cleaningRepository.saved.getLast().status()).isEqualTo(CleaningStatus.RELEASED);
        assertThat(scenario.diningTableRepository.saved.getLast().status()).isEqualTo(DiningTableStatus.AVAILABLE);
        assertThat(scenario.idempotencyRepository.completed.getFirst().action()).isEqualTo("complete_cleaning");
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .contains("cleaning.completed", "table.available");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::transitionCode)
            .contains("cleaning.complete", "dining_table.available");
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode)
            .contains("cleaning.complete.completed");
    }

    @Test
    void completesCleaningByCleaningIdWithTableGroupResource() {
        Scenario scenario = Scenario.readyGroupCleaning();

        CleaningApplicationResult result = scenario.service().completeCleaning(scenario.completeCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.resourceType()).isEqualTo("table_group");
        assertThat(scenario.cleaningRepository.saved.getLast().status()).isEqualTo(CleaningStatus.RELEASED);
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsOnly(DiningTableStatus.AVAILABLE);
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .contains("cleaning.completed", "table.available");
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutMutation() {
        Scenario scenario = Scenario.readyTable();
        String hash = CleaningApplicationService.requestHash(scenario.startCommand());
        UUID replayCleaningId = UUID.randomUUID();
        scenario.idempotencyRepository.existing = completedRecord(
            "start_cleaning",
            hash,
            replayCleaningId,
            scenario.seating.id().value(),
            "dining_table",
            scenario.table.id().value(),
            "cleaning",
            "cleaning"
        );

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.cleaningId()).isEqualTo(replayCleaningId);
        assertThat(scenario.cleaningRepository.saved).isEmpty();
        assertThat(scenario.diningTableRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.readyTableCleaning();
        String hash = CleaningApplicationService.requestHash(scenario.completeCommand());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-cleaning-complete"),
            "staff",
            "complete_cleaning",
            hash,
            IdempotencyStatus.STARTED,
            null,
            null,
            null
        );

        CleaningApplicationResult result = scenario.service().completeCleaning(scenario.completeCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.COMMAND_IN_PROGRESS);
        assertThat(scenario.diningTableRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.readyTable();
        String hash = CleaningApplicationService.requestHash(scenario.startCommand());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-cleaning-start"),
            "staff",
            "start_cleaning",
            hash,
            IdempotencyStatus.FAILED,
            null,
            null,
            "{\"failure_reason\":\"table_not_occupied\"}"
        );

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.cleaningRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.readyTable();
        scenario.idempotencyRepository.existing = completedRecord(
            "start_cleaning",
            "different-hash",
            UUID.randomUUID(),
            scenario.seating.id().value(),
            "dining_table",
            scenario.table.id().value(),
            "cleaning",
            "cleaning"
        );

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.cleaningRepository.saved).isEmpty();
    }

    @Test
    void seatingNotFoundIsRejected() {
        Scenario scenario = Scenario.readyTable();
        scenario.seatingRepository.seatings.clear();

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.SEATING_NOT_FOUND);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void noActiveSeatingResourceIsRejected() {
        Scenario scenario = Scenario.readyTable();
        scenario.seatingRepository.resources.clear();

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.SEATING_RESOURCE_NOT_FOUND);
    }

    @Test
    void tableNotOccupiedOnStartIsRejected() {
        Scenario scenario = Scenario.readyTable();
        scenario.diningTableRepository.tables.put(
            scenario.table.id().value(),
            scenario.tableWithStatus(DiningTableStatus.AVAILABLE)
        );

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.TABLE_NOT_OCCUPIED);
        assertThat(scenario.diningTableRepository.saved).isEmpty();
    }

    @Test
    void activeCleaningForResourceIsRejected() {
        Scenario scenario = Scenario.readyTable();
        scenario.cleaningRepository.activeByResource = new Cleaning(
            new CleaningId(UUID.randomUUID()),
            scenario.scope,
            scenario.seating.id(),
            "dining_table",
            scenario.table.id().value(),
            CleaningStatus.CLEANING
        );

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.CLEANING_ALREADY_ACTIVE);
    }

    @Test
    void cleaningNotFoundIsRejectedOnComplete() {
        Scenario scenario = Scenario.readyTableCleaning();
        scenario.cleaningRepository.cleanings.clear();

        CleaningApplicationResult result = scenario.service().completeCleaning(scenario.completeCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.CLEANING_NOT_FOUND);
    }

    @Test
    void alreadyReleasedCleaningIsRejectedOnComplete() {
        Scenario scenario = Scenario.readyTableCleaning();
        Cleaning released = new Cleaning(
            scenario.cleaning.id(),
            scenario.scope,
            scenario.seating.id(),
            "dining_table",
            scenario.table.id().value(),
            CleaningStatus.RELEASED
        );
        scenario.cleaningRepository.cleanings.put(released.id().value(), released);

        CleaningApplicationResult result = scenario.service().completeCleaning(scenario.completeCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.CLEANING_ALREADY_COMPLETED);
    }

    @Test
    void tableNotCleaningOnCompleteIsRejected() {
        Scenario scenario = Scenario.readyTableCleaning();
        scenario.diningTableRepository.tables.put(
            scenario.table.id().value(),
            scenario.tableWithStatus(DiningTableStatus.OCCUPIED)
        );

        CleaningApplicationResult result = scenario.service().completeCleaning(scenario.completeCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.TABLE_NOT_CLEANING);
        assertThat(scenario.diningTableRepository.saved).isEmpty();
    }

    @Test
    void invalidTableGroupIsRejected() {
        Scenario scenario = Scenario.readyGroup();
        scenario.tableGroupRepository.members.clear();

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.INVALID_TABLE_GROUP);
    }

    @Test
    void auditFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.readyTable();
        scenario.auditLogRepository.failOnAppend = true;

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.AUDIT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void repositoryFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.readyTable();
        scenario.cleaningRepository.failOnSave = true;

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(CleaningApplicationError.REPOSITORY_SAVE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void boundaryDoesNotCreateReservationQueueTurnoverApiOrUi() {
        Scenario scenario = Scenario.readyTable();

        CleaningApplicationResult result = scenario.service().startCleaning(scenario.startCommand());

        assertThat(result.success()).isTrue();
        assertThat(scenario.reservationCreated).isFalse();
        assertThat(scenario.queueCreated).isFalse();
        assertThat(scenario.turnoverCreated).isFalse();
        assertThat(scenario.controllerCreated).isFalse();
        assertThat(scenario.apiDtoCreated).isFalse();
        assertThat(scenario.uiCreated).isFalse();
    }

    private static IdempotencyRecord completedRecord(
        String action,
        String requestHash,
        UUID cleaningId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        String tableStatus,
        String cleaningStatus
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-cleaning"),
            "staff",
            action,
            requestHash,
            IdempotencyStatus.COMPLETED,
            "cleaning",
            cleaningId,
            """
                {"cleaningId":"%s","seatingId":"%s","resourceType":"%s","resourceId":"%s","currentTableStatus":"%s","cleaningStatus":"%s"}
                """.formatted(cleaningId, seatingId, resourceType, resourceId, tableStatus, cleaningStatus)
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
        final DiningTable table = tableWithStatus(DiningTableStatus.OCCUPIED);
        final Seating seating = new Seating(
            new SeatingId(UUID.randomUUID()),
            scope,
            "walk_in",
            UUID.randomUUID(),
            "S-1",
            new PartySize(2),
            SeatingStatus.OCCUPIED
        );
        final TableGroup group = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "G-01",
            "fixed",
            new CapacityRange(2, 8),
            TableGroupStatus.ACTIVE
        );
        Cleaning cleaning = new Cleaning(
            new CleaningId(UUID.randomUUID()),
            scope,
            seating.id(),
            "dining_table",
            table.id().value(),
            CleaningStatus.CLEANING
        );
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository();
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository();
        final FakeSeatingRepository seatingRepository = new FakeSeatingRepository();
        final FakeCleaningRepository cleaningRepository = new FakeCleaningRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean reservationCreated;
        boolean queueCreated;
        boolean turnoverCreated;
        boolean controllerCreated;
        boolean apiDtoCreated;
        boolean uiCreated;

        static Scenario readyTable() {
            Scenario scenario = new Scenario();
            scenario.diningTableRepository.tables.put(scenario.table.id().value(), scenario.table);
            scenario.seatingRepository.seatings.put(scenario.seating.id().value(), scenario.seating);
            scenario.seatingRepository.resources.put(
                scenario.seating.id().value(),
                new SeatingResource(
                    UUID.randomUUID(),
                    scenario.scope,
                    scenario.seating.id(),
                    "dining_table",
                    scenario.table.id().value(),
                    "active"
                )
            );
            return scenario;
        }

        static Scenario readyGroup() {
            Scenario scenario = readyTable();
            scenario.seatingRepository.resources.put(
                scenario.seating.id().value(),
                new SeatingResource(
                    UUID.randomUUID(),
                    scenario.scope,
                    scenario.seating.id(),
                    "table_group",
                    scenario.group.id().value(),
                    "active"
                )
            );
            scenario.tableGroupRepository.groups.put(scenario.group.id().value(), scenario.group);
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                scenario.table.id(),
                "primary"
            ));
            return scenario;
        }

        static Scenario readyTableCleaning() {
            Scenario scenario = readyTable();
            DiningTable cleaningTable = scenario.tableWithStatus(DiningTableStatus.CLEANING);
            scenario.diningTableRepository.tables.put(cleaningTable.id().value(), cleaningTable);
            scenario.cleaning = new Cleaning(
                scenario.cleaning.id(),
                scenario.scope,
                scenario.seating.id(),
                "dining_table",
                cleaningTable.id().value(),
                CleaningStatus.CLEANING
            );
            scenario.cleaningRepository.cleanings.put(scenario.cleaning.id().value(), scenario.cleaning);
            return scenario;
        }

        static Scenario readyGroupCleaning() {
            Scenario scenario = readyGroup();
            DiningTable cleaningTable = scenario.tableWithStatus(DiningTableStatus.CLEANING);
            scenario.diningTableRepository.tables.put(cleaningTable.id().value(), cleaningTable);
            scenario.tableGroupRepository.members.clear();
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                cleaningTable.id(),
                "primary"
            ));
            scenario.cleaning = new Cleaning(
                scenario.cleaning.id(),
                scenario.scope,
                scenario.seating.id(),
                "table_group",
                scenario.group.id().value(),
                CleaningStatus.CLEANING
            );
            scenario.cleaningRepository.cleanings.put(scenario.cleaning.id().value(), scenario.cleaning);
            return scenario;
        }

        CleaningApplicationService service() {
            return new CleaningApplicationService(
                storeRepository,
                diningTableRepository,
                tableGroupRepository,
                seatingRepository,
                cleaningRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository
            );
        }

        StartCleaningCommand startCommand() {
            return new StartCleaningCommand(
                tenantId.value(),
                storeId.value(),
                seating.id().value(),
                "idem-cleaning-start",
                actorId,
                "staff",
                null,
                null
            );
        }

        CompleteCleaningCommand completeCommand() {
            return new CompleteCleaningCommand(
                tenantId.value(),
                storeId.value(),
                cleaning.id().value(),
                "idem-cleaning-complete",
                actorId,
                "staff",
                null,
                null
            );
        }

        DiningTable tableWithStatus(DiningTableStatus status) {
            return new DiningTable(
                table == null ? new TableId(UUID.randomUUID()) : table.id(),
                scope,
                areaId,
                "T-" + status.code(),
                new CapacityRange(1, 4),
                status,
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

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        final Map<UUID, Seating> seatings = new HashMap<>();
        final Map<UUID, SeatingResource> resources = new HashMap<>();
        final List<Seating> saved = new ArrayList<>();
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
                    && resource.status().equals("active"));
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return resources.values().stream()
                .filter(resource -> resource.scope().equals(scope)
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId)
                    && resource.status().equals("active"))
                .findFirst()
                .flatMap(resource -> findById(scope, resource.seatingId()));
        }

        @Override
        public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
            return Optional.ofNullable(resources.get(seatingId.value()))
                .filter(resource -> resource.scope().equals(scope) && resource.status().equals("active"));
        }

        @Override
        public Seating save(StoreScope scope, Seating seating) {
            saved.add(seating);
            seatings.put(seating.id().value(), seating);
            return seating;
        }

        @Override
        public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
            savedResources.add(resource);
            resources.put(resource.seatingId().value(), resource);
            return resource;
        }
    }

    private static final class FakeCleaningRepository implements CleaningRepositoryPort {
        final Map<UUID, Cleaning> cleanings = new HashMap<>();
        final List<Cleaning> saved = new ArrayList<>();
        Cleaning activeByResource;
        boolean failOnSave;

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
            if (failOnSave) {
                throw new IllegalStateException("cleaning save failed");
            }
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
