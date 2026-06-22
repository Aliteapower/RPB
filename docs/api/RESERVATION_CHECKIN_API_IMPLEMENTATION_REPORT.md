# Reservation CheckIn API Implementation Report V1

## 1. Read Documents

- `docs/backend/RESERVATION_CHECKIN_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_CHECKIN_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_CHECKIN_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF_REPORT.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md`
- `docs/backend/APP_GATE_REJECTION_CODES.md`
- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

## 2. API Contract

Created `docs/api/RESERVATION_CHECKIN_API_CONTRACT.md`.

The implemented contract exposes only:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
```

It maps a confirmed Reservation to `arrived` through the existing `ReservationCheckInApplicationService`.

## 3. Created / Updated Files

Created:

- `src/main/java/com/rpb/reservation/reservation/api/CheckInReservationRequest.java`
- `src/main/java/com/rpb/reservation/reservation/api/CheckInReservationResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationCheckInApiMapper.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationCheckInApiErrorMapper.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationCheckInControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationCheckInApiIntegrationTest.java`
- `docs/api/RESERVATION_CHECKIN_API_CONTRACT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`

Updated:

- `src/main/java/com/rpb/reservation/reservation/api/ReservationController.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationApiErrorCode.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`

## 4. Endpoint

Implemented on existing `ReservationController`:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
```

Fresh success, already-arrived success-like response, and completed idempotency replay all return `200 OK`.

## 5. App Gate

The endpoint declares:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")
```

Integration tests cover:

- Enabled tenant + enabled store + actor permission allowed.
- Tenant not entitled denied and audited.
- Store app disabled denied and audited.
- Missing `reservation.check_in` permission denied and audited.

## 6. Request DTO

`CheckInReservationRequest` contains only:

- `arrivedAt`
- `reasonCode`
- `note`

It does not expose trusted scope, target, queue, seating, table, no-show, cancellation, or status fields.

## 7. Response DTO

`CheckInReservationResponse` returns:

- `success`
- `reservationId`
- `reservationCode`
- `status`
- `arrivedAt`
- `alreadyArrived`
- `events`
- `idempotency`

## 8. Error Mapping

Added CheckIn API error mapping for:

- Missing idempotency key.
- Invalid command.
- Store not found / scope mismatch / access denied.
- Reservation not found.
- Non-confirmed and terminal Reservation status failures.
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

The API passes `Idempotency-Key` to `CheckInReservationCommand`.

Covered behavior:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Fresh success completes idempotency.
- Completed same-key replay returns `idempotency.replayed = true`.
- Same hash in progress returns `IDEMPOTENCY_IN_PROGRESS`.
- Hash conflict and failed-key mappings are covered in controller tests.

## 10. Already Arrived

Already-arrived behavior returns:

- `status = arrived`
- `alreadyArrived = true`
- `events = []`
- `idempotency.status = completed`

PostgreSQL integration test confirms no duplicate BusinessEvent, StateTransitionLog, or AuditLog is written.

## 11. App Gate Tests

`ReservationCheckInApiIntegrationTest` validates App Gate with SpringBootTest + MockMvc + local PostgreSQL:

- Allowed path mutates Reservation.
- Tenant entitlement denial writes `app_gate_audit_logs`.
- Store disabled denial writes `app_gate_audit_logs`.
- Permission denial writes `app_gate_audit_logs`.
- App Gate denials leave Reservation status unchanged.
- App Gate denials do not write Reservation business event, state transition, audit log, or idempotency record.

## 12. API / Integration Tests

Unit/controller tests cover:

- Success response and command mapping.
- Already-arrived response.
- Completed idempotency replay.
- Missing idempotency key.
- DTO boundary.
- App Gate annotation.
- Application error mapping.
- Role, permission, and Store scope checks.

PostgreSQL integration tests cover:

- `reservations.status = arrived`.
- `business_events.event_type = reservation.arrived`.
- `state_transition_logs` records `confirmed -> arrived`.
- `audit_logs.operation_code = reservation.check_in`.
- `idempotency_records.status = completed`.
- Already-arrived no duplicate evidence.
- Failed cancelled Reservation leaves business state unchanged and marks idempotency failed.
- App Gate denial audit and no business mutation.
- Boundary tables remain empty.
- No `check_ins` table exists.

## 13. Commands

Executed:

```text
mvn -q '-Dtest=ReservationCheckInControllerTest' test
```

Initial TDD red result:

- Failed at test compilation because `CheckInReservationRequest`, `ReservationCheckInApiMapper`, and `ReservationCheckInApiErrorMapper` did not exist.

Executed after implementation:

```text
mvn -q '-Dtest=ReservationCheckInControllerTest' test
```

Result:

- Exit code: 0.
- Tests passed.

Executed:

```text
mvn -q '-Dtest=ReservationCheckInApiIntegrationTest' test
```

Result:

- Exit code: 0.
- Tests passed against temporary local PostgreSQL.

Executed:

```text
mvn -q '-Dtest=ReservationCheckIn*Test' test
```

Result:

- Exit code: 0.

Executed:

```text
mvn test
```

Result:

- Tests run: 295.
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
- 49 modules transformed.

## 14. Boundary Check

Queue API implemented: No  
Seating API implemented: No  
Table assignment API implemented: No  
No-show API implemented: No  
Cancellation API implemented: No  
CheckInEntity created: No  
check_ins table created: No  
UI implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No  
Existing API paths changed: No

Additional boundary observations:

- `src/main/resources/db/migration` still contains only V001 and V002.
- `arrived_at` remains only on existing `walk_ins`; no `reservations.arrived_at` was added.
- `CheckInEntity` appears only in existing documentation/comment boundary text, not as a Java entity class.

## 15. Open Questions

- Future schema decision remains open: whether Reservation arrival time should stay event/transition/audit-derived or get a dedicated `reservations.arrived_at` column in a separately approved migration round.
- Future product decision remains open: whether `/api/me/apps` entry permission lists should include `reservation.check_in` as a visible app entry permission.

## 16. Open Conflicts

- No implementation conflict found in this API slice.
- App Gate visibility permissions currently do not list `reservation.check_in`, but endpoint authorization works because `AppGateService.evaluate` checks the annotation-required permission directly against the actor.

## 17. Next Step Recommendation

Recommended next step: Reservation CheckIn local runtime/manual smoke validation or a minimal Store Staff CheckIn UI contract, while continuing to keep Queue, Seating, Table assignment, No-show, Cancellation, migrations, and production data changes out of scope unless separately approved.
