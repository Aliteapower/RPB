# Queue Skip API Implementation Report V1

## 1. Read Documents

- `docs/backend/QUEUE_SKIP_APPLICATION_CONTRACT.md`
- `docs/backend/QUEUE_SKIP_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/QUEUE_SKIP_APPLICATION_IMPLEMENTATION_REPORT.md`
- Queue Skip application command, service, result, error, rules, evidence rule, and tests
- `docs/api/QUEUE_LIST_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_LIST_UI_VALIDATION_REPORT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- `docs/api/SEATING_FROM_CALLED_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/SEATING_FROM_CALLED_QUEUE_UI_VALIDATION_REPORT.md`
- Existing Queue Call, Queue List, and Seating From Called Queue controller, DTO, mapper, and error mapper patterns
- Existing App Gate metadata, service tests, guard tests, and `/api/me/apps` patterns
- QueueTicket, Reservation, audit/event/transition/idempotency persistence patterns
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

## 2. API Contract Document

- Created `docs/api/QUEUE_SKIP_API_CONTRACT.md`.
- The contract defines the endpoint, method, path parameters, `Idempotency-Key`, request allowlist, response DTO, error response, App Gate annotation, idempotency behavior, alreadySkipped behavior, skippedAt behavior, forbidden fields, non-scope, and test contract.

## 3. Created / Updated Files

Created:

- `src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketRequest.java`
- `src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketResponse.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorCode.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorResponse.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueSkipApiMapper.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueSkipController.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueSkipControllerTest.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueSkipApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueSkipLocalRuntimeSecurityTest.java`
- `src/test/java/com/rpb/reservation/queue/api/LocalPostgresTestDatabase.java`
- `docs/api/QUEUE_SKIP_API_CONTRACT.md`
- `docs/api/QUEUE_SKIP_API_IMPLEMENTATION_REPORT.md`

Updated:

- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- Existing boundary baseline tests that enumerate approved queue API files/controllers
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`

## 4. Endpoint

- Method: `POST`
- Path: `/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip`
- Path variables: `storeId`, `queueTicketId`
- Header: required `Idempotency-Key`
- Body: optional JSON body with `skippedAt`, `reasonCode`, `note`

## 5. App Gate Annotation

- Controller method uses `@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")`.
- App Gate denial happens before application mutation.
- Denied requests write `app_gate_audit_logs` through the existing App Gate guard path.

## 6. App Gate Permission Metadata Update

- Added `queue.skip` as `AppGateRequiredPermission.QUEUE_SKIP`.
- Added `QUEUE_SKIP` to `RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- Updated metadata alignment docs to include `queue.skip` under `reservation_queue`.
- No new app key, permission model, migration, or V003 file was added.

## 7. Request DTO

`SkipQueueTicketRequest` exposes only:

- `skippedAt`
- `reasonCode`
- `note`

The controller does not accept client-provided tenant, actor, store in body, queue ticket in body, reservation, table, seating, cleaning, turnover, rejoin, no-show, cancellation, or status mutation fields.

## 8. Response DTO

`SkipQueueTicketResponse` returns:

- `success`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = skipped`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `skippedAt`
- `alreadySkipped`
- `events`
- `idempotency.status`
- `idempotency.replayed`

V1 does not expose event ids.

## 9. Error Mapping

`QueueSkipApiErrorMapper` maps application errors to stable API envelopes:

- `MISSING_IDEMPOTENCY_KEY`
- `INVALID_COMMAND`
- `STORE_NOT_FOUND`
- `STORE_SCOPE_MISMATCH`
- `FORBIDDEN`
- `QUEUE_TICKET_NOT_FOUND`
- `QUEUE_TICKET_STATUS_NOT_CALLED`
- `QUEUE_SKIP_EVIDENCE_INCOMPLETE`
- `RESERVATION_NOT_FOUND`
- `RESERVATION_STATUS_NOT_ARRIVED`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- `ILLEGAL_STATE_TRANSITION`
- `EVENT_WRITE_FAILED`
- `STATE_TRANSITION_WRITE_FAILED`
- `AUDIT_WRITE_FAILED`
- `PERSISTENCE_ERROR`

Error responses use the existing shape:

```json
{
  "success": false,
  "error": {
    "code": "...",
    "messageKey": "queue.skip....",
    "details": {}
  },
  "idempotency": {
    "status": "..."
  }
}
```

Raw database exceptions are not exposed.

## 10. skippedAt Behavior

- `skippedAt` may be supplied in the request.
- If omitted, the application service supplies the current clock time.
- Successful skip persists `queue_tickets.skipped_at`.
- API responses include `skippedAt`.

## 11. Idempotency Behavior

