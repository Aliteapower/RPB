# Reservation Arrived Direct Seating API Implementation Report V1

## 1. Read Documents

- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF_REPORT.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md`
- `docs/backend/APP_GATE_REJECTION_CODES.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `docs/api/RESERVATION_CHECKIN_API_CONTRACT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_API_IDEMPOTENCY_CONTRACT.md`
- `docs/api/RESERVATION_API_ERROR_CONTRACT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

## 2. API Contract

Created `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`.

The implemented contract exposes only:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct
```

It maps an `arrived` Reservation to `seated` through the existing `ReservationArrivedDirectSeatingApplicationService`.

## 3. Created / Updated Files

Created:

- `src/main/java/com/rpb/reservation/reservation/api/SeatArrivedReservationRequest.java`
- `src/main/java/com/rpb/reservation/reservation/api/SeatArrivedReservationResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiMapper.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiErrorMapper.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationArrivedDirectSeatingSecurityTest.java`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`

Updated:

- `src/main/java/com/rpb/reservation/reservation/api/ReservationController.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationApiErrorCode.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationCheckInControllerTest.java`
- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationCheckInSecurityTest.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`

## 4. Endpoint

Implemented on existing `ReservationController`:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct
```

Fresh success, already-seated success-like response, and completed idempotency replay all return `200 OK`.

## 5. App Gate

The endpoint declares:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")
```

