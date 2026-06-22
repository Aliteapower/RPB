# Queue Call Vertical Slice Checklist V1

## 1. Read Inputs

- [x] Queue Call request attachment was read.
- [x] Reservation Arrived To Queue application contract was read.
- [x] Reservation Arrived To Queue application implementation report was read.
- [x] Reservation Arrived To Queue API contract was read.
- [x] Reservation Arrived To Queue API implementation report was read.
- [x] Reservation Arrived To Queue UI contract was read.
- [x] Reservation Arrived To Queue UI implementation report was read.
- [x] Reservation Arrived To Queue UI validation report was read.
- [x] Queue schema design was read.
- [x] V001 migration QueueTicket, QueueGroup, StorePolicy, idempotency, audit, and transition sections were checked.
- [x] QueueTicket domain was read.
- [x] QueueGroup domain was read.
- [x] QueueTicketStatus was read.
- [x] QueueTicketStateMachine was read.
- [x] QueueTicketRepositoryPort was read.
- [x] QueueTicketEntity was read.
- [x] DefaultQueueTicketMapper was read.
- [x] QueueTicketPersistenceAdapter was read.
- [x] QueueTicketJpaRepository was read.
- [x] StorePolicy domain was read.
- [x] StorePolicyRepositoryPort was read.
- [x] StoreRepositoryPort was read.
- [x] ReservationRepositoryPort was read.
- [x] BusinessEventRepositoryPort was read.
- [x] StateTransitionLogRepositoryPort was read.
- [x] AuditLogRepositoryPort was read.
- [x] IdempotencyRepositoryPort was read.
- [x] Existing Staff handoff and smoke review were read.
- [x] Reservation Today View UI validation report was read.
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
- [x] Reservation status remains `arrived` after entering Queue: Yes.
- [x] QueueTicket enum contains `waiting`: Yes.
- [x] QueueTicket enum contains `called`: Yes.
- [x] QueueTicket enum contains `skipped`: Yes.
- [x] QueueTicket enum contains `rejoined`: Yes.
- [x] QueueTicket enum contains `seated`: Yes.
- [x] QueueTicket enum contains `cancelled`: Yes.
- [x] QueueTicket enum contains `expired`: Yes.
- [x] QueueTicket enum contains `completed`: No.
- [x] QueueTicketStateMachine allows `waiting -> called`: Yes.
- [x] V001 `queue_tickets.called_at` exists: Yes.
- [x] V001 `queue_tickets.expires_at` exists: Yes.
- [x] V001 `store_policies.queue_call_hold_minutes` exists: Yes.
- [x] StorePolicy default queue call hold is 3 minutes: Yes.
- [x] Current QueueTicket domain exposes `calledAt`: No.
- [x] Current QueueTicket mapper preserves `called_at`: No.
- [x] Queue Call implementation exists: No.
- [x] Queue Skip implementation exists: No.
- [x] Queue Rejoin implementation exists: No.
- [x] Queue Display Screen exists: No.
- [x] Seating from Queue exists: No.

## 3. Selected Vertical Slice

- [x] Selected slice is `Queue Call`.
- [x] Input precondition is `queue_ticket.status = waiting`.
- [x] Output QueueTicket status is `called`.
- [x] Output persists `calledAt`.
- [x] Output persists `holdUntilAt` through existing `queue_tickets.expires_at`.
- [x] Output writes BusinessEvent.
- [x] Output writes QueueTicket transition evidence.
- [x] Output writes AuditLog.
- [x] Output completes IdempotencyRecord.
- [x] This checklist does not cover Queue Skip.
- [x] This checklist does not cover Queue Rejoin.
- [x] This checklist does not cover Seating from Queue.

## 4. Scope Boundary

- [x] Only `waiting -> called` is covered.
- [x] Reservation remains `arrived`: Yes.
- [x] QueueTicket expresses called state: Yes.
- [x] Queue call hold policy is covered: Yes.
- [x] AlreadyCalled behavior is covered: Yes.
- [x] Queue Skip designed: No.
- [x] Queue Rejoin designed: No.
- [x] Queue Display designed: No.
- [x] Queue list/workbench designed: No.
- [x] Seating designed: No.
- [x] Table assignment designed: No.
- [x] Table lock designed: No.
- [x] Auto assignment designed: No.
- [x] Recommended table designed: No.
- [x] Table ranking designed: No.
- [x] Cleaning designed: No.
- [x] Turnover designed: No.
- [x] No-show designed: No.
- [x] Cancellation designed: No.
- [x] Reservation state mutation designed: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.

