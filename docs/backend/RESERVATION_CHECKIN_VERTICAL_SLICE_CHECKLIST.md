# Reservation CheckIn Vertical Slice Checklist V1

## 1. Slice Selection

- [x] Only Reservation CheckIn is selected.
- [x] CheckIn means customer arrival confirmation for an existing Reservation.
- [x] CheckIn changes Reservation status only from `confirmed` to `arrived`.
- [x] CheckIn writes event, transition, audit, and idempotency boundaries.
- [x] CheckIn does not continue into queue, seating, table assignment, no-show, cancellation, cleaning, or turnover.

## 2. Required Starting Confirmation

- [x] Reservation Create completed: Yes.
- [x] Reservation Create passed UI validation: Yes.
- [x] Reservation Create passed API integration validation: Yes.
- [x] Reservation currently creates `status = confirmed`: Yes.
- [x] CheckIn currently implemented: No.
- [x] Queue currently implemented for Reservation arrival: No.
- [x] Seating from Reservation currently implemented: No.
- [x] No-show currently implemented: No.
- [x] Cancellation currently implemented: No.
- [x] Migration changed by this round: No.
- [x] Production database touched by this round: No.
- [x] App Gate Operational Handoff completed: Yes.

## 3. Scope Boundary

- [x] Does this checklist cover only CheckIn? Yes.
- [x] Does this checklist avoid Queue? Yes.
- [x] Does this checklist avoid Seating? Yes.
- [x] Does this checklist avoid Table assignment? Yes.
- [x] Does this checklist avoid No-show? Yes.
- [x] Does this checklist avoid Cancellation? Yes.
- [x] Does this checklist avoid Reservation list/search? Yes.
- [x] Does this checklist avoid Reservation calendar? Yes.
- [x] Does this checklist avoid API implementation? Yes.
- [x] Does this checklist avoid UI implementation? Yes.

## 4. Business Object Boundary

- [x] CheckIn is a V1 business event, not a primary business entity.
- [x] `CheckInEntity` designed: No.
- [x] `check_ins` table designed: No.
- [x] New App Gate app key designed: No.
- [x] Reservation remains separate from QueueTicket.
- [x] CheckIn remains separate from Seating.
- [x] Seating remains responsible for table occupancy, not CheckIn.
- [x] QueueTicket remains created only by a later queue decision, not by CheckIn.

## 5. Command Boundary

- [x] Command is `CheckInReservationCommand`.
- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `reservationId`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] Command includes optional `arrivedAt`.
- [x] Command includes optional `reasonCode`.
- [x] Command includes optional `note`.
- [x] Command excludes `queueTicketId`.
- [x] Command excludes `seatingId`.
- [x] Command excludes `tableId`.
- [x] Command excludes `tableGroupId`.

## 6. Application Service Boundary

- [x] Service boundary is `ReservationCheckInApplicationService`.
- [x] Method is `checkInReservation(CheckInReservationCommand command)`.
- [x] Service validates command.
- [x] Service builds `StoreScope`.
- [x] Service checks idempotency.
- [x] Service validates Store access.
- [x] Service loads Reservation by `StoreScope + reservationId`.
- [x] Service validates Reservation belongs to Store.
- [x] Service validates Reservation status.
- [x] Service validates Reservation can check in.
- [x] Service transitions `confirmed -> arrived`.
- [x] Service sets or resolves `arrivedAt`.
- [x] Service saves Reservation.
- [x] Service writes BusinessEvent.
- [x] Service writes StateTransitionLog.
- [x] Service writes AuditLog.
- [x] Service completes idempotency.
- [x] Service returns result.
- [x] Service does not parse API requests.
- [x] Service does not choose a table.
- [x] Service does not create queue.
- [x] Service does not create seating.

## 7. Required Ports

- [x] `StoreRepositoryPort` required.
- [x] `ReservationRepositoryPort` required.
- [x] `BusinessEventRepositoryPort` required.
- [x] `StateTransitionLogRepositoryPort` required.
- [x] `AuditLogRepositoryPort` required.
- [x] `IdempotencyRepositoryPort` required.
- [x] `ReservationRepositoryPort.findById(StoreScope, ReservationId)` required.
- [x] `ReservationRepositoryPort.save(StoreScope, Reservation)` required.
- [x] Queue repository port required: No.
- [x] Seating repository port required: No.
- [x] TableLock repository port required: No.
- [x] ReservationPreassignment repository port required: No.
- [x] Ports avoid mechanical CRUD expansion: Yes.

## 8. Required Rules / Policies / Validators

- [x] `StoreAccessPolicy` required.
- [x] `ReservationCheckInRule` required.
- [x] `ReservationStateMachine` required.
- [x] `AuditRule` required.
- [x] `BusinessEventRule` required.
- [x] `StateTransitionRule` required.
- [x] `IdempotencyRule` required.
- [x] `QueueCallingRule` introduced: No.
- [x] `TableAssignmentRule` introduced: No.
- [x] `SeatingSourceValidator` introduced: No.
- [x] `NoShowPolicy` introduced: No.
- [x] `CancellationPolicy` introduced: No.

## 9. State / Event / Audit Boundary

