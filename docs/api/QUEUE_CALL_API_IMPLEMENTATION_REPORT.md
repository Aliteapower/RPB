# Queue Call API Implementation Report V1

## Queue Call API Contract / Implementation Result

### 1. Read Documents

Read and checked:

- `docs/backend/QUEUE_CALL_APPLICATION_CONTRACT.md`
- `docs/backend/QUEUE_CALL_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/QUEUE_CALL_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_VALIDATION_REPORT.md`
- `docs/backend/APPROVED_SLICE_BOUNDARY_BASELINE_SYNC_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- QueueTicket, QueueGroup, StorePolicy, Reservation, App Gate, local runtime security, and existing controller/test implementation patterns.

Confirmed:

- Queue Call application layer is completed.
- Queue Call application behavior is `QueueTicket waiting -> called` only.
- Reservation status remains `arrived`.
- `queue_tickets.expires_at` is used as `holdUntilAt`.
- Default Queue Call hold is 3 minutes when StorePolicy does not define `queue_call_hold_minutes`.
- This round does not create UI, migrations, Queue skip/rejoin/display, Seating from Queue, Table assignment, No-show, Cancellation, Cleaning, or Turnover.

### 2. API Contract

Created:

- `docs/api/QUEUE_CALL_API_CONTRACT.md`

Implemented endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call
```

The endpoint maps HTTP path/header/body/actor context to `CallQueueTicketCommand` and delegates all business mutation to `QueueCallApplicationService`.

### 3. Created / Updated Files

Created:

- `src/main/java/com/rpb/reservation/queue/api/CallQueueTicketRequest.java`
- `src/main/java/com/rpb/reservation/queue/api/CallQueueTicketResponse.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorCode.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorResponse.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueCallApiMapper.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueCallController.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueCallControllerTest.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueCallApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueCallLocalRuntimeSecurityTest.java`
- `docs/api/QUEUE_CALL_API_CONTRACT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`

Updated:

- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`
- Approved boundary tests that previously blocked every `queue/api` file, now allowing only the Queue Call API whitelist.
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`

### 4. Endpoint

Implemented:

```java
@PostMapping("/{queueTicketId}/call")
@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")
```

Full path:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call
```

Success responses use `200 OK` for fresh call, completed idempotency replay, and already-called success-like responses.

### 5. App Gate

Added:

- `AppGateRequiredPermission.QUEUE_CALL = "queue.call"`
- `queue.call` in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`

Verified:

- App Gate guard annotation uses `appKey = reservation_queue`.
- App Gate guard annotation uses `permission = queue.call`.
- `/api/me/apps` recognizes `queue.call` as an entry permission for `reservation_queue`.
- Tenant entitlement denial, Store disabled denial, and permission denial do not call the Queue Call application service and do not mutate business tables.

No new `app_key` was created.

### 6. Request DTO

`CallQueueTicketRequest` exposes only:

- `calledAt`
- `reasonCode`
- `note`

Forbidden fields are not part of the DTO, including:

- `reservationStatus`
- `tableId`
- `tableGroupId`
- `seatingId`
- `cleaningId`
- `turnoverId`
- `skipReason`
- `rejoinReason`

The controller gets `tenantId`, `actorId`, `actorType`, roles, permissions, and Store scope from `CurrentActorProvider`, not from the request body.

### 7. Response DTO

`CallQueueTicketResponse` returns:

- `success`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = called`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `calledAt`
- `holdUntilAt`
- `alreadyCalled`
- `events`
- `idempotency`

V1 does not return event ids.

### 8. Error Mapping

Created Queue Call API-specific error envelope and mapper:

- `QueueCallApiErrorCode`
- `QueueCallApiErrorResponse`
- `QueueCallApiErrorMapper`

Covered mappings include:

- `MISSING_IDEMPOTENCY_KEY` -> 400
- `STORE_NOT_FOUND` -> 404
- `STORE_SCOPE_MISMATCH` -> 403
- `QUEUE_TICKET_NOT_FOUND` -> 404
- `QUEUE_TICKET_STATUS_NOT_WAITING` -> 409
- `QUEUE_CALL_EVIDENCE_INCOMPLETE` -> 409
- seated/cancelled/expired call rejection -> 409
- `RESERVATION_NOT_FOUND` -> 404
- `RESERVATION_STATUS_NOT_ARRIVED` -> 409
- `QUEUE_CALL_HOLD_POLICY_INVALID` -> 409
- idempotency in-progress/failed/conflict -> 409
- event/transition/audit/persistence failures -> 500

The API does not expose raw database exceptions.

### 9. Idempotency

The endpoint requires `Idempotency-Key`.

Covered behavior:

- Missing key returns `MISSING_IDEMPOTENCY_KEY` and does not call the application service.
- Fresh success completes idempotency with action `call_queue_ticket`.
- Completed same-key replay returns `idempotency.replayed = true`.
- Same-hash `started` returns `IDEMPOTENCY_IN_PROGRESS` and `idempotency.status = started`.
- Same-hash `failed` returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Different-hash key returns `IDEMPOTENCY_CONFLICT` and `idempotency.status = conflict`.

### 10. AlreadyCalled

Already-called behavior is exposed as success-like:

- HTTP status `200 OK`.
- `queueTicketStatus = called`.
- `reservationStatus = arrived`.
- `alreadyCalled = true`.
- `events = []`.
- No duplicate BusinessEvent, StateTransitionLog, or AuditLog is written.

If an already-called QueueTicket lacks call evidence, the application returns `QUEUE_CALL_EVIDENCE_INCOMPLETE`, and the API maps it to HTTP 409.

### 11. App Gate Tests

Added or updated coverage for:

- `queue.call` stable permission key.
- `queue.call` included in `RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `/api/me/apps` app-entry recognition for `queue.call`.
- Queue Call controller method annotation.
- Guard inspection for `appKey = reservation_queue` and `permission = queue.call`.
- App Gate runtime denial behavior in Queue Call API integration tests.

