# Reservation Arrived Direct Seating Vertical Slice Checklist V1

## 1. Read Inputs

- [x] Reservation Create API contract was read.
- [x] Reservation Create API implementation report was read.
- [x] Reservation Create UI validation report was read.
- [x] Store Staff Reservation Create handoff was read.
- [x] Reservation CheckIn application contract was read.
- [x] Reservation CheckIn application implementation report was read.
- [x] Reservation CheckIn API contract was read.
- [x] Reservation CheckIn API implementation report was read.
- [x] Reservation CheckIn UI validation report was read.
- [x] Reservation CheckIn local runtime security fix report was read.
- [x] WalkIn Direct Seating application contract was read.
- [x] WalkIn Direct Seating application implementation report was read.
- [x] WalkIn Direct Seating API implementation report was read.
- [x] Store Staff closed-loop runtime smoke report was read.
- [x] Cleaning Complete application contract was read.
- [x] Cleaning Complete application implementation report was read.
- [x] Cleaning Complete API implementation report was read.
- [x] App Gate operational handoff was read.
- [x] App Gate integration checklist was read.
- [x] App Gate new slice template was read.
- [x] App Gate permission metadata alignment was read.
- [x] App Gate permission metadata alignment report was read.
- [x] `AppGateRequiredPermission.java` was read.
- [x] Governance documents were read.
- [x] Architecture document was read.
- [x] Reservation system skill document was read.
- [x] Schema design was read.
- [x] V001 migration was read.
- [x] V002 App Gate migration was read.

## 2. Starting Baseline

- [x] Reservation Create completed: Yes.
- [x] Reservation Create passed runtime/UI validation: Yes.
- [x] Reservation Create currently produces `status = confirmed`: Yes.
- [x] Reservation CheckIn completed: Yes.
- [x] Reservation CheckIn passed API validation: Yes.
- [x] Reservation CheckIn passed UI validation: Yes.
- [x] Reservation CheckIn passed local runtime security validation: Yes.
- [x] Reservation can currently reach `status = arrived`: Yes.
- [x] WalkIn Direct Seating has reusable Seating logic: Yes.
- [x] Cleaning Complete can release occupied table resources: Yes.
- [x] App Gate `reservation_queue` exists: Yes.
- [x] App Gate permission `reservation.seat` currently registered in Java: No.
- [x] Migration changed by this round: No.

## 3. Selected Vertical Slice

- [x] Selected slice is `Reservation Arrived Direct Seating`.
- [x] Input precondition is `reservation.status = arrived`.
- [x] Output Reservation status is `seated`.
- [x] Output creates existing `seatings` record.
- [x] Output creates existing `seating_resources` record.
- [x] Output makes the selected table or TableGroup member tables `occupied`.
- [x] Output writes BusinessEvent.
- [x] Output writes StateTransitionLog.
- [x] Output writes AuditLog.
- [x] Output completes IdempotencyRecord.
- [x] This checklist does not cover Reservation Arrived To Queue.

## 4. Scope Boundary

- [x] Only arrived Reservation direct seating is covered.
- [x] Queue designed: No.
- [x] QueueTicket creation designed: No.
- [x] Queue calling designed: No.
- [x] Queue rejoin designed: No.
- [x] Queue skip designed: No.
- [x] Auto assignment designed: No.
- [x] Recommended table designed: No.
- [x] Table ranking designed: No.
- [x] No-show designed: No.
- [x] Cancellation designed: No.
- [x] Cleaning designed: No.
- [x] Turnover designed: No.
- [x] Reservation list/search/calendar designed: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.

## 5. Business Object Boundary

- [x] Reservation remains separate from Seating.
- [x] CheckIn remains separate from Seating.
- [x] QueueTicket remains optional and outside this slice.
- [x] Seating is the occupancy record.
- [x] Seating source is exactly Reservation for this slice.
- [x] Seating source is not QueueTicket for this slice.
- [x] Seating source is not WalkIn for this slice.
- [x] SeatingResource target is exactly one DiningTable or TableGroup.
- [x] Cleaning remains downstream of Seating and is not started here.
- [x] Turnover remains derived from Seating plus Cleaning and is not created here.
- [x] New `reservation_seatings` table designed: No.
- [x] New seating table designed: No.

