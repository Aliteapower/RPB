# Queue Call Application Implementation Report V1

## 1. Read Documents

- `docs/backend/QUEUE_CALL_APPLICATION_CONTRACT.md`
- `docs/backend/QUEUE_CALL_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_VALIDATION_REPORT.md`
- QueueTicket domain, status, state machine, repository port, persistence adapter, mapper, entity, and JPA repository.
- StorePolicy domain, entity, mapper, repository port, persistence adapter, and `queue_call_hold_minutes` support.
- Existing Reservation CheckIn, Arrived Direct Seating, and Arrived To Queue application service patterns.
- App Gate operational handoff, integration checklist, permission metadata alignment, and `AppGateRequiredPermission`.
- Governance, architecture, schema design, V001, and V002 documents.

## 2. Application Classes Created

- `CallQueueTicketCommand`
- `QueueCallApplicationService`
- `QueueCallResult`
- `QueueCallError`
- `QueueCallRule`
- `QueueCallHoldPolicy`
- `QueueCallApplicationServiceTest`

Supplemented:

- `QueueTicket` now preserves `calledAt` and `expiresAt`.
- `DefaultQueueTicketMapper` now maps `called_at` and `expires_at` both ways.

## 3. Rules / Policies Implemented

- `QueueCallRule` accepts only `waiting` for fresh call.
- `QueueCallRule` treats `called` with `calledAt` and `expiresAt` as already-called evidence.
- `QueueCallHoldPolicy` resolves hold duration from StorePolicy or default 3 minutes.
- Existing `QueueTicketStateMachine` validates `waiting -> called`.
- Existing audit, business event, state transition, store access, and idempotency rules are reused.

## 4. QueueTicket Status Behavior

- Fresh success changes `QueueTicket.status` from `waiting` to `called`.
- Fresh success records `calledAt`.
- Fresh success records `expiresAt` as the V1 `holdUntilAt`.
- `skipped`, `rejoined`, `seated`, `cancelled`, and `expired` are rejected.
- No `called -> seated`, `called -> skipped`, rejoin, expiry, cancellation, or display behavior was implemented.

## 5. Reservation Status Behavior

- Related Reservation is loaded only when `queueTicket.reservationId` is present.
- Related Reservation must be `arrived`.
- Reservation is not saved by Queue Call.
- Reservation status remains `arrived`.
- No Reservation `called` status or Reservation state-machine change was introduced.

## 6. Hold Policy Behavior

- `StoreRepositoryPort.findCurrentPolicy(scope, calledAt)` is used.
- `StorePolicy.queueCallHoldMinutes` is preferred.
- Missing policy falls back to 3 minutes.
- `holdUntilAt = calledAt + holdMinutes`.
- Invalid policy lookup is mapped to `QUEUE_CALL_HOLD_POLICY_INVALID`.
- No migration or new DB column was added.

## 7. AlreadyCalled Behavior

- `QueueTicket.status = called` with both `calledAt` and `expiresAt` returns `alreadyCalled = true`.
- AlreadyCalled completes idempotency with a replayable success-like snapshot.
- AlreadyCalled does not save QueueTicket.
- AlreadyCalled does not write duplicate BusinessEvent, StateTransitionLog, or AuditLog.
- `called` without required evidence returns `QUEUE_CALL_EVIDENCE_INCOMPLETE`.

## 8. Idempotency Behavior

- Action is `call_queue_ticket`.
- Completed same hash replays stored result.
- In-progress same hash returns retry-later behavior.
- Failed same hash requires a new idempotency key.
- Same key with different hash returns conflict.
- Missing `calledAt` hashes with an application-clock marker, not the resolved current time.

## 9. Events / Transition / Audit

- Success writes BusinessEvent `queue_ticket.called`.
- Success writes StateTransitionLog `queue_ticket: waiting -> called` with transition code `queue_ticket.call`.
- Success writes AuditLog operation `queue.call`.
- Failure audit operation is `queue.call.failed` and is best effort.
- No `reservation.queue_called` event or Reservation transition is written.

## 10. Success Cases

- Waiting QueueTicket can be called.
- QueueTicket status becomes `called`.
- Reservation remains `arrived`.
- `calledAt` is returned.
- `holdUntilAt` is returned.
- StorePolicy hold duration and default 3-minute fallback are covered.
- Event, transition, audit, and idempotency completion are covered.

## 11. Failure Cases

- QueueTicket not found.
- QueueTicket not waiting.
- Already-called missing evidence.
- Skipped, rejoined, seated, cancelled, and expired status rejection.
- Related Reservation not found.
- Related Reservation not arrived.
- Invalid hold policy.
- Idempotency conflict, in-progress, and failed-key reuse.
- BusinessEvent, StateTransitionLog, AuditLog, and persistence save failure.

## 12. Tests Executed

- TDD red: `mvn -q "-Dtest=QueueCallApplicationServiceTest" test`
  - Failed as expected before implementation because the new command, service, result, error, rules, and QueueTicket call evidence fields did not exist.
- Target green: `mvn -q "-Dtest=QueueCallApplicationServiceTest" test`
  - Passed.
- Full regression: `mvn test`
  - Failed on existing static boundary tests outside this round's allowed modification scope.

## 13. Test Result

- `QueueCallApplicationServiceTest`: 15 tests, 0 failures, 0 errors, 0 skipped.
- `mvn test`: 405 tests run, 4 failures, 0 errors, 0 skipped.
- The 4 full-regression failures are:
  - `CleaningControllerTest.noOtherVerticalSliceApiOrUiArtifactsAreCreated`
  - `ReservationControllerTest.noForbiddenReservationApiOrUiArtifactsAreCreated`
  - `ReservationCreateApiIntegrationTest.boundaryArtifactsRemainLimitedToReservationCreateApi`
  - `WalkInDirectSeatingControllerTest.noForbiddenVerticalSliceApiOrUiArtifactsAreCreated`
- The failures are old static boundary whitelist failures around the already-existing `src/pages/ReservationArrivedToQueuePage.vue`, not Queue Call application behavior.

## 14. Boundary Check

Controller created: No
REST API created: No
API DTO created: No
Vue UI changed: No
Router changed: No
Staff Home changed: No
App Gate metadata changed: No
`AppGateRequiredPermission` changed: No
Migration changed: No
SQL changed: No
Database structure changed: No
Queue Skip implemented: No
Queue Rejoin implemented: No
Queue Display implemented: No
Seating from Queue implemented: No
Table assignment implemented: No
Auto assignment implemented: No
Reservation status changed: No
No-show implemented: No
Cancellation implemented: No
Cleaning implemented: No
Turnover implemented: No
Production database touched: No
Seed data inserted: No

## 15. Open Questions

- Should future Queue Call API expose both `holdUntilAt` and raw `expiresAt`, or only `holdUntilAt`?
- Should future WalkIn-sourced QueueTicket call results include WalkIn display fields, or remain QueueTicket-only until a WalkIn queue contract exists?

## 16. Open Conflicts

- The pass condition asks for full `mvn test` to pass, but full regression currently fails in old boundary tests whose expected UI artifact whitelist does not include the already-approved Reservation Arrived To Queue page.
- Updating those tests would be outside this round's allowed modification scope, so this implementation leaves them unchanged and reports the blocker.

## 17. Next Step Recommendation

- First approve a small baseline-sync test maintenance round for the stale boundary tests, if full `mvn test` must be green before the next feature gate.
- After that, proceed to Queue Call API Contract or API Implementation only in a separately approved round, with future App Gate口径 `app_key = reservation_queue` and `permission = queue.call`.
