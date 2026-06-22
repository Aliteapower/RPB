# Seating From Called Queue Vertical Slice Checklist V1

## 1. Read Inputs

- [x] Seating From Called Queue request attachment was read.
- [x] Reservation Arrived To Queue application contract was read.
- [x] Reservation Arrived To Queue application implementation report was read.
- [x] Reservation Arrived To Queue API implementation report was read.
- [x] Reservation Arrived To Queue UI validation report was read.
- [x] Queue Call application contract was read.
- [x] Queue Call application implementation report was read.
- [x] Queue Call API implementation report was read.
- [x] Queue Call UI validation report was read.
- [x] Reservation Arrived Direct Seating application contract was read.
- [x] Reservation Arrived Direct Seating application implementation report was read.
- [x] Reservation Arrived Direct Seating API implementation report was read.
- [x] Reservation Arrived Direct Seating UI validation report was read.
- [x] WalkIn Direct Seating application implementation report was read.
- [x] Schema design was read.
- [x] V001 migration QueueTicket, Seating, SeatingResource, DiningTable, TableGroup, TableLock, idempotency, audit, and transition sections were checked.
- [x] QueueTicket domain was read.
- [x] QueueGroup domain was read.
- [x] QueueTicketStatus was read.
- [x] QueueTicketStateMachine was read.
- [x] QueueTicketRepositoryPort was read.
- [x] QueueTicketEntity was read.
- [x] DefaultQueueTicketMapper was read.
- [x] QueueTicketPersistenceAdapter was read.
- [x] Seating domain was read.
- [x] SeatingResource domain was read.
- [x] SeatingStatus was read.
- [x] SeatingStateMachine was read.
- [x] SeatingRepositoryPort was read.
- [x] SeatingEntity was read.
- [x] SeatingResourceEntity was read.
- [x] DefaultSeatingMapper was read.
- [x] DefaultSeatingResourceMapper was read.
- [x] SeatingPersistenceAdapter was read.
- [x] SeatingSourceValidator was read.
- [x] SeatingResourceValidator was read.
- [x] DiningTable domain/status/state machine/repository port were read.
- [x] TableGroup domain/status/repository port were read.
- [x] TableLock domain/status/repository port were read.
- [x] Table availability, capacity, assignment, lock, and group validation rules were read.
- [x] Reservation domain/status/state machine/repository port were read.
- [x] BusinessEventRepositoryPort was read.
- [x] StateTransitionLogRepositoryPort was read.
- [x] AuditLogRepositoryPort was read.
- [x] IdempotencyRepositoryPort and IdempotencyRule were read.
- [x] App Gate operational handoff was read.
- [x] App Gate integration checklist was read.
- [x] App Gate permission metadata alignment was read.
- [x] `AppGateRequiredPermission.java` was read.
- [x] Governance documents were read.
- [x] Architecture document was read.
- [x] Reservation system skill documents were read.

## 2. Starting Baseline

- [x] Reservation Arrived To Queue completed and validated: Yes.
- [x] QueueTicket can be created with `status = waiting`: Yes.
- [x] Queue Call completed and validated: Yes.
- [x] QueueTicket can become `status = called`: Yes.
- [x] Reservation status after Queue Call remains `arrived`: Yes.
- [x] Reservation Direct Seating has reusable table/tableGroup seating rules: Yes.
- [x] Existing `seatings` supports QueueTicket source through `queue_ticket_id`: Yes.
- [x] Existing `seating_resources` supports DiningTable/TableGroup XOR target: Yes.
- [x] QueueTicket enum contains `seated`: Yes.
- [x] QueueTicket enum contains `completed`: No.
- [x] QueueTicketStateMachine allows `called -> seated`: Yes.
- [x] ReservationStateMachine allows `arrived -> seated`: Yes.
- [x] AppGateRequiredPermission currently contains `queue.seat`: No.
- [x] This checklist does not authorize adding `queue.seat` to App Gate metadata.

## 3. Selected Vertical Slice