## 5. Business Object Boundary

- [x] QueueTicket is kept separate from Reservation.
- [x] Queue Call is a QueueTicket state transition, not a Reservation state.
- [x] Reservation status `called` is not introduced.
- [x] Reservation state machine is not modified.
- [x] Seating remains outside this slice.
- [x] DiningTable remains outside this slice.
- [x] TableGroup occupancy remains outside this slice.
- [x] Cleaning remains downstream and is not started here.
- [x] Turnover remains outside this slice.
- [x] No-show remains outside this slice.
- [x] Cancellation remains outside this slice.

## 6. QueueTicket Status Decision

- [x] Fresh success transition is `waiting -> called`.
- [x] `called` is not a fresh-call source state.
- [x] `called` with durable call evidence returns `alreadyCalled = true`.
- [x] `called` missing `called_at` or `expires_at` returns `QUEUE_CALL_EVIDENCE_INCOMPLETE`.
- [x] `skipped` is rejected.
- [x] `rejoined` is rejected.
- [x] `seated` is rejected.
- [x] `cancelled` is rejected.
- [x] `expired` is rejected.
- [x] Future `completed` status, if added, must be rejected unless a later contract says otherwise.

## 7. Reservation Status Decision

- [x] Related Reservation is loaded when `queue_ticket.reservation_id` is present.
- [x] Related Reservation must remain `arrived`.
- [x] Related Reservation not found is a failure.
- [x] Related Reservation not `arrived` is a failure.
- [x] Reservation `arrived -> called` transition is written: No.
- [x] Reservation `arrived -> seated` transition is written: No.
- [x] Reservation status mutation designed: No.
- [x] WalkIn source mutation designed: No.

## 8. Call Hold Policy

- [x] StorePolicy contains `queueCallHoldMinutes`.
- [x] V001 contains `queue_call_hold_minutes`.
- [x] V001 default is 3 minutes.
- [x] `holdUntilAt = calledAt + queueCallHoldMinutes`.
- [x] Existing `queue_tickets.expires_at` is used as durable hold-until field.
- [x] Missing Store policy falls back to 3 minutes.
- [x] Invalid non-positive hold minutes is a failure.
- [x] No new hold field or migration is designed.

## 9. Command Boundary

- [x] Command is `CallQueueTicketCommand`.
- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `queueTicketId`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] Command includes optional `calledAt`.
- [x] Command includes optional `reasonCode`.
- [x] Command includes optional `note`.
- [x] Command excludes `reservationStatus`.
- [x] Command excludes `tableId`.
- [x] Command excludes `tableGroupId`.
- [x] Command excludes `seatingId`.
- [x] Command excludes `cleaningId`.
- [x] Command excludes `turnoverId`.
- [x] Command excludes `skipReason`.
- [x] Command excludes `rejoinReason`.
- [x] Command excludes client-provided `status`.

## 10. Application Service Boundary

- [x] Service boundary is `QueueCallApplicationService`.
- [x] Method is `callQueueTicket(CallQueueTicketCommand command)`.
- [x] Service validates command.
- [x] Service builds `StoreScope`.
- [x] Service checks idempotency.
- [x] Service validates Store access.
- [x] Service loads QueueTicket by Store scope and id.
- [x] Service validates QueueTicket status is `waiting` for fresh success.
- [x] Service detects already-called duplicate behavior.
- [x] Service loads related Reservation when present.
- [x] Service resolves `calledAt`.
- [x] Service resolves `holdUntilAt`.
- [x] Service saves QueueTicket.
- [x] Service writes BusinessEvent.
- [x] Service writes StateTransitionLog.
- [x] Service writes AuditLog.
- [x] Service completes idempotency.
- [x] Service returns result.
- [x] Service does not parse API request.
- [x] Service does not skip QueueTicket.
- [x] Service does not rejoin QueueTicket.
- [x] Service does not seat the party.
- [x] Service does not mutate DiningTable.
- [x] Service does not start Cleaning.
- [x] Service does not implement No-show or Cancellation.

## 11. Required Ports

