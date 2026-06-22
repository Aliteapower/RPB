# Reservation Arrived To Queue Vertical Slice Checklist V1

## 1. Read Inputs

- [x] Reservation Create API implementation report was read.
- [x] Reservation CheckIn API implementation report was read.
- [x] Reservation Arrived Direct Seating API implementation report was read.
- [x] Reservation staff end-to-end handoff was read.
- [x] Reservation staff end-to-end smoke review report was read.
- [x] Reservation Today View UI validation report was read.
- [x] Queue schema design was read.
- [x] V001 migration QueueGroup and QueueTicket sections were read.
- [x] QueueTicket domain was read.
- [x] QueueGroup domain was read.
- [x] QueueTicketStatus was read.
- [x] QueueTicketStateMachine was read.
- [x] QueueTicketRepositoryPort was read.
- [x] QueueGroupPolicy was read.
- [x] QueueOrderingPolicy was read.
- [x] CreateQueueTicketCommand was read.
- [x] QueueTicketEntity was read.
- [x] QueueGroupEntity was read.
- [x] Reservation domain was read.
- [x] ReservationStatus was read.
- [x] ReservationStateMachine was read.
- [x] ReservationRepositoryPort was read.
- [x] WalkIn Direct Seating contract and implementation report were read for contrast.
- [x] Reservation Arrived Direct Seating contract and implementation report were read for contrast.
- [x] App Gate operational handoff was read.
- [x] App Gate integration checklist was read.
- [x] App Gate permission metadata alignment was read.
- [x] `AppGateRequiredPermission.java` was read.
- [x] Governance documents were read.
- [x] Architecture document was read.
- [x] Reservation system skill document was read.

## 2. Starting Baseline

- [x] Reservation Create completed: Yes.
- [x] Reservation Create passed runtime/UI validation: Yes.
- [x] Reservation Create currently produces `status = confirmed`: Yes.
- [x] Reservation CheckIn completed: Yes.
- [x] Reservation CheckIn passed API validation: Yes.
- [x] Reservation CheckIn passed UI validation: Yes.
- [x] Reservation can currently reach `status = arrived`: Yes.
- [x] Reservation Arrived Direct Seating completed: Yes.
- [x] Reservation Arrived Direct Seating can move `arrived -> seated`: Yes.
- [x] Today View completed and validated: Yes.
- [x] Today View is read-only at current baseline: Yes.
- [x] Queue schema already exists: Yes.
- [x] QueueTicket can reference Reservation through `reservation_id`: Yes.
- [x] QueueTicket can reference WalkIn through `walk_in_id`: Yes.
- [x] QueueTicket has `source_type`: No.
- [x] Reservation status `queued` exists: No.
- [x] App Gate app key `reservation_queue` exists: Yes.
- [x] App Gate permission `reservation.queue` currently registered in Java: No.
- [x] Migration changed by this round: No.

## 3. Selected Vertical Slice

- [x] Selected slice is `Reservation Arrived To Queue`.
- [x] Input precondition is `reservation.status = arrived`.
- [x] Output Reservation status remains `arrived`.
- [x] Output creates existing `queue_tickets` record.
- [x] Output sets `queue_tickets.reservation_id`.
- [x] Output leaves `queue_tickets.walk_in_id = null`.
- [x] Output sets QueueTicket status to `waiting`.
- [x] Output writes BusinessEvent.
- [x] Output writes QueueTicket transition evidence when supported.
- [x] Output writes AuditLog.
- [x] Output completes IdempotencyRecord.
- [x] This checklist does not cover Reservation Arrived Direct Seating.

## 4. Scope Boundary

- [x] Only arrived Reservation to Queue is covered.
- [x] Reservation remains `arrived`: Yes.
- [x] QueueTicket expresses waiting state: Yes.
- [x] QueueGroup party-size selection is covered: Yes.
- [x] Backend-generated QueueTicket number is covered: Yes.
- [x] Queue Call designed: No.
- [x] Queue Skip designed: No.
- [x] Queue Rejoin designed: No.
- [x] Queue notification designed: No.
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
- [x] Reservation list/search/calendar designed: No.
- [x] Table map designed: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.

## 5. Business Object Boundary