- [x] Selected slice is `Seating From Called Queue`.
- [x] Input QueueTicket status is `called`.
- [x] Input Reservation status is `arrived`.
- [x] Staff manually selects exactly one `tableId` or `tableGroupId`.
- [x] Output QueueTicket status is `seated`.
- [x] Output Reservation status is `seated`.
- [x] Seating is created.
- [x] Seating source is QueueTicket.
- [x] SeatingResource is created.
- [x] Selected table or TableGroup member tables become `occupied`.
- [x] BusinessEvent records are written.
- [x] StateTransitionLog records are written.
- [x] AuditLog is written.
- [x] Idempotency is completed.

## 4. Scope Boundary

- [x] Only called QueueTicket to seating is covered.
- [x] Reservation `arrived -> seated` is covered.
- [x] QueueTicket `called -> seated` is covered.
- [x] Manual resource selection is covered.
- [x] Table occupancy is covered.
- [x] Seating source QueueTicket is covered.
- [x] AlreadySeated behavior is covered.
- [x] Queue Skip designed: No.
- [x] Queue Rejoin designed: No.
- [x] Queue Display designed: No.
- [x] Queue list/workbench designed: No.
- [x] WalkIn queue seating designed: No.
- [x] Auto assignment designed: No.
- [x] Table recommendation designed: No.
- [x] No-show designed: No.
- [x] Cancellation designed: No.
- [x] Cleaning designed: No.
- [x] Turnover designed: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.
- [x] App Gate metadata changed: No.

## 5. Business Object Boundary

- [x] QueueTicket remains separate from Reservation.
- [x] Seating remains the occupancy event and record.
- [x] Seating source is QueueTicket, not Reservation.
- [x] Related Reservation is updated through QueueTicket source, not through Seating source duplication.
- [x] CheckIn remains separate from Seating.
- [x] Cleaning remains downstream and is not started here.
- [x] Turnover remains outside this slice.
- [x] No-show remains outside this slice.
- [x] Cancellation remains outside this slice.
- [x] WalkIn queue seating remains outside this slice.

## 6. QueueTicket Status Decision

- [x] Fresh success transition is `called -> seated`.
- [x] `seated` is selected because schema and enum support it.
- [x] `completed` is not selected because schema and enum do not support it.
- [x] `waiting` is rejected.
- [x] `skipped` is rejected.
- [x] `rejoined` is rejected.
- [x] `cancelled` is rejected.
- [x] `expired` is rejected.
- [x] `seated` is handled only by AlreadySeated evidence checks.
- [x] QueueTicket called evidence must exist for fresh seating.
- [x] QueueTicket status `completed` must not be written in V1.

## 7. Reservation Status Decision

- [x] Related Reservation is loaded from `queue_ticket.reservation_id`.
- [x] Related Reservation must be `arrived` for fresh seating.
- [x] Fresh transition is `arrived -> seated`.
- [x] Reservation `called` status is not introduced.
- [x] Reservation state machine is not modified.
- [x] Client-provided `reservationId` is forbidden.
- [x] Reservation not found is a failure.
- [x] Reservation not `arrived` is a failure for fresh seating.
- [x] QueueTicket already seated expects Reservation already seated for AlreadySeated.

## 8. Seating Source Boundary

- [x] Existing `seatings` table is reused.
- [x] Source column used is `queue_ticket_id`.
- [x] `reservation_id` remains null on Seating for this slice.
- [x] `walk_in_id` remains null on Seating for this slice.
- [x] V001 source XOR constraint is preserved.
- [x] No new seating table is designed.
- [x] No `reservation_seatings` table is designed.
- [x] No `queue_seatings` table is designed.

## 9. Resource Selection Boundary

- [x] Command requires exactly one of `tableId` or `tableGroupId`.
- [x] DiningTable target maps to `resource_type = dining_table`.
- [x] TableGroup target maps to `resource_type = table_group`.
- [x] SeatingResource status is `active`.
- [x] Table capacity check is required.
- [x] Table availability check is required.
- [x] Table lock conflict check is required.
- [x] Active SeatingResource occupancy check is required.
- [x] TableGroup validation is required.
- [x] TableGroup member availability check is required.
- [x] Fixed TableGroup configuration status should not be mutated solely for occupancy.
- [x] Member DiningTables become occupied.