- [x] `StoreRepositoryPort` required.
- [x] `QueueTicketRepositoryPort` required.
- [x] `ReservationRepositoryPort` required only when ticket has Reservation source.
- [x] `BusinessEventRepositoryPort` required.
- [x] `StateTransitionLogRepositoryPort` required.
- [x] `AuditLogRepositoryPort` required.
- [x] `IdempotencyRepositoryPort` required.
- [x] `StorePolicyRepositoryPort` optional if current policy is not loaded through `StoreRepositoryPort`.
- [x] `DiningTableRepositoryPort` introduced: No.
- [x] Seating `TableGroupRepositoryPort` introduced: No.
- [x] `TableLockRepositoryPort` introduced: No.
- [x] `SeatingRepositoryPort` introduced: No.
- [x] `CleaningRepositoryPort` introduced: No.
- [x] `TurnoverRepositoryPort` introduced: No.

## 12. Required Rules / Policies / Validators

- [x] `StoreAccessPolicy` required.
- [x] `QueueCallRule` required.
- [x] `QueueTicketStateMachine` required.
- [x] `QueueCallHoldPolicy` required.
- [x] `AuditRule` required.
- [x] `BusinessEventRule` required.
- [x] `StateTransitionRule` required.
- [x] `IdempotencyRule` required.
- [x] `TableAssignmentRule` introduced: No.
- [x] `SeatingSourceValidator` introduced: No.
- [x] `SeatingResourceValidator` introduced: No.
- [x] `DiningTableStateMachine` introduced: No.
- [x] `NoShowPolicy` introduced: No.
- [x] `CancellationPolicy` introduced: No.
- [x] `AutoAssignmentPolicy` introduced: No.

## 13. Event / Audit / Transition Boundary

- [x] BusinessEvent `queue_ticket.called` is required.
- [x] BusinessEvent `reservation.queue_called` is not required and not recommended for V1.
- [x] Audit operation is `queue.call`.
- [x] Failure audit operation is `queue.call.failed`.
- [x] StateTransitionLog target is `queue_ticket`.
- [x] StateTransitionLog transition is `waiting -> called`.
- [x] StateTransitionLog code is `queue_ticket.call`.
- [x] Reservation transition log is written: No.
- [x] Raw DB exception exposure allowed: No.

## 14. Idempotency Boundary

- [x] Idempotency action is `call_queue_ticket`.
- [x] Store-scoped idempotency is used.
- [x] Request hash fields are defined.
- [x] Missing `calledAt` hashes as an absent marker, not current time.
- [x] Completed same hash replays.
- [x] In-progress same hash returns retry later.
- [x] Failed same hash requires new key.
- [x] Same key with different hash conflicts.
- [x] Completed replay updates QueueTicket: No.
- [x] Completed replay duplicates events/transitions/audit: No.
- [x] Completed replay changes Reservation status: No.

## 15. AlreadyCalled Boundary

- [x] AlreadyCalled is success-like.
- [x] AlreadyCalled requires `status = called`.
- [x] AlreadyCalled requires existing `called_at`.
- [x] AlreadyCalled requires existing `expires_at`.
- [x] AlreadyCalled returns existing `calledAt`.
- [x] AlreadyCalled returns existing `holdUntilAt`.
- [x] AlreadyCalled creates duplicate BusinessEvent: No.
- [x] AlreadyCalled creates duplicate StateTransitionLog: No.
- [x] AlreadyCalled creates duplicate AuditLog: No.
- [x] AlreadyCalled mutates QueueTicket: No.

## 16. App Gate Future Boundary

- [x] Future API app key is `reservation_queue`.
- [x] Future API permission is `queue.call`.
- [x] Future API annotation is documented as `@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")`.
- [x] This contract does not use `reservation.queue`.
- [x] This contract does not use `reservation.call`.
- [x] This contract does not use `queue.ticket.call`.
- [x] API path selected in this contract: No.
- [x] App Gate Java registry modified in this round: No.
- [x] App Gate metadata modified in this round: No.
- [x] New app key created: No.
- [x] Body `tenantId` trusted: No.
- [x] App Gate deny happens before business handler in future API.
- [x] App Gate deny mutates business state: No.
- [x] App Gate deny writes `APP_GATE_DENIED`.

## 17. Failure Coverage

