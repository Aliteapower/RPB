package com.rpb.reservation.table.application.service;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.table.application.TemporaryTableGroupError;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.rule.DefaultTableAvailabilityRule;
import com.rpb.reservation.table.rule.DefaultTableCapacityRule;
import com.rpb.reservation.table.rule.DefaultTableLockRule;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemporaryTableGroupApplicationService {
    public static final String RESOURCE_TABLE = "dining_table";
    public static final String RESOURCE_GROUP = "table_group";
    private static final String GROUP_TYPE = "temporary";
    private static final String MEMBER_ROLE = "temporary_member";

    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final TableLockRepositoryPort tableLockRepository;
    private final ReservationPreassignmentRepositoryPort preassignmentRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final Clock clock;
    private final DefaultTableAvailabilityRule tableAvailabilityRule = new DefaultTableAvailabilityRule();
    private final DefaultTableCapacityRule tableCapacityRule = new DefaultTableCapacityRule();
    private final DefaultTableLockRule tableLockRule = new DefaultTableLockRule();

    public TemporaryTableGroupApplicationService(
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        SeatingRepositoryPort seatingRepository,
        Clock clock
    ) {
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.tableLockRepository = tableLockRepository;
        this.preassignmentRepository = preassignmentRepository;
        this.seatingRepository = seatingRepository;
        this.clock = clock;
    }

    public TemporaryTableGroupResult createForSeating(TemporaryTableGroupCommand command) {
        TemporaryTableGroupError validationError = validateCommand(command);
        if (validationError != null) {
            return TemporaryTableGroupResult.failure(validationError);
        }
        if (command.sourceReservationId() != null
            && preassignmentRepository.findActiveAssignmentForReservation(command.scope(), command.sourceReservationId()).isPresent()) {
            return TemporaryTableGroupResult.failure(TemporaryTableGroupError.PREASSIGNMENT_CONFLICT);
        }

        List<DiningTable> memberTables = new ArrayList<>();
        for (UUID tableId : command.tableIds()) {
            DiningTable table = diningTableRepository.findById(command.scope(), new TableId(tableId)).orElse(null);
            TemporaryTableGroupError memberError = validateMember(command, table);
            if (memberError != null) {
                return TemporaryTableGroupResult.failure(memberError);
            }
            memberTables.add(table);
        }

        CapacityRange capacity = combinedCapacity(memberTables);
        if (!tableCapacityRule.evaluate(command.partySize(), capacity).accepted()) {
            return TemporaryTableGroupResult.failure(TemporaryTableGroupError.CAPACITY_INSUFFICIENT);
        }

        TableGroup group = tableGroupRepository.save(
            command.scope(),
            new TableGroup(
                new TableGroupId(UUID.randomUUID()),
                command.scope(),
                nextGroupCode(),
                GROUP_TYPE,
                capacity,
                TableGroupStatus.OCCUPIED
            )
        );
        List<TableGroupMember> members = new ArrayList<>();
        for (DiningTable table : memberTables) {
            members.add(tableGroupRepository.saveMember(
                command.scope(),
                new TableGroupMember(UUID.randomUUID(), command.scope(), group.id(), table.id(), MEMBER_ROLE)
            ));
        }
        return TemporaryTableGroupResult.success(group, memberTables, members);
    }

    private TemporaryTableGroupError validateCommand(TemporaryTableGroupCommand command) {
        if (command.tableIds().size() < 2) {
            return TemporaryTableGroupError.MEMBER_REQUIRED;
        }
        Set<UUID> distinct = new HashSet<>(command.tableIds());
        if (distinct.size() != command.tableIds().size()) {
            return TemporaryTableGroupError.MEMBER_DUPLICATE;
        }
        return null;
    }

    private TemporaryTableGroupError validateMember(TemporaryTableGroupCommand command, DiningTable table) {
        if (table == null || !table.scope().equals(command.scope()) || !table.combinable()) {
            return TemporaryTableGroupError.MEMBER_UNAVAILABLE;
        }
        RuleDecision availability = tableAvailabilityRule.evaluate(table);
        if (!availability.accepted()) {
            return TemporaryTableGroupError.MEMBER_UNAVAILABLE;
        }
        if (!tableLockRule.evaluate(tableLockRepository.existsActiveConflict(
            command.scope(),
            RESOURCE_TABLE,
            table.id().value(),
            OffsetDateTime.now(clock)
        )).accepted()) {
            return TemporaryTableGroupError.LOCK_CONFLICT;
        }
        if (seatingRepository.existsActiveResourceOccupancy(command.scope(), RESOURCE_TABLE, table.id().value())) {
            return TemporaryTableGroupError.MEMBER_UNAVAILABLE;
        }
        if (preassignmentRepository.findActiveAssignmentForResource(
            command.scope(),
            RESOURCE_TABLE,
            table.id().value(),
            command.businessDate()
        ).isPresent()) {
            return TemporaryTableGroupError.PREASSIGNMENT_CONFLICT;
        }
        return null;
    }

    private static CapacityRange combinedCapacity(List<DiningTable> memberTables) {
        int min = memberTables.stream().mapToInt(table -> table.capacity().min()).sum();
        int max = memberTables.stream().mapToInt(table -> table.capacity().max()).sum();
        return new CapacityRange(min, max);
    }

    private String nextGroupCode() {
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "TMP-" + DateTimeFormatter.BASIC_ISO_DATE.format(date) + "-" + suffix;
    }
}
