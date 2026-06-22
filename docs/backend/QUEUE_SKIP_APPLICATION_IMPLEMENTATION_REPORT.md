# Queue Skip Application Implementation Report V1

## 1. Read Documents

- `docs/backend/QUEUE_SKIP_APPLICATION_CONTRACT.md`
- `docs/backend/QUEUE_SKIP_VERTICAL_SLICE_CHECKLIST.md`
- `docs/api/QUEUE_LIST_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_LIST_UI_VALIDATION_REPORT.md`
- `docs/backend/QUEUE_CALL_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- `docs/backend/SEATING_FROM_CALLED_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/SEATING_FROM_CALLED_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/SEATING_FROM_CALLED_QUEUE_UI_VALIDATION_REPORT.md`
- QueueTicket domain, status enum, state machine, entity, mapper, repository port, and persistence adapter.
- Reservation domain, status enum, mapper, repository port, and persistence adapter.
- BusinessEvent, StateTransitionLog, AuditLog, and Idempotency domain / repository / rule patterns.
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`

Confirmed before implementation:

- `QueueTicketStatus.SKIPPED` exists.
- `QueueTicketStateMachine` allows `called -> skipped`.
- V001 `queue_tickets.status` allows `skipped`.
- V001 `queue_tickets.skipped_at` exists.
- Existing `QueueTicketEntity` has `skippedAt`.
- Existing `QueueTicket` domain / `DefaultQueueTicketMapper` did not preserve `skippedAt`; this round fixed that source-code gap.

## 2. Application Classes Created

Created:

- `src/main/java/com/rpb/reservation/queue/application/command/SkipQueueTicketCommand.java`
- `src/main/java/com/rpb/reservation/queue/application/service/QueueSkipApplicationService.java`
- `src/main/java/com/rpb/reservation/queue/application/QueueSkipResult.java`
- `src/main/java/com/rpb/reservation/queue/application/QueueSkipError.java`
- `src/main/java/com/rpb/reservation/queue/application/rule/QueueSkipRule.java`
- `src/main/java/com/rpb/reservation/queue/application/rule/QueueSkipEvidenceRule.java`
- `src/test/java/com/rpb/reservation/queue/application/QueueSkipApplicationServiceTest.java`

Updated:

- `src/main/java/com/rpb/reservation/queue/domain/QueueTicket.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueTicketMapper.java`

## 3. skippedAt Domain / Mapper / Persistence Support

- Added nullable `QueueTicket.skippedAt`.
- Added `QueueTicket.skip(Instant skippedAt)`.
- Preserved `skippedAt` through `QueueTicket.call(...)` and `QueueTicket.seat()`.
- Updated `DefaultQueueTicketMapper.toDomain(...)` to map `QueueTicketEntity.skippedAt`.
- Updated `DefaultQueueTicketMapper.toEntity(...)` to map `QueueTicket.skippedAt`.
- Existing `QueueTicketPersistenceAdapter.save(...)` already preserves mapper-supplied `skippedAt` on create and update; no SQL or migration was changed.

## 4. Rules / Policies Implemented

- `QueueSkipRule` accepts fresh skip only when `QueueTicket.status = called`.
- `QueueSkipEvidenceRule` validates alreadySkipped evidence:
  - `QueueTicket.status = skipped`
  - `QueueTicket.skippedAt != null`
  - `queue_ticket.skipped` BusinessEvent exists
  - `queue_ticket called -> skipped` StateTransitionLog exists
  - `queue.skip` AuditLog exists
- Reused `DefaultStoreAccessPolicy`, `QueueTicketStateMachine`, `DefaultIdempotencyRule`, `DefaultBusinessEventRule`, `DefaultStateTransitionRule`, and `DefaultAuditRule`.

## 5. QueueTicket Status Behavior

- Fresh success requires `called`.
- Fresh success persists `skipped`.
- `waiting`, `rejoined`, `seated`, `cancelled`, and `expired` are rejected as `QUEUE_TICKET_STATUS_NOT_CALLED`.
- Existing `skipped` is handled only through the alreadySkipped branch with complete evidence.

## 6. Reservation Status Behavior

- Related Reservation is required.
- Related Reservation must be `arrived`.
- Reservation remains `arrived`.
- The service does not save Reservation.
- No Reservation state transition is written.

## 7. Seating / Table Boundary

- No Seating is created.
- No SeatingResource is created.
- No DiningTable is loaded or changed.
- No TableGroup, TableLock, Cleaning, Turnover, No-show, or Cancellation behavior was added.

## 8. AlreadySkipped Behavior

