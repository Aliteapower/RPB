# Seating From Called Queue API Implementation Report V1

## 1. Read Documents

- Read the attached `Seating From Called Queue API Contract / Implementation V1` request.
- Read seating application documents:
  - `docs/backend/SEATING_FROM_CALLED_QUEUE_APPLICATION_CONTRACT.md`
  - `docs/backend/SEATING_FROM_CALLED_QUEUE_VERTICAL_SLICE_CHECKLIST.md`
  - `docs/backend/SEATING_FROM_CALLED_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- Read queue call API and UI validation context:
  - `docs/api/QUEUE_CALL_API_CONTRACT.md`
  - `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
  - `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- Read App Gate handoff and alignment context:
  - `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
  - `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
  - `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
  - `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
  - `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- Read governance, architecture, schema, and RPB skill context:
  - `docs/governance/BUSINESS_RULES.md`
  - `docs/governance/BUSINESS_GLOSSARY.md`
  - `docs/governance/DATA_STANDARD.md`
  - `docs/architecture/ARCHITECTURE.md`
  - `docs/database/SCHEMA_DESIGN.md`
  - `docs/skills/reservation-system/SKILL.md`
  - `docs/skills/api-review/SKILL.md`
  - `docs/skills/tdd-review/SKILL.md`
  - `docs/skills/code-review/SKILL.md`
  - `docs/skills/release-note/SKILL.md`
  - `docs/skills/pr-review/SKILL.md`
  - `docs/skills/production-readiness/SKILL.md`
- Read existing implementation patterns for Queue Call API, Reservation Arrived Direct Seating API, Reservation Arrived To Queue API, App Gate tests, and local runtime security tests.

## 2. API Contract Document

- Created `docs/api/SEATING_FROM_CALLED_QUEUE_API_CONTRACT.md`.
- The contract documents purpose, endpoint, path parameters, headers, request body, response body, error response, App Gate annotation, permission, idempotency, alreadySeated, table/tableGroup XOR behavior, forbidden fields, non-scope, local runtime security, and test contract.

## 3. Created / Updated Files

- Created API layer:
  - `src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java`
  - `src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketResponse.java`
  - `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java`
  - `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorMapper.java`
  - `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorResponse.java`
  - `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java`
  - `src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java`
- Updated App Gate permission metadata code:
  - `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
  - `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
  - `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- Updated local/test runtime security:
  - `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- Created API and security tests:
  - `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiIntegrationTest.java`
  - `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueControllerTest.java`
  - `src/test/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueLocalRuntimeSecurityTest.java`
- Updated App Gate tests:
  - `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
  - `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
  - `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`
- Updated approved boundary baselines to include this API artifact:
  - `src/test/java/com/rpb/reservation/queue/api/QueueCallControllerTest.java`
  - `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
  - `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
  - `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`
  - Related existing integration boundary tests that already whitelist approved queue API files.
- Created this report:
  - `docs/api/SEATING_FROM_CALLED_QUEUE_API_IMPLEMENTATION_REPORT.md`

## 4. Endpoint

- Method: `POST`
- Path: `/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct`
- Path-sourced values:
  - `storeId`
  - `queueTicketId`
- Header:
  - `Idempotency-Key` is required.

## 5. App Gate Annotation

- Applied App Gate on the controller method:
  - `app_key = reservation_queue`
  - `permission = queue.seat`
- App Gate deny path is covered by controller and integration tests.
- Deny audit writes to `app_gate_audit_logs`.

## 6. App Gate Permission Metadata Update

- Added `queue.seat` as a reservation queue entry permission in `AppGateRequiredPermission`.
- Confirmed `/api/me/apps` visibility logic recognizes `queue.seat` as part of `reservation_queue`.
- Updated App Gate metadata alignment docs and tests.
- No new app key was added.
- No migration or permission model change was added.

## 7. Request DTO

- Implemented `SeatCalledQueueTicketRequest`.
- Allowed fields:
  - `tableId`
  - `tableGroupId`
  - `overrideReasonCode`
  - `overrideNote`
  - `note`
- Enforced exactly one resource selector:
  - `tableId` XOR `tableGroupId`
- DTO does not expose tenant, store, queue ticket, reservation, no-show, cancellation, cleaning, turnover, skip, rejoin, or status fields.

## 8. Response DTO

- Implemented `SeatCalledQueueTicketResponse`.
- Response includes:
  - `queueTicketId`
  - `queueTicketNumber`
  - `queueTicketStatus`
  - `reservationId`
  - `reservationCode`
  - `reservationStatus`
  - `seatingId`
  - `seatingStatus`
  - `resourceType`
  - `resourceId`
  - `alreadySeated`
  - `events`
  - `idempotency`
- API resource type mapping:
  - single table: `table`
  - table group: `table_group`

## 9. Error Mapping