- [x] Reservation remains separate from QueueTicket.
- [x] QueueTicket is not a Reservation status.
- [x] QueueTicket is the waiting record.
- [x] Reservation can be queued only after arrival.
- [x] QueueTicket source is exactly Reservation for this slice.
- [x] QueueTicket source is not WalkIn for this slice.
- [x] Seating remains outside this slice.
- [x] DiningTable remains outside this slice.
- [x] TableGroup occupancy remains outside this slice.
- [x] Cleaning remains downstream and is not started here.
- [x] Turnover remains outside this slice.
- [x] New `source_type` column designed: No.
- [x] New reservation queue mapping table designed: No.

## 6. Command Boundary

- [x] Command is `QueueArrivedReservationCommand`.
- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `reservationId`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] Command includes optional `partySizeGroup`.
- [x] Command includes optional `reasonCode`.
- [x] Command includes optional `note`.
- [x] Command excludes `queueTicketId`.
- [x] Command excludes client-provided `queueTicketNumber`.
- [x] Command excludes client-provided `ticketNumber`.
- [x] Command excludes `tableId`.
- [x] Command excludes `tableGroupId`.
- [x] Command excludes `seatingId`.
- [x] Command excludes `walkInId`.
- [x] Command excludes `calledAt`.
- [x] Command excludes `skippedAt`.
- [x] Command excludes `rejoinedAt`.
- [x] Command excludes `seatedAt`.
- [x] Command excludes `noShowAt`.
- [x] Command excludes `cancelledAt`.
- [x] Command excludes `cleaningId`.
- [x] Command excludes `turnoverId`.

## 7. Application Service Boundary

- [x] Service boundary is `ReservationArrivedToQueueApplicationService`.
- [x] Method is `queueArrivedReservation(QueueArrivedReservationCommand command)`.
- [x] Service validates command.
- [x] Service builds `StoreScope`.
- [x] Service checks idempotency.
- [x] Service validates Store access.
- [x] Service loads Reservation by Store scope and Reservation id.
- [x] Service validates Reservation status is `arrived`.
- [x] Service detects already-queued duplicate behavior.
- [x] Service selects QueueGroup.
- [x] Service validates QueueGroup compatibility.
- [x] Service generates QueueTicket number in backend.
- [x] Service assigns QueueTicket position.
- [x] Service creates QueueTicket with source Reservation.
- [x] Service sets QueueTicket status to `waiting`.
- [x] Service keeps Reservation status as `arrived`.
- [x] Service writes BusinessEvent.
- [x] Service writes QueueTicket transition evidence when supported.
- [x] Service writes AuditLog.
- [x] Service completes idempotency.
- [x] Service returns result.
- [x] Service does not parse API request.
- [x] Service does not call QueueTicket.
- [x] Service does not skip QueueTicket.
- [x] Service does not rejoin QueueTicket.
- [x] Service does not seat the party.
- [x] Service does not mutate DiningTable.
- [x] Service does not start Cleaning.
- [x] Service does not implement No-show or Cancellation.

## 8. Required Ports

- [x] `StoreRepositoryPort` required.
- [x] `ReservationRepositoryPort` required.
- [x] `QueueGroupRepositoryPort` required for future implementation if absent.
- [x] `QueueTicketRepositoryPort` required.
- [x] `BusinessEventRepositoryPort` required.
- [x] `StateTransitionLogRepositoryPort` required.
- [x] `AuditLogRepositoryPort` required.
- [x] `IdempotencyRepositoryPort` required.
- [x] `DiningTableRepositoryPort` introduced: No.
- [x] Seating `TableGroupRepositoryPort` introduced: No.
- [x] `TableLockRepositoryPort` introduced: No.
- [x] `SeatingRepositoryPort` introduced: No.
- [x] `CleaningRepositoryPort` introduced: No.
- [x] `TurnoverRepositoryPort` introduced: No.
- [x] Broad reporting repository introduced: No.

## 9. Required Rules / Policies / Validators

- [x] `StoreAccessPolicy` required.
- [x] `ReservationArrivedToQueueRule` required as new or supplemented rule.
- [x] `ReservationStateMachine` required only to confirm no Reservation queue transition.
- [x] `QueueGroupPolicy` required.
- [x] `QueueGroupSelectionRule` required as new or supplemented rule.
- [x] `QueueTicketNumberPolicy` required as new or supplemented rule.
- [x] `QueueTicketStateMachine` required for initial waiting state.
- [x] `QueueOrderingPolicy` required.
- [x] `AuditRule` required.
- [x] `BusinessEventRule` required.
- [x] `StateTransitionRule` required when transition evidence is written.
- [x] `IdempotencyRule` required.
- [x] `QueueCallingRule` introduced: No.
- [x] `QueueSkipRule` introduced: No.
- [x] `QueueRejoinRule` introduced: No.
- [x] `SeatingSourceValidator` introduced: No.
- [x] `SeatingResourceValidator` introduced: No.
- [x] `TableAvailabilityRule` introduced: No.
- [x] `TableCapacityRule` introduced: No.
- [x] `TableLockRule` introduced: No.
- [x] `NoShowPolicy` introduced: No.
- [x] `CancellationPolicy` introduced: No.
- [x] `AutoAssignmentPolicy` introduced: No.

