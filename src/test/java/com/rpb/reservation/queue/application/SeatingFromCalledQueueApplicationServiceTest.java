package com.rpb.reservation.queue.application;

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
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.application.command.SeatCalledQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.service.SeatingFromCalledQueueApplicationService;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatingFromCalledQueueApplicationServiceTest {

    @Test
    void seatsCalledQueueTicketToSingleTableAndWritesEvidence() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.queueTicketId()).isEqualTo(scenario.queueTicketId.value());
        assertThat(result.queueTicketNumber()).isEqualTo(22);
        assertThat(result.queueTicketStatus()).isEqualTo("seated");
        assertThat(result.reservationId()).isEqualTo(scenario.reservationId.value());
        assertThat(result.reservationCode()).isEqualTo("R-Q-SEAT-1");
        assertThat(result.reservationStatus()).isEqualTo("seated");
        assertThat(result.seatingId()).isNotNull();
        assertThat(result.resourceType()).isEqualTo("dining_table");
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(result.partySizeSnapshot()).isEqualTo(4);
        assertThat(result.seatingStatus()).isEqualTo("occupied");
        assertThat(result.seatingResourceStatus()).isEqualTo("active");
        assertThat(result.tableStatus()).isEqualTo("occupied");
        assertThat(result.alreadySeated()).isFalse();
        assertThat(result.idempotencyStatus()).isEqualTo("completed");
        assertThat(result.events()).containsExactly("queue_ticket.seated", "reservation.seated", "seating.created", "table.occupied");

        assertThat(scenario.queueTicketRepository.saved).hasSize(1);
        assertThat(scenario.queueTicketRepository.saved.getFirst().status()).isEqualTo(QueueTicketStatus.SEATED);
        assertThat(scenario.reservationRepository.saved).hasSize(1);
        assertThat(scenario.reservationRepository.saved.getFirst().status()).isEqualTo(ReservationStatus.SEATED);
        assertThat(scenario.seatingRepository.saved).hasSize(1);
        assertThat(scenario.seatingRepository.saved.getFirst().sourceType()).isEqualTo("queue_ticket");
        assertThat(scenario.seatingRepository.saved.getFirst().sourceId()).isEqualTo(scenario.queueTicketId.value());
        assertThat(scenario.seatingRepository.saved.getFirst().status()).isEqualTo(SeatingStatus.OCCUPIED);
        assertThat(scenario.seatingRepository.savedResources).hasSize(1);
        assertThat(scenario.seatingRepository.savedResources.getFirst().resourceType()).isEqualTo("dining_table");
        assertThat(scenario.seatingRepository.savedResources.getFirst().status()).isEqualTo("active");
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status).contains(DiningTableStatus.OCCUPIED);
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .containsExactly("queue_ticket.seated", "reservation.seated", "seating.created", "table.occupied");
        assertThat(scenario.stateTransitionLogRepository.logs)
            .anySatisfy(log -> {
                assertThat(log.targetType()).isEqualTo("queue_ticket");
                assertThat(log.fromStatus()).isEqualTo("called");
                assertThat(log.toStatus()).isEqualTo("seated");
            })
            .anySatisfy(log -> {
                assertThat(log.targetType()).isEqualTo("reservation");
                assertThat(log.fromStatus()).isEqualTo("arrived");
                assertThat(log.toStatus()).isEqualTo("seated");
            })
            .anySatisfy(log -> {
                assertThat(log.targetType()).isEqualTo("dining_table");
                assertThat(log.fromStatus()).isEqualTo("available");
                assertThat(log.toStatus()).isEqualTo("occupied");
            });
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("queue.seat");
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
        assertThat(scenario.idempotencyRepository.completed.getFirst().targetType()).isEqualTo("seating");

        assertBoundaryUnchanged(scenario);
    }

    @Test
    void seatsCalledQueueTicketToTableGroupAndOccupiesEveryMemberTable() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithGroup(scenario.group.id().value()));

        assertThat(result.success()).isTrue();
        assertThat(result.resourceType()).isEqualTo("table_group");
        assertThat(result.resourceId()).isEqualTo(scenario.group.id().value());
        assertThat(result.groupMemberStatuses()).containsExactly("occupied", "occupied");
        assertThat(result.occupiedTableIds()).containsExactlyInAnyOrder(
            scenario.table.id().value(),
            scenario.groupMemberTable.id().value()
        );
        assertThat(scenario.seatingRepository.savedResources.getFirst().resourceType()).isEqualTo("table_group");
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::id)
            .containsExactlyInAnyOrder(scenario.table.id(), scenario.groupMemberTable.id());
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsOnly(DiningTableStatus.OCCUPIED);
        assertThat(scenario.businessEventRepository.events)
            .anySatisfy(event -> {
                assertThat(event.eventType()).isEqualTo("table.occupied");
                assertThat(event.targetType()).isEqualTo("table_group");
                assertThat(event.targetId()).isEqualTo(scenario.group.id().value());
            });
        assertThat(scenario.stateTransitionLogRepository.logs)
            .filteredOn(log -> log.targetType().equals("dining_table") && log.toStatus().equals("occupied"))
            .hasSize(2);
    }

    @Test
    void seatsCalledQueueTicketToTemporaryTableGroupAndPersistsMembers() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTemporaryTables(
                scenario.table.id().value(),
                scenario.groupMemberTable.id().value()
            ));

        assertThat(result.success()).isTrue();
        assertThat(result.queueTicketStatus()).isEqualTo("seated");
        assertThat(result.reservationStatus()).isEqualTo("seated");
        assertThat(result.resourceType()).isEqualTo("table_group");
        assertThat(result.resourceId()).isNotEqualTo(scenario.group.id().value());
        assertThat(result.groupMemberStatuses()).containsExactly("occupied", "occupied");
        assertThat(scenario.tableGroupRepository.saved).hasSize(1);
        TableGroup temporaryGroup = scenario.tableGroupRepository.saved.getFirst();
        assertThat(temporaryGroup.groupType()).isEqualTo("temporary");
        assertThat(temporaryGroup.status()).isEqualTo(TableGroupStatus.OCCUPIED);
        assertThat(result.resourceId()).isEqualTo(temporaryGroup.id().value());
        assertThat(scenario.tableGroupRepository.findActiveMembers(scenario.scope, temporaryGroup.id()))
            .extracting(member -> member.tableId().value())
            .containsExactly(scenario.table.id().value(), scenario.groupMemberTable.id().value());
        assertThat(scenario.seatingRepository.savedResources.getFirst().resourceId()).isEqualTo(temporaryGroup.id().value());
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsOnly(DiningTableStatus.OCCUPIED);
    }

    @Test
    void alreadySeatedWithMatchingEvidenceReturnsAlreadySeatedWithoutDuplicateWrites() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.SEATED, ReservationStatus.SEATED);
        Seating existingSeating = scenario.persistExistingQueueSeating(scenario.table.id().value(), "dining_table");

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-already-seated"));

        assertThat(result.success()).isTrue();
        assertThat(result.alreadySeated()).isTrue();
        assertThat(result.replayed()).isFalse();
        assertThat(result.seatingId()).isEqualTo(existingSeating.id().value());
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(result.queueTicketStatus()).isEqualTo("seated");
        assertThat(result.reservationStatus()).isEqualTo("seated");
        assertThat(result.events()).isEmpty();
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.savedResources).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
    }

    @Test
    void alreadySeatedWithoutMatchingActiveSeatingIsConsistencyError() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.SEATED, ReservationStatus.SEATED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-seated-without-seating"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING);
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("queue.seat.failed");
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutMutation() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        SeatCalledQueueTicketCommand command = scenario.commandWithTable(scenario.table.id().value());
        String hash = SeatingFromCalledQueueApplicationService.requestHash(command);
        UUID replaySeatingId = UUID.randomUUID();
        scenario.idempotencyRepository.existing = completedRecord(
            hash,
            scenario.queueTicketId.value(),
            scenario.reservationId.value(),
            replaySeatingId,
            scenario.table.id().value(),
            false
        );

        SeatingFromCalledQueueResult result = scenario.service().seatCalledQueueTicket(command);

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.seatingId()).isEqualTo(replaySeatingId);
        assertThat(result.queueTicketStatus()).isEqualTo("seated");
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.savedResources).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        SeatCalledQueueTicketCommand command = scenario.commandWithTable(scenario.table.id().value());
        String hash = SeatingFromCalledQueueApplicationService.requestHash(command);
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.STARTED, null);

        SeatingFromCalledQueueResult result = scenario.service().seatCalledQueueTicket(command);

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.IDEMPOTENCY_IN_PROGRESS);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        SeatCalledQueueTicketCommand command = scenario.commandWithTable(scenario.table.id().value());
        String hash = SeatingFromCalledQueueApplicationService.requestHash(command);
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.FAILED, "{\"failure_reason\":\"audit_write_failed\"}");

        SeatingFromCalledQueueResult result = scenario.service().seatCalledQueueTicket(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            scenario.queueTicketId.value(),
            scenario.reservationId.value(),
            UUID.randomUUID(),
            scenario.table.id().value(),
            false
        );

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void resourceSelectionMustBeExactlyOneTableOrGroup() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        SeatingFromCalledQueueResult both = scenario.service().seatCalledQueueTicket(
            scenario.command(scenario.table.id().value(), scenario.group.id().value(), "idem-both")
        );
        SeatingFromCalledQueueResult neither = scenario.service().seatCalledQueueTicket(
            scenario.command(null, null, "idem-neither")
        );

        assertThat(both.success()).isFalse();
        assertThat(both.error()).isEqualTo(SeatingFromCalledQueueError.RESOURCE_SELECTION_CONFLICT);
        assertThat(neither.success()).isFalse();
        assertThat(neither.error()).isEqualTo(SeatingFromCalledQueueError.RESOURCE_SELECTION_REQUIRED);
        assertThat(scenario.idempotencyRepository.started).isEmpty();
    }

    @Test
    void queueTicketNotFoundReturnsApplicationError() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.persisted.clear();

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.QUEUE_TICKET_NOT_FOUND);
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void nonCalledQueueTicketStatusesAreRejected() {
        assertQueueStatusRejected(QueueTicketStatus.WAITING, SeatingFromCalledQueueError.QUEUE_TICKET_STATUS_NOT_CALLED);
        assertQueueStatusRejected(QueueTicketStatus.SKIPPED, SeatingFromCalledQueueError.QUEUE_TICKET_STATUS_NOT_CALLED);
        assertQueueStatusRejected(QueueTicketStatus.REJOINED, SeatingFromCalledQueueError.QUEUE_TICKET_STATUS_NOT_CALLED);
        assertQueueStatusRejected(QueueTicketStatus.CANCELLED, SeatingFromCalledQueueError.QUEUE_TICKET_CANNOT_SEAT_CANCELLED);
        assertQueueStatusRejected(QueueTicketStatus.EXPIRED, SeatingFromCalledQueueError.QUEUE_TICKET_CANNOT_SEAT_EXPIRED);
    }

    @Test
    void relatedReservationNotFoundReturnsApplicationError() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        scenario.reservationRepository.reservations.clear();

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.RESERVATION_NOT_FOUND);
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void relatedReservationMustBeArrived() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.CONFIRMED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(SeatingFromCalledQueueError.RESERVATION_STATUS_NOT_ARRIVED);
        assertThat(scenario.reservationRepository.reservations.get(scenario.reservationId.value()).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void tableFailuresReturnApplicationErrors() {
        Scenario notFound = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        SeatingFromCalledQueueResult missing = notFound.service()
            .seatCalledQueueTicket(notFound.commandWithTable(UUID.randomUUID()));
        assertThat(missing.success()).isFalse();
        assertThat(missing.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_NOT_FOUND);

        Scenario unavailable = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        unavailable.replaceTableStatus(DiningTableStatus.OCCUPIED);
        SeatingFromCalledQueueResult occupied = unavailable.service()
            .seatCalledQueueTicket(unavailable.commandWithTable(unavailable.table.id().value()));
        assertThat(occupied.success()).isFalse();
        assertThat(occupied.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_NOT_AVAILABLE);

        Scenario insufficient = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        insufficient.replaceTableCapacity(new CapacityRange(1, 2));
        SeatingFromCalledQueueResult tooSmall = insufficient.service()
            .seatCalledQueueTicket(insufficient.commandWithTable(insufficient.table.id().value()));
        assertThat(tooSmall.success()).isFalse();
        assertThat(tooSmall.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_CAPACITY_INSUFFICIENT);

        Scenario locked = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        locked.tableLockRepository.conflict = true;
        SeatingFromCalledQueueResult conflict = locked.service()
            .seatCalledQueueTicket(locked.commandWithTable(locked.table.id().value()));
        assertThat(conflict.success()).isFalse();
        assertThat(conflict.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_LOCK_CONFLICT);
    }

    @Test
    void tableGroupFailuresReturnApplicationErrors() {
        Scenario notFound = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        SeatingFromCalledQueueResult missing = notFound.service()
            .seatCalledQueueTicket(notFound.commandWithGroup(UUID.randomUUID()));
        assertThat(missing.success()).isFalse();
        assertThat(missing.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_GROUP_NOT_FOUND);

        Scenario invalid = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        invalid.replaceGroupStatus(TableGroupStatus.INACTIVE);
        SeatingFromCalledQueueResult inactive = invalid.service()
            .seatCalledQueueTicket(invalid.commandWithGroup(invalid.group.id().value()));
        assertThat(inactive.success()).isFalse();
        assertThat(inactive.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_GROUP_INVALID);

        Scenario memberUnavailable = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        memberUnavailable.replaceGroupMemberStatus(DiningTableStatus.OCCUPIED);
        SeatingFromCalledQueueResult unavailable = memberUnavailable.service()
            .seatCalledQueueTicket(memberUnavailable.commandWithGroup(memberUnavailable.group.id().value()));
        assertThat(unavailable.success()).isFalse();
        assertThat(unavailable.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE);

        Scenario tooSmall = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        tooSmall.replaceGroupCapacity(new CapacityRange(1, 2));
        SeatingFromCalledQueueResult insufficient = tooSmall.service()
            .seatCalledQueueTicket(tooSmall.commandWithGroup(tooSmall.group.id().value()));
        assertThat(insufficient.success()).isFalse();
        assertThat(insufficient.error()).isEqualTo(SeatingFromCalledQueueError.TABLE_GROUP_CAPACITY_INSUFFICIENT);
    }

    @Test
    void eventTransitionAuditAndPersistenceFailuresReturnApplicationErrors() {
        assertWriteFailure(scenario -> scenario.businessEventRepository.failOnAppend = true, SeatingFromCalledQueueError.BUSINESS_EVENT_WRITE_FAILED);
        assertWriteFailure(scenario -> scenario.stateTransitionLogRepository.failOnAppend = true, SeatingFromCalledQueueError.STATE_TRANSITION_WRITE_FAILED);
        assertWriteFailure(scenario -> scenario.auditLogRepository.failOnAppend = true, SeatingFromCalledQueueError.AUDIT_WRITE_FAILED);
        assertWriteFailure(scenario -> scenario.seatingRepository.failOnSave = true, SeatingFromCalledQueueError.PERSISTENCE_ERROR);
    }

    @Test
    void boundaryDoesNotCreateForbiddenArtifactsOrBehaviors() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-boundary"));

        assertThat(result.success()).isTrue();
        assertBoundaryUnchanged(scenario);
    }

    private static void assertQueueStatusRejected(QueueTicketStatus status, SeatingFromCalledQueueError expectedError) {
        Scenario scenario = Scenario.ready(status, ReservationStatus.ARRIVED);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-" + status.code()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(expectedError);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
    }

    private static void assertWriteFailure(java.util.function.Consumer<Scenario> mutator, SeatingFromCalledQueueError expectedError) {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        mutator.accept(scenario);

        SeatingFromCalledQueueResult result = scenario.service()
            .seatCalledQueueTicket(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-failure-" + expectedError.code()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(expectedError);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    private static void assertBoundaryUnchanged(Scenario scenario) {
        assertThat(scenario.queueSkipImplemented).isFalse();
        assertThat(scenario.queueRejoinImplemented).isFalse();
        assertThat(scenario.queueDisplayImplemented).isFalse();
        assertThat(scenario.queueListWorkbenchImplemented).isFalse();
        assertThat(scenario.autoAssignmentImplemented).isFalse();
        assertThat(scenario.noShowImplemented).isFalse();
        assertThat(scenario.cancellationImplemented).isFalse();
        assertThat(scenario.cleaningCreated).isFalse();
        assertThat(scenario.turnoverCreated).isFalse();
        assertThat(scenario.controllerCreated).isFalse();
        assertThat(scenario.apiDtoCreated).isFalse();
        assertThat(scenario.uiCreated).isFalse();
        assertThat(scenario.migrationChanged).isFalse();
    }

    private static IdempotencyRecord completedRecord(
        String requestHash,
        UUID queueTicketId,
        UUID reservationId,
        UUID seatingId,
        UUID resourceId,
        boolean alreadySeated
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-seat-called-queue-ticket"),
            "staff",
            "seat_called_queue_ticket",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "seating",
            seatingId,
            """
                {"queueTicketId":"%s","queueTicketNumber":22,"queueTicketStatus":"seated","reservationId":"%s","reservationCode":"R-Q-SEAT-1","reservationStatus":"seated","seatingId":"%s","resourceType":"dining_table","resourceId":"%s","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":%s}
                """.formatted(queueTicketId, reservationId, seatingId, resourceId, alreadySeated).trim()
        );
    }

    private static IdempotencyRecord idempotencyRecord(String requestHash, IdempotencyStatus status, String snapshot) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-seat-called-queue-ticket"),
            "staff",
            "seat_called_queue_ticket",
            requestHash,
            status,
            null,
            null,
            snapshot
        );
    }

    private static final class Scenario {
        final Instant now = Instant.parse("2026-06-20T04:00:00Z");
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final UUID areaId = UUID.randomUUID();
        final QueueTicketId queueTicketId = new QueueTicketId(UUID.randomUUID());
        final UUID queueGroupId = UUID.randomUUID();
        final ReservationId reservationId = new ReservationId(UUID.randomUUID());
        final CustomerId customerId = new CustomerId(UUID.randomUUID());
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "zh-CN", "active");
        final DiningTable table = table("T1", new CapacityRange(2, 4), DiningTableStatus.AVAILABLE);
        final DiningTable groupMemberTable = table("T2", new CapacityRange(2, 4), DiningTableStatus.AVAILABLE);
        final TableGroup group = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "G1",
            "temporary",
            new CapacityRange(2, 8),
            TableGroupStatus.ACTIVE
        );
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeReservationRepository reservationRepository = new FakeReservationRepository();
        final FakeQueueTicketRepository queueTicketRepository = new FakeQueueTicketRepository();
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository();
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository();
        final FakeTableLockRepository tableLockRepository = new FakeTableLockRepository();
        final FakeSeatingRepository seatingRepository = new FakeSeatingRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean queueSkipImplemented;
        boolean queueRejoinImplemented;
        boolean queueDisplayImplemented;
        boolean queueListWorkbenchImplemented;
        boolean autoAssignmentImplemented;
        boolean noShowImplemented;
        boolean cancellationImplemented;
        boolean cleaningCreated;
        boolean turnoverCreated;
        boolean controllerCreated;
        boolean apiDtoCreated;
        boolean uiCreated;
        boolean migrationChanged;

        static Scenario ready(QueueTicketStatus ticketStatus, ReservationStatus reservationStatus) {
            Scenario scenario = new Scenario();
            scenario.queueTicketRepository.persisted.put(
                scenario.queueTicketId.value(),
                scenario.queueTicket(ticketStatus)
            );
            scenario.reservationRepository.reservations.put(
                scenario.reservationId.value(),
                scenario.reservation(reservationStatus)
            );
            scenario.diningTableRepository.tables.put(scenario.table.id().value(), scenario.table);
            scenario.diningTableRepository.tables.put(scenario.groupMemberTable.id().value(), scenario.groupMemberTable);
            scenario.tableGroupRepository.groups.put(scenario.group.id().value(), scenario.group);
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                scenario.table.id(),
                "primary"
            ));
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                scenario.groupMemberTable.id(),
                "member"
            ));
            return scenario;
        }

        SeatingFromCalledQueueApplicationService service() {
            return new SeatingFromCalledQueueApplicationService(
                storeRepository,
                queueTicketRepository,
                reservationRepository,
                diningTableRepository,
                tableGroupRepository,
                tableLockRepository,
                seatingRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository,
                Clock.fixed(now, ZoneOffset.UTC)
            );
        }

        SeatCalledQueueTicketCommand commandWithTable(UUID tableId) {
            return commandWithTableAndKey(tableId, "idem-seat-called-queue-ticket");
        }

        SeatCalledQueueTicketCommand commandWithGroup(UUID tableGroupId) {
            return command(null, tableGroupId, "idem-seat-called-queue-ticket");
        }

        SeatCalledQueueTicketCommand commandWithTableAndKey(UUID tableId, String idempotencyKey) {
            return command(tableId, null, idempotencyKey);
        }

        SeatCalledQueueTicketCommand commandWithTemporaryTables(UUID... tableIds) {
            return new SeatCalledQueueTicketCommand(
                tenantId.value(),
                storeId.value(),
                queueTicketId.value(),
                null,
                null,
                List.of(tableIds),
                "idem-seat-temporary-group",
                actorId,
                "staff",
                "manual_override",
                "Host combined tables",
                "Seat after queue call"
            );
        }

        SeatCalledQueueTicketCommand command(UUID tableId, UUID tableGroupId, String idempotencyKey) {
            return new SeatCalledQueueTicketCommand(
                tenantId.value(),
                storeId.value(),
                queueTicketId.value(),
                tableId,
                tableGroupId,
                List.of(),
                idempotencyKey,
                actorId,
                "staff",
                "manual_override",
                "Host selected a specific table",
                "Seat after queue call"
            );
        }

        QueueTicket queueTicket(QueueTicketStatus status) {
            return new QueueTicket(
                queueTicketId,
                scope,
                queueGroupId,
                customerId,
                reservationId.value(),
                null,
                new QueueTicketNumber(22),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                status,
                3,
                status == QueueTicketStatus.WAITING ? null : now.minusSeconds(120),
                status == QueueTicketStatus.WAITING ? null : now.plusSeconds(60),
                "queue ticket note"
            );
        }

        Reservation reservation(ReservationStatus status) {
            return new Reservation(
                reservationId,
                scope,
                customerId,
                new ReservationCode("R-Q-SEAT-1"),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                Instant.parse("2026-06-20T03:30:00Z"),
                Instant.parse("2026-06-20T05:00:00Z"),
                Instant.parse("2026-06-20T03:45:00Z"),
                status,
                "staff",
                null,
                null,
                "Window seat preferred",
                Instant.parse("2026-06-18T01:00:00Z"),
                Instant.parse("2026-06-20T03:45:00Z"),
                null
            );
        }

        DiningTable table(String code, CapacityRange capacity, DiningTableStatus status) {
            return new DiningTable(new TableId(UUID.randomUUID()), scope, areaId, code, capacity, status, true);
        }

        void replaceTableStatus(DiningTableStatus status) {
            diningTableRepository.tables.put(
                table.id().value(),
                new DiningTable(table.id(), scope, areaId, table.tableCode(), table.capacity(), status, table.combinable())
            );
        }

        void replaceTableCapacity(CapacityRange capacity) {
            diningTableRepository.tables.put(
                table.id().value(),
                new DiningTable(table.id(), scope, areaId, table.tableCode(), capacity, table.status(), table.combinable())
            );
        }

        void replaceGroupStatus(TableGroupStatus status) {
            tableGroupRepository.groups.put(
                group.id().value(),
                new TableGroup(group.id(), scope, group.groupCode(), group.groupType(), group.capacity(), status)
            );
        }

        void replaceGroupCapacity(CapacityRange capacity) {
            tableGroupRepository.groups.put(
                group.id().value(),
                new TableGroup(group.id(), scope, group.groupCode(), group.groupType(), capacity, group.status())
            );
        }

        void replaceGroupMemberStatus(DiningTableStatus status) {
            diningTableRepository.tables.put(
                groupMemberTable.id().value(),
                new DiningTable(
                    groupMemberTable.id(),
                    scope,
                    areaId,
                    groupMemberTable.tableCode(),
                    groupMemberTable.capacity(),
                    status,
                    groupMemberTable.combinable()
                )
            );
        }

        Seating persistExistingQueueSeating(UUID resourceId, String resourceType) {
            Seating seating = new Seating(
                new SeatingId(UUID.randomUUID()),
                scope,
                "queue_ticket",
                queueTicketId.value(),
                "S-existing",
                null,
                null,
                new PartySize(4),
                SeatingStatus.OCCUPIED
            );
            seatingRepository.persistedSeatings.put(seating.id().value(), seating);
            seatingRepository.persistedResources.add(new SeatingResource(
                UUID.randomUUID(),
                scope,
                seating.id(),
                resourceType,
                resourceId,
                "active"
            ));
            return seating;
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
            return Optional.empty();
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

    private static final class FakeReservationRepository implements ReservationRepositoryPort {
        final Map<UUID, Reservation> reservations = new HashMap<>();
        final List<Reservation> saved = new ArrayList<>();

        @Override
        public Optional<Reservation> findById(StoreScope scope, ReservationId reservationId) {
            return Optional.ofNullable(reservations.get(reservationId.value()))
                .filter(reservation -> reservation.scope().equals(scope));
        }

        @Override
        public Optional<Reservation> findByCode(StoreScope scope, ReservationCode reservationCode) {
            return Optional.empty();
        }

        @Override
        public boolean existsByReservationCode(StoreScope scope, ReservationCode reservationCode) {
            return false;
        }

        @Override
        public List<Reservation> findStoreSchedule(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
            return List.of();
        }

        @Override
        public boolean existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
            return false;
        }

        @Override
        public List<Reservation> findActiveConflicts(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
            return List.of();
        }

        @Override
        public int findActiveCapacityUsage(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
            return 0;
        }

        @Override
        public Reservation save(StoreScope scope, Reservation reservation) {
            saved.add(reservation);
            reservations.put(reservation.id().value(), reservation);
            return reservation;
        }
    }

    private static final class FakeQueueTicketRepository implements QueueTicketRepositoryPort {
        final Map<UUID, QueueTicket> persisted = new HashMap<>();
        final List<QueueTicket> saved = new ArrayList<>();

        @Override
        public Optional<QueueTicket> findById(StoreScope scope, QueueTicketId queueTicketId) {
            return Optional.ofNullable(persisted.get(queueTicketId.value()))
                .filter(ticket -> ticket.scope().equals(scope));
        }

        @Override
        public List<QueueTicket> findActiveQueue(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return List.of();
        }

        @Override
        public Optional<QueueTicket> findNextCallable(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return Optional.empty();
        }

        @Override
        public Optional<QueueTicket> findActiveByReservationId(StoreScope scope, UUID reservationId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveSourceTicket(StoreScope scope, String sourceType, UUID sourceId) {
            return false;
        }

        @Override
        public QueueTicket save(StoreScope scope, QueueTicket queueTicket) {
            saved.add(queueTicket);
            persisted.put(queueTicket.id().value(), queueTicket);
            return queueTicket;
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
            return tables.values().stream().filter(table -> table.scope().equals(scope)).toList();
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
        final List<TableGroup> saved = new ArrayList<>();

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
            return groups.values().stream().filter(group -> group.scope().equals(scope)).toList();
        }

        @Override
        public TableGroup save(StoreScope scope, TableGroup tableGroup) {
            saved.add(tableGroup);
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
            return lock;
        }

        @Override
        public TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt) {
            throw new UnsupportedOperationException("not used by seating from called queue");
        }
    }

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        final Map<UUID, Seating> persistedSeatings = new HashMap<>();
        final List<SeatingResource> persistedResources = new ArrayList<>();
        final List<Seating> saved = new ArrayList<>();
        final List<SeatingResource> savedResources = new ArrayList<>();
        boolean failOnSave;

        @Override
        public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
            return Optional.ofNullable(persistedSeatings.get(seatingId.value())).filter(seating -> seating.scope().equals(scope));
        }

        @Override
        public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
            return persistedSeatings.values().stream()
                .filter(seating -> seating.scope().equals(scope))
                .filter(seating -> sourceType.equals(seating.sourceType()) && sourceId.equals(seating.sourceId()))
                .filter(seating -> seating.status() == SeatingStatus.OCCUPIED || seating.status() == SeatingStatus.LOCKED || seating.status() == SeatingStatus.PLANNED)
                .findFirst();
        }

        @Override
        public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return persistedResources.stream()
                .anyMatch(resource -> resource.scope().equals(scope)
                    && resource.status().equals("active")
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId));
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return persistedResources.stream()
                .filter(resource -> resource.scope().equals(scope)
                    && resource.status().equals("active")
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId))
                .findFirst()
                .flatMap(resource -> findById(scope, resource.seatingId()));
        }

        @Override
        public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
            return persistedResources.stream()
                .filter(resource -> resource.scope().equals(scope) && resource.seatingId().equals(seatingId) && resource.status().equals("active"))
                .findFirst();
        }

        @Override
        public Seating save(StoreScope scope, Seating seating) {
            if (failOnSave) {
                throw new IllegalStateException("seating save failed");
            }
            saved.add(seating);
            persistedSeatings.put(seating.id().value(), seating);
            return seating;
        }

        @Override
        public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
            if (failOnSave) {
                throw new IllegalStateException("seating resource save failed");
            }
            savedResources.add(resource);
            persistedResources.add(resource);
            return resource;
        }
    }

    private static final class FakeBusinessEventRepository implements BusinessEventRepositoryPort {
        final List<BusinessEvent> events = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public BusinessEvent append(StoreScope scope, BusinessEvent event) {
            if (failOnAppend) {
                throw new IllegalStateException("event append failed");
            }
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
        boolean failOnAppend;

        @Override
        public StateTransitionLog append(StoreScope scope, StateTransitionLog transitionLog) {
            if (failOnAppend) {
                throw new IllegalStateException("transition append failed");
            }
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
            return logs.stream()
                .filter(log -> log.targetType().equals(targetType) && log.targetId().equals(targetId))
                .reduce((first, second) -> second);
        }
    }

    private static final class FakeAuditLogRepository implements AuditLogRepositoryPort {
        final List<AuditLog> logs = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public AuditLog append(StoreScope scope, AuditLog auditLog) {
            if (failOnAppend) {
                throw new IllegalStateException("audit append failed");
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