- [x] Status transition is clear: `confirmed -> arrived`.
- [x] `arrived -> seated` designed: No.
- [x] `arrived -> queue` designed: No.
- [x] `arrived -> no_show` designed: No.
- [x] `arrived -> cancelled` designed: No.
- [x] BusinessEvent recommendation is clear: `reservation.arrived`.
- [x] Audit operation is clear: `reservation.check_in`.
- [x] StateTransitionLog target is clear: `target_type = reservation`, `target_id = reservationId`.
- [x] Transition code is clear: `reservation.check_in`.
- [x] Duplicate/already-arrived behavior does not duplicate event, transition, or audit.

## 10. App Gate Boundary

- [x] Future API uses existing `app_key = reservation_queue`.
- [x] Future API uses permission `reservation.check_in`.
- [x] Future API must use `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")`.
- [x] Future API gets `tenantId` from actor/server context.
- [x] Future API gets `storeId` from path/server context.
- [x] Request body cannot override `tenantId`.
- [x] App Gate denial happens before CheckIn business execution.
- [x] App Gate denial does not mutate Reservation.
- [x] App Gate denial does not write CheckIn BusinessEvent, StateTransitionLog, AuditLog, or idempotency records.
- [x] App Gate denial writes `app_gate_audit_logs` with `action = APP_GATE_DENIED`.
- [x] `reservation_checkin` app key introduced: No.
- [x] `checkin_app` app key introduced: No.
- [x] `arrival_app` app key introduced: No.
- [x] New permission model introduced: No.

## 11. Idempotency Boundary

- [x] Idempotency behavior is clear.
- [x] Action is `check_in_reservation`.
- [x] Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- [x] Completed same hash replays result.
- [x] In-progress same hash returns retry later.
- [x] Failed same hash requires a new key.
- [x] Same key with different hash returns conflict.
- [x] Already-arrived with a new key returns success-like result.
- [x] Already-arrived result sets `alreadyArrived=true`.
- [x] Already-arrived result does not duplicate StateTransitionLog.
- [x] Already-arrived result does not duplicate BusinessEvent.
- [x] Already-arrived result does not duplicate AuditLog.

## 12. Failure Coverage

- [x] App Gate denied covered.
- [x] Store not found covered.
- [x] Store scope mismatch covered.
- [x] Reservation not found covered.
- [x] Reservation status not confirmed covered.
- [x] Reservation already arrived covered.
- [x] Reservation cancelled covered.
- [x] Reservation no_show covered.
- [x] Reservation completed covered.
- [x] Reservation seated covered.
- [x] Idempotency conflict covered.
- [x] Idempotency in progress covered.
- [x] Failed idempotency requires new key covered.
- [x] Event write failure covered.
- [x] Transition write failure covered.
- [x] Audit write failure covered.
- [x] Persistence save failure covered.
- [x] Raw DB exceptions exposed: No.

## 13. Test Contract Boundary

- [x] Success: confirmed Reservation CheckIn.
- [x] Success: status becomes `arrived`.
- [x] Success: `arrivedAt` set.
- [x] Success: `reservation.arrived` event written.
- [x] Success: transition `confirmed -> arrived` written.
- [x] Success: audit `reservation.check_in` written.
- [x] Success: idempotency completed.
- [x] Idempotency: completed replay.
- [x] Idempotency: in-progress retry later.
- [x] Idempotency: failed requires new key.
- [x] Idempotency: hash conflict.
- [x] Idempotency: already arrived with new key returns `alreadyArrived=true`.
- [x] Idempotency: already arrived does not duplicate transition.
- [x] Failure: Reservation not found.
- [x] Failure: wrong Store scope.
- [x] Failure: cancelled cannot check in.
- [x] Failure: no_show cannot check in.
- [x] Failure: completed cannot check in.
- [x] Failure: seated cannot check in.
- [x] Failure: persistence failure.
- [x] Failure: audit/event/transition failure.
- [x] App Gate future test: unauthorized app denied.
- [x] App Gate future test: store not enabled denied.
- [x] App Gate future test: missing `reservation.check_in` permission denied.
- [x] App Gate future test: authorized `reservation_queue` app with `reservation.check_in` succeeds into application service.
- [x] App Gate future test: denial writes `APP_GATE_DENIED`.
- [x] App Gate future test: denial does not mutate Reservation.

## 14. Forbidden Artifacts

- [x] Java Application Service created: No.
- [x] Repository implementation created: No.
- [x] Controller created: No.
- [x] API DTO created: No.
- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] Flyway migration created or modified: No.
- [x] SQL file created or modified: No.
- [x] App Gate app key created: No.
- [x] Permission model created: No.
- [x] Database structure changed: No.
- [x] Seed data inserted: No.
- [x] Production config changed: No.
- [x] Production database connected: No.

## 15. Boundary Assertions

- [x] Queue designed: No.
- [x] Seating designed: No.
- [x] Table assignment designed: No.
- [x] No-show designed: No.
- [x] Cancellation designed: No.
- [x] CheckInEntity designed: No.
- [x] `check_ins` table designed: No.
- [x] New app_key designed: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.
- [x] Idempotency behavior clear: Yes.
- [x] State transition clear: Yes.

## 16. Next Gate

Recommended next gate:

```text
Reservation CheckIn Application Implementation
```

Entry requirements for the next gate:

- Product Owner accepts this CheckIn contract.
- Implementation remains application-layer only unless a later prompt explicitly opens API, UI, persistence, or migration work.
- Queue, Seating, Table assignment, No-show, Cancellation, `CheckInEntity`, and `check_ins` remain outside the next gate unless separately approved.