## 10. State / Event / Audit Boundary

- [x] Reservation status before is `arrived`.
- [x] Reservation status after remains `arrived`.
- [x] Reservation `arrived -> queued` transition exists: No.
- [x] Reservation `arrived -> seated` transition happens in this slice: No.
- [x] QueueTicket creation state is `none -> waiting`.
- [x] QueueTicket persisted status is `waiting`.
- [x] BusinessEvent `reservation.queued` is required.
- [x] BusinessEvent `queue_ticket.created` is required.
- [x] Audit operation is `reservation.queue`.
- [x] Failure audit operation is `reservation.queue.failed`.
- [x] StateTransitionLog for Reservation `arrived -> queued` is written: No.
- [x] StateTransitionLog for QueueTicket `none -> waiting` is documented when supported.
- [x] Raw DB exception exposure allowed: No.

## 11. QueueTicket Source Boundary

- [x] Uses existing `queue_tickets` table.
- [x] `queue_tickets.reservation_id` is populated.
- [x] `queue_tickets.walk_in_id` remains null.
- [x] `queue_tickets.status = waiting` for fresh success.
- [x] `queue_tickets.ticket_number` is generated by backend.
- [x] `queue_tickets.party_size` comes from Reservation party size.
- [x] `queue_tickets.business_date` comes from Reservation business date for V1.
- [x] `queue_tickets.queue_group_id` references active Store QueueGroup.
- [x] `source_type` created: No.
- [x] New queue source table created: No.

## 12. QueueGroup Boundary

- [x] QueueGroup is Store-scoped.
- [x] Default party-size groups are documented as 1-2, 3-4, 5-6, 7+.
- [x] QueueGroup can be derived from Reservation party size.
- [x] Optional `partySizeGroup` can be validated.
- [x] QueueGroup must be active.
- [x] QueueGroup must belong to same Tenant and Store.
- [x] QueueGroup must cover Reservation party size.
- [x] Missing QueueGroup failure is covered.
- [x] Party-size mismatch failure is covered.
- [x] QueueGroup seed/migration created in this round: No.

## 13. QueueTicket Number / Position Boundary

- [x] QueueTicket number is backend-generated.
- [x] Client-provided ticket number is forbidden.
- [x] Persisted field remains integer `ticket_number`.
- [x] Number scope is Store + QueueGroup + business date.
- [x] V001 uniqueness constraint is respected.
- [x] Concurrency conflict handling is documented.
- [x] Queue position tail assignment is documented.
- [x] `Q-YYYYMMDD-XXXX` is deferred to future display/API decision.

## 14. App Gate Future Boundary

- [x] Future API app key is `reservation_queue`.
- [x] Future API permission is `reservation.queue`.
- [x] Future API annotation is documented as `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")`.
- [x] `reservation.queue` is documented as future permission to add.
- [x] API path selected in this contract: No.
- [x] App Gate Java registry modified in this round: No.
- [x] App Gate metadata modified in this round: No.
- [x] New app key created: No.
- [x] `queue.ticket.create` used for this Reservation action: No.
- [x] `reservation.seat` used for this action: No.
- [x] `reservation.arrived.queue` used: No.
- [x] Body `tenantId` trusted: No.
- [x] App Gate deny happens before business handler in future API.
- [x] App Gate deny mutates business state: No.
- [x] App Gate deny writes `APP_GATE_DENIED`.

## 15. Idempotency Boundary

- [x] Idempotency action is `queue_arrived_reservation`.
- [x] Store-scoped idempotency is used.
- [x] Request hash fields are defined.
- [x] Completed same hash replays.
- [x] In-progress same hash returns retry later.
- [x] Failed same hash requires new key.
- [x] Same key with different hash conflicts.
- [x] Completed replay creates duplicate QueueTicket: No.
- [x] Completed replay creates duplicate ticket number: No.
- [x] Completed replay duplicates events/transitions/audit: No.
- [x] Completed replay changes Reservation status: No.

## 16. Already Queued Boundary