- Already skipped ticket with complete evidence returns `alreadySkipped = true`.
- Does not save QueueTicket.
- Does not save Reservation.
- Does not duplicate BusinessEvent, StateTransitionLog, or AuditLog.
- Completes the new idempotency key with a replayable skipped snapshot.
- Already skipped without complete evidence returns `QUEUE_SKIP_EVIDENCE_INCOMPLETE`.

## 9. Idempotency Behavior

- Action: `skip_queue_ticket`.
- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- `COMPLETED + same hash` replays the stored result.
- `STARTED + same hash` returns retry-later with `IDEMPOTENCY_IN_PROGRESS`.
- `FAILED + same hash` returns `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY`.
- Same key with a different hash returns `IDEMPOTENCY_CONFLICT`.
- Completed replay does not duplicate mutation, event, transition, or audit writes.

## 10. Events / Transition / Audit

- BusinessEvent: `queue_ticket.skipped`.
- StateTransitionLog: `queue_ticket`, `called -> skipped`, transition code `queue_ticket.skip`.
- AuditLog: `queue.skip`.
- Failure audit: best-effort `queue.skip.failed`.
- No Reservation transition is written.

## 11. Success Cases

- Called queue ticket can be skipped.
- `QueueTicket.status` becomes `skipped`.
- `QueueTicket.skippedAt` is persisted through the domain/mapper path.
- Reservation remains `arrived`.
- BusinessEvent, StateTransitionLog, AuditLog, and completed idempotency are written.
- Command-provided `skippedAt` is used when present; otherwise the application clock is used.

## 12. Failure Cases

Covered in `QueueSkipApplicationServiceTest`:

- Queue ticket not found.
- Queue ticket status not called.
- Already skipped without complete evidence.
- Related Reservation not found.
- Related Reservation not arrived.
- Reservation-backed source missing.
- Idempotency conflict.
- Idempotency in progress.
- Failed idempotency requires new key.
- BusinessEvent write failure.
- StateTransitionLog write failure.
- AuditLog write failure.
- QueueTicket persistence save failure.

## 13. Tests Executed

- TDD red:
  - `mvn -q "-Dtest=QueueSkipApplicationServiceTest" test`
  - Failed as expected before implementation because Queue Skip classes and `QueueTicket.skippedAt()` did not exist.
- Target green:
  - `mvn -q "-Dtest=QueueSkipApplicationServiceTest" test`
  - Passed.
- Full regression:
  - First `mvn test` attempt timed out at the command timeout before a final result.
  - Reran `mvn test` with a longer timeout.

## 14. Test Result

- `QueueSkipApplicationServiceTest`: passed, 15 tests, 0 failures, 0 errors, 0 skipped.
- `mvn test`: failed, 504 tests run, 3 failures, 0 errors, 0 skipped.
- Full regression failures are stale boundary whitelist failures:
  - `CleaningControllerTest.noOtherVerticalSliceApiOrUiArtifactsAreCreated`
  - `ReservationControllerTest.noForbiddenReservationApiOrUiArtifactsAreCreated`
  - `WalkInDirectSeatingControllerTest.noForbiddenVerticalSliceApiOrUiArtifactsAreCreated`
- All three failures reject pre-existing `src/pages/QueueTicketListPage.vue`.
- Updating those UI boundary whitelists is outside this round's allowed files, so this implementation leaves them unchanged and reports the conflict.

## 15. Boundary Check

Queue Rejoin implemented: No

Queue Display implemented: No

Queue Workbench implemented: No

Seating implemented: No

SeatingResource created: No

Table status changed: No

No-show implemented: No

Cancellation implemented: No

Cleaning implemented: No

Turnover implemented: No

Controller created: No

REST API created: No

API DTO created: No

Vue UI implemented: No

Vue Router changed: No

Staff Home changed: No

App Gate metadata changed: No

`AppGateRequiredPermission` changed: No

Migration changed: No

V001 changed: No

V002 changed: No

SQL file changed: No

Production database touched: No

Seed data inserted: No

## 16. Open Questions

- Should a future API error mapper use `queue.skip.*` message keys for every `QueueSkipError`?
- Should future reason-code validation require `reason_codes.reason_type = skip` in a separate approved slice?

## 17. Open Conflicts

- Full `mvn test` currently fails because three old boundary tests do not include the already-approved Queue List UI page `src/pages/QueueTicketListPage.vue`.
- Fixing those tests requires a boundary-baseline maintenance change outside this Queue Skip application scope.
- `queue.skip` App Gate metadata is intentionally not added in this application-only round.

## 18. Next Step Recommendation

- Approve a small boundary-baseline sync round for the stale Queue List UI whitelist failures if full `mvn test` must be green before the next feature gate.
- After that, proceed to Queue Skip API Contract / Implementation in a separate approved round, including future App Gate metadata for `reservation_queue` + `queue.skip`.
