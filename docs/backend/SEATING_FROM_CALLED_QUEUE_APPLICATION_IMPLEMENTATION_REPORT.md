# Seating From Called Queue Application Implementation Report

## 1. Read Documents

- `docs/backend/SEATING_FROM_CALLED_QUEUE_APPLICATION_CONTRACT.md`
- `docs/backend/SEATING_FROM_CALLED_QUEUE_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_VALIDATION_REPORT.md`
- `docs/backend/QUEUE_CALL_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- QueueTicket, Reservation, Seating, SeatingResource, DiningTable, TableGroup, TableLock domain/persistence ports and adapters.

## 2. Application Classes Created

- `SeatCalledQueueTicketCommand`
- `SeatingFromCalledQueueApplicationService`
- `SeatingFromCalledQueueResult`
- `SeatingFromCalledQueueError`
- `QueueTicketSeatRule`
- `SeatingFromCalledQueueRule`
- `SeatingFromCalledQueueApplicationServiceTest`

## 3. Rules / Policies Implemented

- Reused `DefaultStoreAccessPolicy`, table availability/capacity/lock rules, table group validation, seating resource validation, state machines, audit/event/transition rules, and idempotency rule.
- Added queue-specific called-to-seated validation in `QueueTicketSeatRule`.
- Added minimal queue-ticket seating source validation in `SeatingFromCalledQueueRule`.

## 4. QueueTicket Status Behavior

- Fresh success path requires `QueueTicket.status = called`.
- Success path persists `QueueTicket.status = seated`.
- `waiting`, `skipped`, and `rejoined` are rejected as `queue_ticket_status_not_called`.
- `cancelled` and `expired` are rejected with terminal status errors.
- `completed` is not introduced.

## 5. Reservation Status Behavior

- Related Reservation is loaded from the QueueTicket source.
- Fresh success path requires `Reservation.status = arrived`.
- Success path persists `Reservation.status = seated`.
- No `Reservation.status = called` is introduced.

## 6. Seating Source Behavior

- Seating is created with `sourceType = queue_ticket` and `sourceId = queueTicketId`.
- Existing mapper stores this as `seatings.queue_ticket_id`.
- Reservation and walk-in seating source columns remain unused for this slice.

## 7. Resource Selection Behavior

- `tableId` and `tableGroupId` are strict XOR.
- Both-present and neither-present commands fail before idempotency start.
- No auto assignment is implemented.

## 8. Table Seating Behavior

- Single table path validates table existence, availability, capacity, lock conflict, and active occupancy.
- Success path creates a dining-table SeatingResource and updates the table to `occupied`.

## 9. TableGroup Seating Behavior

- TableGroup path validates group existence, active/valid group membership, group capacity, group lock, active group occupancy, and each member table availability/lock/occupancy.
- Success path creates a table-group SeatingResource and updates every member table to `occupied`.

## 10. AlreadySeated Behavior

- `QueueTicket.status = seated` returns `alreadySeated = true` only when the related Reservation is `seated`, an active Seating exists with source `queue_ticket`, and an active SeatingResource exists.
- Already seated success completes idempotency but does not duplicate Seating, SeatingResource, BusinessEvent, StateTransitionLog, AuditLog, QueueTicket changes, or Reservation changes.
- Missing active seating/resource evidence returns an application-level consistency error.

## 11. Idempotency Behavior

- Action is `seat_called_queue_ticket`.
- `COMPLETED + same hash` replays the stored result.
- `STARTED + same hash` returns retry-later with `idempotency_in_progress`.
- `FAILED + same hash` requires a new key.
- Same key with a different hash returns `idempotency_conflict`.

## 12. Events / Transition / Audit

- Business events: `queue_ticket.seated`, `reservation.seated`, `seating.created`, `table.occupied`.
- Transition logs: queue ticket `called -> seated`, reservation `arrived -> seated`, seating `planned -> occupied`, and dining table `available -> occupied`.
- TableGroup path records member table occupancy transitions.
- Audit success operation: `queue.seat`.
- Failure audit operation: `queue.seat.failed`.

## 13. Success Cases

- Called queue ticket seated to a single table.
- Called queue ticket seated to a table group.
- QueueTicket becomes `seated`.
- Reservation becomes `seated`.
- Seating and SeatingResource are created.
- Table/member tables become `occupied`.
- Events, transitions, audit, and idempotency completion are written.

## 14. Failure Cases

- Store not found / scope mismatch / access denied.
- Queue ticket not found.
- Queue ticket status not called.
- Queue ticket seated without matching active seating/resource evidence.
- Related reservation not found.
- Related reservation not arrived.
- Table not found, unavailable, too small, or locked.
- TableGroup not found, invalid, too small, or with unavailable member table.
- Resource selection conflict or missing resource selection.
- Idempotency conflict, in-progress retry, and failed-key reuse.
- Business event, transition log, audit log, and persistence write failures.

## 15. Tests Executed

- `mvn -q "-Dtest=SeatingFromCalledQueueApplicationServiceTest" test`
- `mvn test`

## 16. Test Result

- `SeatingFromCalledQueueApplicationServiceTest`: passed.
- Full `mvn test`: passed, 447 tests, 0 failures, 0 errors, 0 skipped.

## 17. Boundary Check

- Queue Skip implemented: No
- Queue Rejoin implemented: No
- Queue Display implemented: No
- Queue list/workbench implemented: No
- Auto assignment implemented: No
- No-show implemented: No
- Cancellation implemented: No
- Cleaning implemented: No
- Turnover implemented: No
- Controller created: No
- API DTO created: No
- UI implemented: No
- Migration changed: No
- App Gate metadata changed: No
- Production database touched: No
- Seed data inserted: No

## 18. Open Questions

- Future API round should decide the REST path, request/response DTOs, and App Gate annotation for `app_key = reservation_queue`, `permission = queue.seat`.

## 19. Open Conflicts

- None for this application-layer slice.
- Expected future boundary remains: `queue.seat` is documented for future API/App Gate metadata work but was not added in this round.

## 20. Next Step Recommendation

- Proceed to Queue Seat API Contract / Implementation after explicit approval, including controller, DTOs, App Gate permission metadata alignment, and API integration tests.