## 10. Command Boundary

- [x] Command is `SeatCalledQueueTicketCommand`.
- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `queueTicketId`.
- [x] Command includes optional `tableId`.
- [x] Command includes optional `tableGroupId`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] Command includes optional `overrideReasonCode`.
- [x] Command includes optional `overrideNote`.
- [x] Command includes optional `note`.
- [x] Command excludes `reservationId`.
- [x] Command excludes `walkInId`.
- [x] Command excludes `checkInAt`.
- [x] Command excludes `noShowAt`.
- [x] Command excludes `cancelledAt`.
- [x] Command excludes `cleaningId`.
- [x] Command excludes `turnoverId`.
- [x] Command excludes `queueSkipReason`.
- [x] Command excludes `queueRejoinReason`.
- [x] Command excludes client-provided status fields.

## 11. Application Service Boundary

- [x] Service boundary is `SeatingFromCalledQueueApplicationService`.
- [x] Method is `seatCalledQueueTicket(SeatCalledQueueTicketCommand command)`.
- [x] Service validates command.
- [x] Service validates resource XOR.
- [x] Service builds `StoreScope`.
- [x] Service checks idempotency.
- [x] Service validates Store access.
- [x] Service loads QueueTicket by Store scope and id.
- [x] Service validates QueueTicket source and status.
- [x] Service loads related Reservation.
- [x] Service validates Reservation status.
- [x] Service detects AlreadySeated behavior.
- [x] Service validates selected resource.
- [x] Service creates Seating.
- [x] Service creates SeatingResource.
- [x] Service updates table/member occupancy.
- [x] Service updates QueueTicket to seated.
- [x] Service updates Reservation to seated.
- [x] Service writes BusinessEvents.
- [x] Service writes StateTransitionLogs.
- [x] Service writes AuditLog.
- [x] Service completes idempotency.
- [x] Service returns result.
- [x] Service does not parse API request.
- [x] Service does not implement Queue Skip/Rejoin/Display.
- [x] Service does not implement Auto assignment.
- [x] Service does not start Cleaning.
- [x] Service does not implement No-show or Cancellation.

## 12. Required Ports

- [x] `StoreRepositoryPort` required.
- [x] `QueueTicketRepositoryPort` required.
- [x] `ReservationRepositoryPort` required.
- [x] `DiningTableRepositoryPort` required.
- [x] `TableGroupRepositoryPort` required.
- [x] `TableLockRepositoryPort` required.
- [x] `SeatingRepositoryPort` required.
- [x] `BusinessEventRepositoryPort` required.
- [x] `StateTransitionLogRepositoryPort` required.
- [x] `AuditLogRepositoryPort` required.
- [x] `IdempotencyRepositoryPort` required.
- [x] `CleaningRepositoryPort` introduced: No.
- [x] `TurnoverRepositoryPort` introduced: No.
- [x] No-show repository introduced: No.
- [x] Cancellation repository introduced: No.

## 13. Required Rules / Policies / Validators

- [x] `StoreAccessPolicy` required.
- [x] `QueueTicketSeatRule` required or supplemented.
- [x] `SeatingFromCalledQueueRule` required or supplemented.
- [x] `QueueTicketStateMachine` required.
- [x] `ReservationStateMachine` required.
- [x] `DiningTableStateMachine` required.
- [x] `TableAvailabilityRule` required.
- [x] `TableCapacityRule` required.
- [x] `TableLockRule` required.
- [x] `TableAssignmentRule` allowed only for manual selection validation.
- [x] `TableGroupValidationRule` required.
- [x] `SeatingSourceValidator` required.
- [x] `SeatingResourceValidator` required.
- [x] `AuditRule` required.
- [x] `BusinessEventRule` required.
- [x] `StateTransitionRule` required.
- [x] `IdempotencyRule` required.
- [x] Queue Skip rule introduced: No.
- [x] Queue Rejoin rule introduced: No.
- [x] NoShowPolicy introduced: No.
- [x] CancellationPolicy introduced: No.
- [x] AutoAssignmentPolicy introduced: No.
- [x] CleaningReleasePolicy introduced: No.
- [x] TurnoverPolicy introduced: No.