## 6. Command Boundary

- [x] Command is `SeatArrivedReservationCommand`.
- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `reservationId`.
- [x] Command includes optional `tableId`.
- [x] Command includes optional `tableGroupId`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] Command includes optional `overrideReasonCode`.
- [x] Command includes optional `overrideNote`.
- [x] Command includes optional `note`.
- [x] `tableId` and `tableGroupId` exactly-one rule is clear.
- [x] Both resource ids present is rejected.
- [x] Neither resource id present is rejected.
- [x] Command excludes `queueTicketId`.
- [x] Command excludes `walkInId`.
- [x] Command excludes `checkInAt`.
- [x] Command excludes `noShowAt`.
- [x] Command excludes `cancelledAt`.
- [x] Command excludes `cleaningId`.
- [x] Command excludes `turnoverId`.

## 7. Application Service Boundary

- [x] Service boundary is `ReservationArrivedDirectSeatingApplicationService`.
- [x] Method is `seatArrivedReservation(SeatArrivedReservationCommand command)`.
- [x] Service validates command.
- [x] Service builds `StoreScope`.
- [x] Service checks idempotency.
- [x] Service validates Store access.
- [x] Service loads Reservation by Store scope and Reservation id.
- [x] Service validates Reservation status is `arrived`.
- [x] Service validates selected resource.
- [x] Service validates resource availability.
- [x] Service validates capacity against Reservation party size.
- [x] Service validates lock conflict.
- [x] Service creates Seating with source Reservation.
- [x] Service creates SeatingResource.
- [x] Service updates table or TableGroup member statuses to `occupied`.
- [x] Service transitions Reservation `arrived -> seated`.
- [x] Service writes BusinessEvent.
- [x] Service writes StateTransitionLog.
- [x] Service writes AuditLog.
- [x] Service completes idempotency.
- [x] Service returns result.
- [x] Service does not parse API request.
- [x] Service does not create Queue.
- [x] Service does not start Cleaning.
- [x] Service does not implement No-show or Cancellation.

## 8. Required Ports

- [x] `StoreRepositoryPort` required.
- [x] `ReservationRepositoryPort` required.
- [x] `DiningTableRepositoryPort` required.
- [x] `TableGroupRepositoryPort` required.
- [x] `TableLockRepositoryPort` required.
- [x] `SeatingRepositoryPort` required.
- [x] `BusinessEventRepositoryPort` required.
- [x] `StateTransitionLogRepositoryPort` required.
- [x] `AuditLogRepositoryPort` required.
- [x] `IdempotencyRepositoryPort` required.
- [x] `QueueTicketRepositoryPort` introduced: No.
- [x] `CleaningRepositoryPort` introduced: No.
- [x] `TurnoverRepositoryPort` introduced: No.
- [x] Broad reporting repository introduced: No.

## 9. Required Rules / Policies / Validators

- [x] `StoreAccessPolicy` required.
- [x] `TableAvailabilityRule` required.
- [x] `TableCapacityRule` required.
- [x] `TableLockRule` required.
- [x] `TableAssignmentRule` reuse is limited to validation helpers.
- [x] `TableAssignmentRule` automatic assignment branch used: No.
- [x] `TableGroupValidationRule` required.
- [x] `SeatingSourceValidator` required.
- [x] `SeatingResourceValidator` required.
- [x] `DiningTableStateMachine` required.
- [x] `AuditRule` required.
- [x] `BusinessEventRule` required.
- [x] `StateTransitionRule` required.
- [x] `IdempotencyRule` required.
- [x] `ReservationArrivedSeatingRule` required as new or supplemented rule.
- [x] `ReservationStateMachine` required for `arrived -> seated`.
- [x] `QueueCallingRule` introduced: No.
- [x] `NoShowPolicy` introduced: No.
- [x] `CancellationPolicy` introduced: No.
- [x] `AutoAssignmentPolicy` introduced: No.

## 10. State / Event / Audit Boundary