- Header `Idempotency-Key` is required.
- Application action remains `skip_queue_ticket`.
- Completed replay returns `200` and `idempotency.replayed = true`.
- In-progress keys return `409` with started idempotency status.
- Failed keys return `409` requiring a new key.
- Same key with different request hash returns `409` conflict.

## 12. AlreadySkipped Behavior

- If the ticket is already `skipped` and complete evidence exists, API returns success with `alreadySkipped = true`.
- No duplicate `BusinessEvent`, `StateTransitionLog`, or `AuditLog` is written.
- Reservation remains `arrived`.
- Table, Seating, SeatingResource, Cleaning, and Turnover remain unchanged.
- If evidence is incomplete, API returns `QUEUE_SKIP_EVIDENCE_INCOMPLETE`.

## 13. App Gate Tests

Covered by:

- `AppGateRequiredPermissionTest`
- `AppGateServiceTest`
- `QueueSkipControllerTest`
- `QueueSkipApiIntegrationTest`

Validated:

- `queue.skip` exists in reservation queue metadata.
- `/api/me/apps` recognizes `queue.skip` under `reservation_queue` when allowed.
- Allowed app plus enabled store plus permission allows the API.
- Tenant not entitled, store disabled, and permission denied block the API.
- Denied App Gate cases write audit rows.
- Denied App Gate cases do not mutate QueueTicket, Reservation, Seating, Table, BusinessEvent, StateTransitionLog, AuditLog, or idempotency records.

## 14. API / Integration Tests

Covered by:

- `QueueSkipControllerTest`
- `QueueSkipApiIntegrationTest`
- `QueueSkipLocalRuntimeSecurityTest`

Validated:

- Called QueueTicket can be skipped through API.
- QueueTicket status becomes `skipped`.
- `skippedAt` is persisted and returned.
- Reservation status remains `arrived`.
- BusinessEvent `queue_ticket.skipped` is written.
- StateTransitionLog records `queue_ticket` `called -> skipped`.
- AuditLog records `queue.skip`.
- Idempotency is completed.
- AlreadySkipped with complete evidence returns `alreadySkipped = true`.
- Idempotency completed replay, in-progress, failed-key reuse, hash conflict, and missing key are mapped.
- Queue ticket not found, ticket not called, reservation not found, reservation not arrived, and incomplete evidence are mapped.
- Boundary assertions confirm no downstream table/seating/cleaning/turnover side effects.

## 15. Local Runtime Security

- Local/test runtime allowlist was updated with:

```text
POST /api/v1/stores/*/queue-tickets/*/skip
```

- This change is limited to `LocalRuntimeSecurityConfiguration`, which is scoped to `local` and `test` profiles.
- `QueueSkipLocalRuntimeSecurityTest` verifies the local profile can call the endpoint without JWT login when the configured local actor has `queue.skip`.
- Production security was not weakened.

## 16. Commands Executed

- Red phase: `mvn -q "-Dtest=QueueSkip*Test,AppGateRequiredPermissionTest,AppGateServiceTest" test` failed before implementation with missing Queue Skip API classes and missing `QUEUE_SKIP` metadata.
- Focused Queue Skip/App Gate: `mvn -q "-Dtest=QueueSkip*Test,AppGateRequiredPermissionTest,AppGateServiceTest" test` passed.
- Required Queue Skip test command: `mvn -q "-Dtest=QueueSkip*Test" test` passed.
- Boundary resync group: `mvn -q "-Dtest=CleaningControllerTest,CleaningCompleteApiIntegrationTest,QueueCallControllerTest,ReservationCheckInApiIntegrationTest,ReservationControllerTest,ReservationCreateApiIntegrationTest,WalkInDirectSeatingControllerTest" test` passed.
- Full backend suite: `mvn test` passed.
- Frontend build: `npm run build` passed.

## 17. Test Result

- `QueueSkip*Test`: passed.
- App Gate metadata/service tests: passed in focused run.
- Full `mvn test`: passed.
- `npm run build`: passed.
- No existing tests were deleted or bypassed.

## 18. Boundary Check

Queue Rejoin API implemented: No  
Queue Display API implemented: No  
Queue Workbench mutation implemented: No  
Queue Call from list implemented: No  
Queue Seat from list implemented: No  
Seating implemented: No  
SeatingResource created: No  
Table status changed: No  
Table map implemented: No  
Auto assignment implemented: No  
No-show API implemented: No  
Cancellation API implemented: No  
Cleaning API changed: No  
Turnover API implemented: No  
UI implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No  
Existing API paths changed: No  

## 19. Open Questions

- None for V1 API implementation.

## 20. Open Conflicts

- None.

## 21. Next Step Recommendation

- Next slice can perform Queue Skip API runtime validation with a real browser or API client against the local backend and local temporary PostgreSQL, including App Gate denial and database read/write assertions.