## 14. Event / Audit / Transition Boundary

- [x] BusinessEvent `queue_ticket.seated` is required.
- [x] BusinessEvent `reservation.seated` is required.
- [x] BusinessEvent `seating.created` is required.
- [x] BusinessEvent `table.occupied` is required.
- [x] Audit operation is `queue.seat`.
- [x] Failure audit operation is `queue.seat.failed`.
- [x] StateTransitionLog includes QueueTicket `called -> seated`.
- [x] StateTransitionLog includes Reservation `arrived -> seated`.
- [x] StateTransitionLog includes Seating occupancy.
- [x] StateTransitionLog includes table/member occupancy.
- [x] Queue Skip transition is written: No.
- [x] Queue Rejoin transition is written: No.
- [x] Cleaning transition is written: No.
- [x] Turnover transition is written: No.
- [x] Raw DB exception exposure allowed: No.

## 15. Idempotency Boundary

- [x] Idempotency action is `seat_called_queue_ticket`.
- [x] Store-scoped idempotency is used.
- [x] Request hash fields are defined.
- [x] Request hash excludes generated `seatedAt` and random IDs.
- [x] Completed same hash replays.
- [x] In-progress same hash returns retry later.
- [x] Failed same hash requires new key.
- [x] Same key with different hash conflicts.
- [x] Completed replay creates duplicate Seating: No.
- [x] Completed replay creates duplicate SeatingResource: No.
- [x] Completed replay duplicates events/transitions/audit: No.
- [x] Completed replay mutates QueueTicket/Reservation/table: No.

## 16. AlreadySeated Boundary

- [x] AlreadySeated is success-like.
- [x] AlreadySeated requires `queue_ticket.status = seated`.
- [x] AlreadySeated requires active Seating source = QueueTicket.
- [x] AlreadySeated requires active SeatingResource.
- [x] AlreadySeated requires related Reservation already `seated`.
- [x] AlreadySeated returns existing resource details.
- [x] AlreadySeated creates duplicate Seating: No.
- [x] AlreadySeated creates duplicate SeatingResource: No.
- [x] AlreadySeated creates duplicate BusinessEvent: No.
- [x] AlreadySeated creates duplicate StateTransitionLog: No.
- [x] AlreadySeated creates duplicate AuditLog: No.
- [x] AlreadySeated mutates QueueTicket/Reservation/table: No.
- [x] AlreadySeated missing evidence is a consistency failure.

## 17. App Gate Future Boundary

- [x] Future API app key is `reservation_queue`.
- [x] Future API permission is `queue.seat`.
- [x] Future API annotation is documented as `@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")`.
- [x] Current `AppGateRequiredPermission` contains `queue.seat`: No.
- [x] This contract does not modify `AppGateRequiredPermission`.
- [x] This contract does not modify App Gate metadata.
- [x] New app key created: No.
- [x] Body `tenantId` trusted: No.
- [x] App Gate deny happens before business handler in future API.
- [x] App Gate deny mutates business state: No.
- [x] App Gate deny writes `APP_GATE_DENIED`.

## 18. Failure Coverage

- [x] Invalid command covered.
- [x] Missing idempotency key covered.
- [x] Both `tableId` and `tableGroupId` covered.
- [x] Neither `tableId` nor `tableGroupId` covered.
- [x] Store not found covered.
- [x] Store scope mismatch covered.
- [x] Store access denied covered.
- [x] QueueTicket not found covered.
- [x] QueueTicket source not Reservation covered.
- [x] QueueTicket status not called covered.
- [x] QueueTicket called evidence incomplete covered.
- [x] QueueTicket already seated without active seating covered.
- [x] QueueTicket cancelled covered.
- [x] QueueTicket expired covered.
- [x] Reservation not found covered.
- [x] Reservation not arrived covered.
- [x] Table not found covered.
- [x] Table not available covered.
- [x] Table capacity insufficient covered.
- [x] Table lock conflict covered.
- [x] TableGroup not found covered.
- [x] TableGroup invalid covered.
- [x] TableGroup member unavailable covered.
- [x] TableGroup capacity insufficient covered.
- [x] Invalid seating source/resource covered.
- [x] Idempotency conflict covered.
- [x] Idempotency in progress covered.
- [x] Failed idempotency requires new key covered.
- [x] Event write failure covered.
- [x] Transition write failure covered.
- [x] Audit write failure covered.
- [x] Persistence save failure covered.
- [x] Raw DB exception exposure allowed: No.