- Implemented stable API error mapping for:
  - missing idempotency key
  - table/tableGroup conflict
  - missing table/tableGroup selector
  - store access denial
  - queue ticket not found
  - queue ticket not called
  - queue ticket already seated without valid active seating
  - reservation not found
  - reservation not arrived
  - table not found
  - table unavailable
  - table capacity insufficient
  - table locked
  - table group not found
  - table group invalid
  - table group member unavailable
  - table group capacity insufficient
  - idempotency conflict
  - idempotency in progress
  - failed idempotency key requires a new key
  - event, transition, audit, and persistence write failures
  - unknown application failure
- API errors follow the existing `{ code, messageKey, details }` envelope.
- Raw database exceptions are not exposed by the controller mapping.

## 10. Idempotency Behavior

- Header `Idempotency-Key` is required before the application service is called.
- Application action remains `seat_called_queue_ticket`.
- Completed same-hash replay returns success with replayed idempotency state.
- In-progress same-hash requests return retry-later conflict behavior.
- Failed same-hash keys require a new key.
- Same key with different hash returns conflict.

## 11. AlreadySeated Behavior

- A seated queue ticket with matching active seating returns `alreadySeated = true`.
- Already seated replay does not create duplicate Seating, SeatingResource, BusinessEvent, StateTransitionLog, or AuditLog.
- Response preserves seated queue ticket and reservation state.

## 12. App Gate Tests

- Added or updated tests for:
  - `queue.seat` permission key stability.
  - `queue.seat` included in reservation queue entry permissions.
  - `/api/me/apps` recognizes `queue.seat` under `reservation_queue`.
  - Controller method has `@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")`.
  - Denied tenant/store/permission paths write App Gate audit.
  - Denied paths leave QueueTicket, Reservation, Seating, BusinessEvent, StateTransitionLog, and AuditLog unchanged.

## 13. API / Integration Tests

- Added SpringBootTest + MockMvc + local temporary PostgreSQL integration coverage for:
  - called queue ticket seated to a table.
  - called queue ticket seated to a tableGroup.
  - QueueTicket status becomes `seated`.
  - Reservation status becomes `seated`.
  - Seating source remains `queue_ticket`.
  - SeatingResource is created.
  - table or table group member tables become occupied.
  - response events and completed idempotency are returned.
  - alreadySeated returns success without duplicates.
  - completed replay, in-progress, failed key, hash conflict, and missing Idempotency-Key.
  - queue ticket not found and not called.
  - reservation not found and reservation not arrived.
  - table not found, unavailable, capacity insufficient, and locked.
  - tableGroup invalid and resource selector validation.
  - boundary assertions for no skip, rejoin, display, no-show, cancellation, cleaning, turnover, UI, or migration side effects.

## 14. Local Runtime Security

- Added local/test runtime allowlist for:
  - `POST /api/v1/stores/*/queue-tickets/*/seating/direct`
- Added regression coverage in `SeatingFromCalledQueueLocalRuntimeSecurityTest`.
- Production security was not weakened.

## 15. Commands Executed

- TDD red check:
  - `mvn -q "-Dtest=SeatingFromCalledQueue*Test,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test`
  - Failed before implementation because the API classes and `QUEUE_SEAT` permission did not exist yet.
- Targeted post-implementation check:
  - `mvn -q "-Dtest=SeatingFromCalledQueue*Test,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test`
  - Passed after implementation.
- Boundary failure reproduction:
  - `mvn test`
  - Initially failed 3 legacy boundary tests because approved controller allowlists did not include `SeatingFromCalledQueueController`.
- Boundary fix verification:
  - `mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,WalkInDirectSeatingControllerTest" test`
  - Passed.
- Required commands:
  - `mvn -q "-Dtest=SeatingFromCalledQueue*Test" test`
  - `mvn test`
  - `npm run build`

## 16. Test Result

- `mvn -q "-Dtest=SeatingFromCalledQueue*Test" test`: passed, 38 tests, 0 failures, 0 errors, 0 skipped, exit code 0.
- `mvn test`: passed, 470 tests, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
- `npm run build`: passed, `vue-tsc --noEmit && vite build`, 69 modules transformed, build completed.

## 17. Boundary Check

- Queue Skip API implemented: No
- Queue Rejoin API implemented: No
- Queue Display API implemented: No
- Queue list/workbench implemented: No
- Auto assignment implemented: No
- Table map implemented: No
- No-show API implemented: No
- Cancellation API implemented: No
- Cleaning API changed: No
- Turnover API implemented: No
- Vue UI implemented: No
- Vue Router changed: No
- Staff Home changed: No
- Migration changed: No
- V001 migration changed: No
- V002 migration changed: No
- Database schema changed: No
- Production database touched: No
- Seed data inserted: No
- Existing API paths changed: No

## 18. Open Questions

- None for this API implementation round.

## 19. Open Conflicts

- None.
- Note: the workspace contains an empty `.git` directory without usable git metadata, so changed-file review was performed from filesystem state rather than `git diff`.

## 20. Next Step Recommendation

- Proceed to Seating From Called Queue UI Contract / Implementation only after this API contract and implementation are accepted.
- Keep Queue Skip, Queue Rejoin, Queue Display, and queue list/workbench as separate future slices.