- [x] Reservation transition is `arrived -> seated`.
- [x] Seating persisted status is `occupied`.
- [x] SeatingResource persisted status is `active`.
- [x] DiningTable business outcome is `available -> occupied`.
- [x] TableGroup member table statuses are covered.
- [x] Fixed TableGroup configuration is not mutated solely to represent occupancy.
- [x] Temporary TableGroup occupancy status is acknowledged as implementation policy.
- [x] BusinessEvent `reservation.seated` is required.
- [x] BusinessEvent `seating.created` is required.
- [x] BusinessEvent `table.occupied` is required.
- [x] Audit operation is `reservation.seat`.
- [x] Failure audit operation is `reservation.seat.failed`.
- [x] StateTransitionLog includes Reservation `arrived -> seated`.
- [x] StateTransitionLog includes table or member table occupancy.
- [x] StateTransitionLog clarifies TableGroup/member table transitions.

## 11. Seating Resource Boundary

- [x] Uses existing `seatings` table.
- [x] Uses existing `seating_resources` table.
- [x] `seatings.reservation_id` is populated.
- [x] `seatings.queue_ticket_id` remains null.
- [x] `seatings.walk_in_id` remains null.
- [x] `seatings.party_size_snapshot` comes from Reservation party size.
- [x] `seating_resources.resource_type` is `dining_table` or `table_group`.
- [x] `seating_resources.table_id` and `table_group_id` exactly-one rule is clear.
- [x] `seating_resources.status = active` for fresh success.
- [x] Active resource occupancy uniqueness is part of the contract.
- [x] `reservation_seatings` created: No.
- [x] New seating table created: No.

## 12. Table / TableGroup Boundary

- [x] `tableId` must belong to Store scope.
- [x] DiningTable must be `available`.
- [x] DiningTable capacity must fit Reservation party size.
- [x] DiningTable must not be locked.
- [x] DiningTable must not be occupied.
- [x] DiningTable must not be cleaning.
- [x] DiningTable must not be inactive.
- [x] DiningTable becomes `occupied` on success.
- [x] `tableGroupId` must belong to Store scope.
- [x] TableGroup must be valid.
- [x] TableGroup capacity must fit Reservation party size.
- [x] TableGroup active members must be valid.
- [x] TableGroup member tables must be available.
- [x] TableGroup member tables must not be locked.
- [x] TableGroup member tables become `occupied` on success.

## 13. App Gate Future Boundary

- [x] Future API app key is `reservation_queue`.
- [x] Future API permission is `reservation.seat`.
- [x] Future API annotation is documented as `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")`.
- [x] `reservation.seat` is documented as future permission to add.
- [x] App Gate Java registry modified in this round: No.
- [x] App Gate metadata modified in this round: No.
- [x] New app key created: No.
- [x] `reservation.direct_seat` used: No.
- [x] `seating.reservation.create` used: No.
- [x] `reservation.arrived.seat` used: No.
- [x] Body `tenantId` trusted: No.
- [x] App Gate deny happens before business handler in future API.
- [x] App Gate deny mutates business state: No.
- [x] App Gate deny writes `APP_GATE_DENIED`.

## 14. Idempotency Boundary

- [x] Idempotency action is `seat_arrived_reservation`.
- [x] Store-scoped idempotency is used.
- [x] Request hash fields are defined.
- [x] Completed same hash replays.
- [x] In-progress same hash returns retry later.
- [x] Failed same hash requires new key.
- [x] Same key with different hash conflicts.
- [x] Completed replay creates duplicate Seating: No.
- [x] Completed replay creates duplicate SeatingResource: No.
- [x] Completed replay duplicates events/transitions/audit: No.
- [x] Completed replay changes table status again: No.

## 15. Already Seated Boundary

- [x] Same completed idempotency key replays stored result.
- [x] Different key with matching existing Reservation-source Seating returns `alreadySeated = true`.
- [x] Different key already-seated path completes new idempotency snapshot.
- [x] Already-seated path creates duplicate Seating: No.
- [x] Already-seated path creates duplicate SeatingResource: No.
- [x] Already-seated path duplicates events/transitions/audit: No.
- [x] Already-seated path changes table or group member status again: No.
- [x] Already seated without matching active Seating is called out as consistency error.