- [x] Active QueueTicket lookup by Reservation source is required.
- [x] Minimum active status for V1 duplicate detection is `waiting`.
- [x] Defensive active statuses may include `called`, `skipped`, and `rejoined` if such data exists.
- [x] Terminal statuses are `seated`, `cancelled`, and `expired`.
- [x] Same completed idempotency key replays stored result.
- [x] Different key with matching active QueueTicket returns `alreadyQueued = true`.
- [x] Different key already-queued path completes new idempotency snapshot.
- [x] Already-queued path creates duplicate QueueTicket: No.
- [x] Already-queued path generates new ticket number: No.
- [x] Already-queued path duplicates events/transitions/audit: No.
- [x] Already-queued path changes Reservation status: No.

## 17. Failure Coverage

- [x] Store not found covered.
- [x] Store scope mismatch covered.
- [x] Store access denied covered.
- [x] Reservation not found covered.
- [x] Reservation status not arrived covered.
- [x] Reservation already queued covered.
- [x] Reservation seated covered.
- [x] Reservation cancelled covered.
- [x] Reservation no_show covered.
- [x] Reservation completed covered.
- [x] QueueGroup not found covered.
- [x] QueueGroup cannot be derived covered.
- [x] QueueGroup party-size mismatch covered.
- [x] Ticket number conflict covered.
- [x] Active QueueTicket conflict covered.
- [x] Idempotency conflict covered.
- [x] Idempotency in progress covered.
- [x] Failed idempotency requires new key covered.
- [x] Event write failure covered.
- [x] Transition write failure covered.
- [x] Audit write failure covered.
- [x] Persistence save failure covered.
- [x] Raw DB exception exposure allowed: No.

## 18. Test Contract

- [x] Arrived Reservation queued success test defined.
- [x] Reservation remains `arrived` test defined.
- [x] QueueTicket source Reservation test defined.
- [x] QueueTicket `walk_in_id = null` test defined.
- [x] QueueTicket status `waiting` test defined.
- [x] QueueGroup derived by party size test defined.
- [x] Optional partySizeGroup validation test defined.
- [x] Backend-generated ticket number test defined.
- [x] Ticket number uniqueness/concurrency test defined.
- [x] Queue position assignment test defined.
- [x] Events written test defined.
- [x] Transition evidence test defined when supported.
- [x] Audit written test defined.
- [x] Idempotency completed test defined.
- [x] Completed replay test defined.
- [x] In-progress retry-later test defined.
- [x] Failed-key requires new key test defined.
- [x] Hash conflict test defined.
- [x] Already-queued no duplicate test defined.
- [x] Boundary no Seating test defined.
- [x] Boundary no DiningTable mutation test defined.
- [x] Boundary no Queue Call/Skip/Rejoin test defined.
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
- [x] Seating implementation created: No.
- [x] Table assignment implementation created: No.
- [x] Cleaning implementation created: No.
- [x] Turnover implementation created: No.
- [x] No-show implementation created: No.
- [x] Cancellation implementation created: No.
- [x] Reservation list/search/calendar created: No.
- [x] Table map created: No.
- [x] Production config changed: No.
- [x] Production database connected: No.
- [x] Seed data inserted: No.

## 20. Final Gate

- [x] Actual modified files are limited to this contract round's two backend documents.
- [x] Reservation Arrived To Queue contract is clear.
- [x] Reservation status remains `arrived` is clear.
- [x] QueueTicket `waiting` state is clear.
- [x] Existing `queue_tickets.reservation_id` reuse is clear.
- [x] No `source_type` migration is clear.
- [x] QueueGroup party-size selection is clear.
- [x] Backend-generated ticket number is clear.
- [x] App key `reservation_queue` is clear.
- [x] Future permission `reservation.queue` is clear.
- [x] Idempotency behavior is clear.
- [x] Already-queued behavior is clear.
- [x] API/UI/Migration remain out of scope.
- [x] Queue Call/Skip/Rejoin remain out of scope.
- [x] Seating and table assignment remain out of scope.
- [x] No-show and Cancellation remain out of scope.

Next recommended gate:

```text
Reservation Arrived To Queue Application Implementation
```

Entry constraints for the next gate:

- Product Owner accepts this contract.
- Next implementation remains application-layer only unless a later request explicitly opens API, UI, migration, App Gate metadata, or runtime security changes.
- Queue Call, Queue Skip, Queue Rejoin, Seating, Table assignment, Cleaning, Turnover, No-show, Cancellation, Reservation list/search/calendar, Table map, and auto assignment remain outside the next gate unless separately approved.