### 12. API / Integration Tests

`QueueCallControllerTest` covers:

- Request-to-command mapping.
- Null body handling.
- Response mapping for fresh success, replay, and already-called.
- Missing idempotency key.
- DTO allowed fields only.
- App Gate annotation.
- Application error to API error mapping.
- Forbidden role, missing permission, and Store scope mismatch before application service.
- Boundary whitelist for only the approved Queue Call API files.

`QueueCallApiIntegrationTest` covers:

- Waiting QueueTicket can be called.
- QueueTicket becomes `called`.
- Reservation remains `arrived`.
- `calledAt` and `holdUntilAt` are returned and persisted.
- StorePolicy hold minutes and default 3-minute fallback.
- `queue_ticket.called` BusinessEvent.
- `queue_ticket waiting -> called` StateTransitionLog.
- `queue.call` AuditLog.
- Idempotency completed/replay/in-progress/failed/conflict.
- Already-called success-like response without duplicate evidence writes.
- QueueTicket not found.
- QueueTicket not waiting.
- Related Reservation not found.
- Related Reservation not arrived.
- Already-called missing evidence.
- App Gate tenant/store/permission denials.
- No Seating, Table assignment/status change, Cleaning, Turnover, No-show, Cancellation, API/UI/migration side effects.

### 13. Local Runtime Security

Updated local runtime security allowlist for:

```http
POST /api/v1/stores/*/queue-tickets/*/call
```

`QueueCallLocalRuntimeSecurityTest` verifies local profile requests reach the controller when the configured local actor has `queue.call`.

### 14. Commands

Initial TDD red command:

```text
mvn -q "-Dtest=QueueCall*Test,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test
```

Initial result:

- Failed at test compilation because the Queue Call API controller/DTO/mapper/error classes and `AppGateRequiredPermission.QUEUE_CALL` did not exist yet.

Executed after implementation:

```text
mvn -q "-Dtest=QueueCall*Test" test
mvn -q "-Dtest=QueueCallLocalRuntimeSecurityTest" test
mvn -q "-Dtest=AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test
mvn test
npm run build
```

### 15. Test Results

`mvn -q "-Dtest=QueueCall*Test" test`:

```text
Exit code: 0
```

`mvn -q "-Dtest=QueueCallLocalRuntimeSecurityTest" test`:

```text
Exit code: 0
```

`mvn -q "-Dtest=AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test`:

```text
Exit code: 0
```

`mvn test`:

```text
Tests run: 428, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

`npm run build`:

```text
vue-tsc --noEmit passed
vite build passed
65 modules transformed
```

### 16. Boundary Check

Seating implemented: No  
Table status changed: No  
Queue Skip implemented: No  
Queue Rejoin implemented: No  
Queue Display implemented: No  
Table assignment implemented: No  
Reservation status changed: No  
No-show implemented: No  
Cancellation implemented: No  
Cleaning implemented: No  
Turnover implemented: No  
Controller created: Yes, Queue Call API controller only  
API DTO created: Yes, Queue Call API request/response only  
UI implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No  

Additional checks:

- No Flyway migration was created or modified.
- `V001__reservation_platform_bootstrap.sql` was not modified.
- `V002__app_gate_foundation.sql` was not modified.
- Existing boundary tests now allow only the approved Queue Call API whitelist under `src/main/java/com/rpb/reservation/queue/api`.
- Local PostgreSQL test fixtures were used only for automated tests; no production database was touched.

### 17. Open Questions / Conflicts

Open Questions:

- None for Queue Call API V1.

Open Conflicts:

- None.

### 18. Next Step Recommendation

- Next slice should be a separately approved Queue UI or Queue Skip/Rejoin/Display contract.
- Keep Seating from Queue, Table assignment, Auto assignment, No-show, Cancellation, Cleaning, Turnover, migrations, and production seed/data changes out of scope unless separately approved.