## 16. Failure Coverage

- [x] Store not found covered.
- [x] Store scope mismatch covered.
- [x] Store access denied covered.
- [x] Reservation not found covered.
- [x] Reservation status not arrived covered.
- [x] Reservation already seated covered.
- [x] Reservation cancelled covered.
- [x] Reservation no_show covered.
- [x] Reservation completed covered.
- [x] Table not found covered.
- [x] Table not available covered.
- [x] Table capacity insufficient covered.
- [x] Table locked covered.
- [x] TableGroup not found covered.
- [x] TableGroup invalid covered.
- [x] TableGroup member unavailable covered.
- [x] TableGroup capacity insufficient covered.
- [x] Both tableId and tableGroupId covered.
- [x] Neither tableId nor tableGroupId covered.
- [x] Idempotency conflict covered.
- [x] Idempotency in progress covered.
- [x] Failed idempotency requires new key covered.
- [x] Event write failure covered.
- [x] Transition write failure covered.
- [x] Audit write failure covered.
- [x] Persistence save failure covered.
- [x] Raw DB exception exposure allowed: No.

## 17. Test Contract

- [x] Success single table test defined.
- [x] Success TableGroup test defined.
- [x] Reservation becomes `seated` test defined.
- [x] Seating source Reservation test defined.
- [x] SeatingResource active test defined.
- [x] Table occupied test defined.
- [x] TableGroup member occupied test defined.
- [x] Events written test defined.
- [x] Transitions written test defined.
- [x] Audit written test defined.
- [x] Idempotency completed test defined.
- [x] Completed replay test defined.
- [x] In-progress retry-later test defined.
- [x] Failed-key requires new key test defined.
- [x] Hash conflict test defined.
- [x] Already-seated no duplicate test defined.
- [x] Boundary no QueueTicket test defined.
- [x] Boundary no Cleaning test defined.
- [x] Boundary no Turnover test defined.
- [x] Boundary no No-show test defined.
- [x] Boundary no Cancellation test defined.
- [x] Boundary no Migration test defined.
- [x] Boundary no API/UI test defined.
- [x] Test code written in this round: No.

## 18. Forbidden Artifact Check

- [x] Java Application Service created: No.
- [x] Repository implementation created: No.
- [x] Controller created: No.
- [x] API DTO created: No.
- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] Flyway migration created or modified: No.
- [x] SQL file created or modified: No.
- [x] Database structure changed: No.
- [x] Queue implementation created: No.
- [x] No-show implementation created: No.
- [x] Cancellation implementation created: No.
- [x] Reservation list/search/calendar created: No.
- [x] Production config changed: No.
- [x] Production database connected: No.
- [x] Seed data inserted: No.

## 19. Final Gate

- [x] Actual modified files are limited to this contract round's two backend documents.
- [x] Reservation arrived -> seated contract is clear.
- [x] Manual direct seating only is clear.
- [x] No Queue is clear.
- [x] No auto assignment is clear.
- [x] No No-show is clear.
- [x] No Cancellation is clear.
- [x] No Cleaning is clear.
- [x] No Turnover is clear.
- [x] Existing `seatings` and `seating_resources` reuse is clear.
- [x] `tableId` / `tableGroupId` exactly-one rule is clear.
- [x] App key `reservation_queue` is clear.
- [x] Future permission `reservation.seat` is clear.
- [x] Idempotency behavior is clear.
- [x] Already-seated behavior is clear.
- [x] API/UI/Migration remain out of scope.

Next recommended gate:

```text
Reservation Arrived Direct Seating Application Implementation
```

Entry constraints for the next gate:

- Product Owner accepts this contract.
- Next implementation remains application-layer only unless a later request explicitly opens API, UI, migration, App Gate metadata, or runtime security changes.
- Queue, No-show, Cancellation, Cleaning, Turnover, Reservation list/search/calendar, and auto assignment remain outside the next gate unless separately approved.