Added `reservation.seat` to `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.

Tests cover:

- Endpoint annotation uses `appKey = reservation_queue`.
- Endpoint annotation uses `permission = reservation.seat`.
- App Gate visible-app metadata recognizes actors that hold `reservation.seat`.
- App Gate denial writes `app_gate_audit_logs`.
- App Gate denial does not mutate Reservation, Seating, resource, event, transition, audit, or idempotency business data.

## 6. Request DTO

`SeatArrivedReservationRequest` contains only:

- `tableId`
- `tableGroupId`
- `overrideReasonCode`
- `overrideNote`
- `note`

The DTO validates:

- Both `tableId` and `tableGroupId` present: `RESOURCE_SELECTION_CONFLICT`.
- Neither `tableId` nor `tableGroupId` present: `RESOURCE_SELECTION_REQUIRED`.
- Exactly one resource target present: valid.

It does not expose trusted scope, target actor, Reservation status, Seating status, Queue, No-show, Cancellation, or lifecycle fields.

## 7. Response DTO

`SeatArrivedReservationResponse` returns:

- `success`
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

The API mapper converts application `resourceType = dining_table` to response `resourceType = table`; `table_group` remains `table_group`.

## 8. Error Mapping

Added Direct Seating API error mapping for:

- Missing idempotency key.
- Invalid command.
- Resource selection conflict/required.
- Store not found / scope mismatch / access denied.
- Reservation not found.
- Non-arrived and terminal Reservation status failures.
- Already-seated-without-active-seating conflict.
- Table and TableGroup lookup, availability, capacity, member, and lock failures.
- Seating source/resource validation failures.
- Idempotency conflict, in-progress, and failed-key cases.
- Event, transition, audit, repository, and persistence failures.

The error envelope remains:

```json
{
  "success": false,
  "error": {
    "code": "...",
    "messageKey": "...",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

## 9. Idempotency

The API passes `Idempotency-Key` to `SeatArrivedReservationCommand`.

Covered behavior:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Fresh success completes idempotency with action `seat_arrived_reservation`.
- Completed same-key replay returns `idempotency.replayed = true`.
- Same hash in progress returns `IDEMPOTENCY_IN_PROGRESS`.
- Hash conflict and failed-key mappings are covered in controller tests.

## 10. Already Seated

Already-seated behavior returns:

- `reservationStatus = seated`
- `seatingStatus = seated`
- `alreadySeated = true`
- `events = []`
- `idempotency.status = completed`

PostgreSQL integration test confirms no duplicate BusinessEvent, StateTransitionLog, AuditLog, Seating, or resource mutation is written.

## 11. App Gate Tests

`ReservationArrivedDirectSeatingApiIntegrationTest` validates App Gate with SpringBootTest + MockMvc + local PostgreSQL:

- Allowed path mutates Reservation and creates Seating evidence.
- Missing `reservation.seat` permission is denied and audited.
- Denied request leaves Reservation status unchanged.
- Denied request does not write business event, state transition, application audit log, seating, resource mutation, or idempotency record.

`AppGateRequiredPermissionTest`, `AppGateServiceTest`, and `AppGateGuardIntegrationTest` validate the Java metadata registry and endpoint annotation.

## 12. API / Integration Tests

Unit/controller tests cover:

- Table success response and command mapping.
- TableGroup success response and command mapping.
- Already-seated response.
- Completed idempotency replay.
- Missing idempotency key.
- Resource XOR validation.
- DTO boundary.
- App Gate annotation.
- Application error mapping.
- Role, permission, and Store scope checks.

PostgreSQL integration tests cover:

- `reservations.status = seated`.
- `seatings.status = seated`.
- `seating_resources` resource evidence.
- `business_events` contains `reservation.seated`, `seating.created`, `table.occupied`.
- `state_transition_logs` contains `reservation.seat`, `seating.occupy`, `dining_table.occupy`.
- `audit_logs.operation_code = reservation.seat`.
- `idempotency_records.action = seat_arrived_reservation`.
- Already-seated no duplicate evidence.
- Failed application paths leave business state unchanged and mark idempotency failed when appropriate.
- App Gate denial audit and no business mutation.

## 13. Local Runtime Security

Updated local runtime security to allow:

```http
POST /api/v1/stores/*/reservations/*/seating/direct
```

`LocalRuntimeReservationArrivedDirectSeatingSecurityTest` verifies the local profile permits the request through local security and reaches the App Gate/controller layer with the local actor configured for `reservation.seat`.

## 14. Commands

Executed initial TDD red command:

```text
mvn -q "-Dtest=ReservationArrivedDirectSeatingControllerTest,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest,LocalRuntimeReservationArrivedDirectSeatingSecurityTest" test
```

Initial result:

- Failed at test compilation because the API DTO/mapper/error mapper and `AppGateRequiredPermission.RESERVATION_SEAT` did not exist yet.

Executed after implementation:

```text
mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test
```

Result:

- Exit code: 0.
- Covered `ReservationArrivedDirectSeatingApiIntegrationTest`, `ReservationArrivedDirectSeatingControllerTest`, and `ReservationArrivedDirectSeatingApplicationServiceTest`.

Executed:

```text
mvn -q "-Dtest=LocalRuntimeReservationArrivedDirectSeatingSecurityTest" test
```

Result:

- Exit code: 0.

Executed:

```text
mvn test
```

Result:

- Tests run: 338.
- Failures: 0.
- Errors: 0.
- Skipped: 0.
- Build success.

Executed:

```text
npm run build
```

Result:

- Exit code: 0.
- `vue-tsc --noEmit` passed.
- `vite build` passed.
- 53 modules transformed.

## 15. Boundary Check

Queue API implemented: No  
Queue UI implemented: No  
Reservation Arrived Direct Seating UI implemented: No  
No-show API implemented: No  
Cancellation API implemented: No  
Reservation list/calendar implemented: No  
Migration changed: No  
SQL changed: No  
Production database touched: No  
Seed data inserted: No  
New app key created: No  
New permission model created: No  
Existing Reservation Create API path changed: No  
Existing Reservation CheckIn API path changed: No  
WalkIn business behavior changed: No  
Cleaning business behavior changed: No  

Additional boundary observations:

- `src/main/resources/db/migration` still uses the existing V001 and V002 migration files.
- The API uses the already-implemented application service and persistence model.
- The only new permission metadata key is `reservation.seat` under existing `reservation_queue`.

## 16. Open Questions

- Future UI work should decide whether `/api/me/apps` remains app-entry-only or needs an explicit capability-level contract before binding button visibility to permissions.
- Future Queue seating flow remains outside this direct seating API.

## 17. Open Conflicts

- No implementation conflict found in this API slice.

## 18. Next Step Recommendation

Recommended next step: write a separate Reservation Arrived Direct Seating UI contract or a Queue seating contract, while keeping new UI, Queue, No-show, Cancellation, migrations, and production data changes out of scope unless separately approved.