- [x] Store not found covered.
- [x] Store scope mismatch covered.
- [x] Store access denied covered.
- [x] QueueTicket not found covered.
- [x] QueueTicket status not waiting covered.
- [x] QueueTicket already called covered.
- [x] QueueTicket skipped covered.
- [x] QueueTicket rejoined covered.
- [x] QueueTicket seated covered.
- [x] QueueTicket cancelled covered.
- [x] QueueTicket expired covered.
- [x] Related Reservation not found covered.
- [x] Related Reservation not arrived covered.
- [x] Idempotency conflict covered.
- [x] Idempotency in progress covered.
- [x] Failed idempotency requires new key covered.
- [x] Event write failure covered.
- [x] Transition write failure covered.
- [x] Audit write failure covered.
- [x] Persistence save failure covered.
- [x] Raw DB exception exposure allowed: No.

## 18. Test Contract

- [x] Waiting QueueTicket called success test defined.
- [x] QueueTicket status `called` test defined.
- [x] Reservation remains `arrived` test defined.
- [x] `calledAt` returned test defined.
- [x] `holdUntilAt` returned test defined.
- [x] Store policy hold duration test defined.
- [x] Default 3-minute fallback test defined.
- [x] Event written test defined.
- [x] Transition evidence test defined.
- [x] Audit written test defined.
- [x] Idempotency completed test defined.
- [x] Completed replay test defined.
- [x] In-progress retry-later test defined.
- [x] Failed-key requires new key test defined.
- [x] Hash conflict test defined.
- [x] AlreadyCalled no duplicate test defined.
- [x] Boundary no Seating test defined.
- [x] Boundary no DiningTable mutation test defined.
- [x] Boundary no Queue Skip/Rejoin test defined.
- [x] Boundary no Cleaning test defined.
- [x] Boundary no Turnover test defined.
- [x] Boundary no No-show test defined.
- [x] Boundary no Cancellation test defined.
- [x] Boundary no Migration test defined.
- [x] Boundary no API/UI test defined.
- [x] Test code written in this round: No.

## 19. Forbidden Artifact Check

- [x] Java Application Service created: No.
- [x] Repository implementation created: No.
- [x] Controller created: No.
- [x] API DTO created: No.
- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] Router entry created: No.
- [x] Flyway migration created or modified: No.
- [x] SQL file created or modified: No.
- [x] Database structure changed: No.
- [x] App Gate Java registry changed: No.
- [x] Permission metadata changed: No.
- [x] Queue Call implementation created: No.
- [x] Queue Skip implementation created: No.
- [x] Queue Rejoin implementation created: No.
- [x] Queue Display Screen implementation created: No.
- [x] Seating implementation created: No.
- [x] Table assignment implementation created: No.
- [x] Cleaning implementation created: No.
- [x] Turnover implementation created: No.
- [x] No-show implementation created: No.
- [x] Cancellation implementation created: No.
- [x] Reservation state machine changed: No.
- [x] Production config changed: No.
- [x] Production database connected: No.
- [x] Seed data inserted: No.

## 20. Final Gate

- [x] Actual modified files are limited to this contract round's two backend documents.
- [x] Queue Call contract is clear.
- [x] QueueTicket `waiting -> called` is clear.
- [x] Reservation status remains `arrived` is clear.
- [x] Call hold policy is clear.
- [x] Existing `queue_tickets.called_at` and `expires_at` reuse is clear.
- [x] No new migration is clear.
- [x] App key `reservation_queue` is clear.
- [x] Future permission `queue.call` is clear.
- [x] Idempotency behavior is clear.
- [x] AlreadyCalled behavior is clear.
- [x] API/UI/Migration remain out of scope.
- [x] Queue Skip/Rejoin remain out of scope.
- [x] Seating and table assignment remain out of scope.
- [x] No-show and Cancellation remain out of scope.

Next recommended gate:

```text
Queue Call Application Implementation
```

Entry constraints for the next gate:

- Product Owner accepts this contract.
- Next implementation remains application-layer only unless a later request explicitly opens API, UI, migration, App Gate metadata, or runtime security changes.
- Queue Skip, Queue Rejoin, Queue Display Screen, Seating from Queue, Table assignment, Cleaning, Turnover, No-show, Cancellation, Queue list/workbench, API, UI, and migration remain outside the next gate unless separately approved.