## 19. Test Contract

- [x] Called QueueTicket seats to table test defined.
- [x] Called QueueTicket seats to TableGroup test defined.
- [x] QueueTicket status `seated` test defined.
- [x] Reservation status `seated` test defined.
- [x] Seating source QueueTicket test defined.
- [x] Seating does not set `reservation_id` test defined.
- [x] SeatingResource active test defined.
- [x] Table occupancy test defined.
- [x] TableGroup member occupancy test defined.
- [x] Event writes test defined.
- [x] Transition evidence test defined.
- [x] Audit write test defined.
- [x] Idempotency completed test defined.
- [x] Completed replay test defined.
- [x] In-progress retry-later test defined.
- [x] Failed-key requires new key test defined.
- [x] Hash conflict test defined.
- [x] AlreadySeated no duplicate test defined.
- [x] Boundary no Queue Skip/Rejoin/Display test defined.
- [x] Boundary no Auto assignment test defined.
- [x] Boundary no Cleaning test defined.
- [x] Boundary no Turnover test defined.
- [x] Boundary no No-show/Cancellation test defined.
- [x] Boundary no API/UI/Migration/App Gate metadata test defined.
- [x] Test code written in this round: No.

## 20. Forbidden Artifact Check

- [x] Java Application Service created: No.
- [x] Repository implementation created: No.
- [x] Controller created: No.
- [x] API DTO created: No.
- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] Router entry created: No.
- [x] Staff Home entry created: No.
- [x] Flyway migration created or modified: No.
- [x] SQL file created or modified: No.
- [x] Database structure changed: No.
- [x] App Gate Java registry changed: No.
- [x] Permission metadata changed: No.
- [x] Queue Skip implementation created: No.
- [x] Queue Rejoin implementation created: No.
- [x] Queue Display Screen implementation created: No.
- [x] Queue list/workbench implementation created: No.
- [x] WalkIn queue seating implementation created: No.
- [x] Auto assignment implementation created: No.
- [x] Cleaning implementation created: No.
- [x] Turnover implementation created: No.
- [x] No-show implementation created: No.
- [x] Cancellation implementation created: No.
- [x] Production config changed: No.
- [x] Production database connected: No.
- [x] Seed data inserted: No.

## 21. Final Gate

- [x] Actual modified files are limited to this contract round's two backend documents.
- [x] Seating From Called Queue contract is clear.
- [x] QueueTicket `called -> seated` is clear.
- [x] QueueTicket `seated` is selected over `completed`.
- [x] Reservation `arrived -> seated` is clear.
- [x] Seating source `queue_ticket_id` is clear.
- [x] Resource selection XOR is clear.
- [x] Table/TableGroup occupancy boundary is clear.
- [x] Idempotency behavior is clear.
- [x] AlreadySeated behavior is clear.
- [x] Future app key `reservation_queue` is clear.
- [x] Future permission `queue.seat` is clear.
- [x] API/UI/Migration remain out of scope.
- [x] Queue Skip/Rejoin/Display remain out of scope.
- [x] Auto assignment remains out of scope.
- [x] No-show, Cancellation, Cleaning, and Turnover remain out of scope.

Next recommended gate:

```text
Seating From Called Queue Application Implementation
```

Entry constraints for the next gate:

- Product Owner accepts this contract.
- Next implementation remains application-layer only unless a later request explicitly opens API, UI, migration, App Gate metadata, or runtime security changes.
- Queue Skip, Queue Rejoin, Queue Display Screen, Queue list/workbench, WalkIn queue seating, Auto assignment, No-show, Cancellation, Cleaning, Turnover, API, UI, App Gate registry change, migration, SQL, seed data, and production data remain outside the next gate unless separately approved.
