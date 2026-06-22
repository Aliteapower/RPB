# Reservation Today View API Implementation Report V1

## 1. Read Documents

- `docs/backend/RESERVATION_TODAY_VIEW_CONTRACT.md`
- `docs/backend/RESERVATION_TODAY_VIEW_VERTICAL_SLICE_CHECKLIST.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_CONTRACT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF_REPORT.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md`
- `docs/backend/APP_GATE_REJECTION_CODES.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `docs/api/RESERVATION_CHECKIN_API_CONTRACT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_API_ERROR_CONTRACT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/skills/reservation-system/SKILL.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

## 2. API Contract

Created `docs/api/RESERVATION_TODAY_VIEW_API_CONTRACT.md`.

The implemented contract exposes only:

```http
GET /api/v1/stores/{storeId}/reservations/today
```

It returns a read-only Store-local business-date reservation view. It does not mutate Reservation state, create QueueTicket, create Seating, assign tables, use idempotency, or write business audit/event/transition records.

## 3. Created / Updated Files

Created:

- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewApiMapper.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewApiErrorResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/reservation/application/ReservationTodayViewError.java`
- `src/main/java/com/rpb/reservation/reservation/application/ReservationTodayViewItem.java`
- `src/main/java/com/rpb/reservation/reservation/application/ReservationTodayViewResult.java`
- `src/main/java/com/rpb/reservation/reservation/application/query/ReservationTodayViewQuery.java`
- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationTodayViewApplicationService.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationTodayViewRow.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationTodayViewProjection.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationTodayViewControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/integration/ReservationTodayViewApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationTodayViewSecurityTest.java`
- `docs/api/RESERVATION_TODAY_VIEW_API_CONTRACT.md`
- `docs/api/RESERVATION_TODAY_VIEW_API_IMPLEMENTATION_REPORT.md`

Updated:

- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationApiErrorCode.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationJpaRepository.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/ReservationCheckInUiImplementationValidationTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/StoreStaffHomePageAppGateRuntimeValidationTest.java`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`

No frontend Vue page, router, or API client was changed in this API implementation round.

## 4. Endpoint

Implemented:

```http
GET /api/v1/stores/{storeId}/reservations/today
```

Supported query parameters:

- `businessDate`, optional, `yyyy-MM-dd`.
- `status`, optional, default `operational`.

Default `businessDate` is calculated from the Store timezone through the application service clock.

## 5. App Gate

The endpoint declares:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")
```

Added:

- `AppGateRequiredPermission.RESERVATION_TODAY_VIEW = "reservation.today_view"`
- `reservation.today_view` under `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`

Tests cover:

- Endpoint annotation uses `appKey = reservation_queue`.
- Endpoint annotation uses `permission = reservation.today_view`.
- `/api/me/apps` visible-app metadata recognizes actors that hold `reservation.today_view`.
- App Gate denial writes `app_gate_audit_logs` with `APP_GATE_DENIED`.
- App Gate denial leaves Reservation, Queue, Seating, TableLock, event, transition, audit, and idempotency business data unchanged.

## 6. Query / Status Filter

Supported status filters:

- `operational` -> `confirmed`, `arrived`, `seated`
- `all` -> `confirmed`, `arrived`, `seated`, `cancelled`, `no_show`, `completed`
- Single status -> `confirmed`, `arrived`, `seated`, `cancelled`, `no_show`, or `completed`

Rejected:

- invalid date format
- unsupported status value
- `draft`

Items are sorted by:

```text
reservedStartAt ASC
createdAt ASC
```

## 7. Response DTO

`ReservationTodayViewResponse` returns:

- `success`
- `storeId`
- `businessDate`
- `storeTimezone`
- `statusFilter`
- `items`

Each item returns:

- `reservationId`
- `reservationCode`
- `status`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `businessDate`
- `customerName`
- `customerNickname`
- `phoneMasked`
- `note`

Privacy boundary:

- `phoneE164` is never returned.
- phone values are masked to the final four digits, for example `+6591234567 -> ****4567`.
- action capabilities such as `canCheckIn` or `canSeat` are not returned.

## 8. Error Mapping

Added Today View API error mapping for:

- invalid command
- store not found
- store scope mismatch
- store access denied
- invalid business date
- invalid status filter
- persistence failure

The non-idempotent error envelope is:

```json
{
  "success": false,
  "error": {
    "code": "...",
    "messageKey": "...",
    "details": {}
  }
}
```

Added message keys:

```text
reservation.today_view.invalid_business_date
reservation.today_view.invalid_status_filter
```

App Gate denials continue to use the existing App Gate envelope.

## 9. Persistence

The API reads:

- `stores`
- `reservations`
- `customers`

The persistence query filters by:

- tenant scope
- store scope
- business date
- allowed status set
- non-deleted reservations

The API does not write:

- `reservations`
- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`
- `queue_tickets`
- `seatings`
- `table_locks`

Only denied App Gate checks may write `app_gate_audit_logs`.

## 10. Local Runtime Security

Updated local runtime security to allow:

```http
GET /api/v1/stores/*/reservations/today
```

`LocalRuntimeReservationTodayViewSecurityTest` verifies the local profile permits the request through local security and reaches the App Gate/controller layer with the local actor configured for `reservation.today_view`.

## 11. API / Integration Tests

Controller tests cover:

- App Gate annotation.
- Successful response mapping.
- query mapping to `ReservationTodayViewQuery`.
- controller-level role, permission, and Store access checks.

PostgreSQL integration tests cover:

- default Store-timezone business date.
- `operational` status set.
- `all` status set.
- explicit single status.
- invalid business date.
- invalid status filter.
- phone masking and no `phoneE164` leak.
- read-only success path with no business writes.
- App Gate permission denial audit and no business mutation.
- no Queue, Seating, or TableLock side effects.

App Gate tests cover:

- `reservation.today_view` in the Java registry.
- `/api/me/apps` recognizes `reservation.today_view` as a `reservation_queue` entry permission.

## 12. Commands

Executed initial TDD red command:

```text
mvn -q "-Dtest=ReservationTodayView*Test" test
```

Initial result:

- Failed at test compilation because Today View API/application/persistence classes and `AppGateRequiredPermission.RESERVATION_TODAY_VIEW` did not exist yet.

Executed after implementation:

```text
mvn -q "-Dtest=ReservationTodayView*Test" test
```

Result:

- Exit code: 0.
- Covered `ReservationTodayViewControllerTest` and `ReservationTodayViewApiIntegrationTest`.

Executed:

```text
mvn -q "-Dtest=LocalRuntimeReservationTodayViewSecurityTest" test
```

Result:

- Exit code: 0.

Executed:

```text
mvn test
```

Result:

- Tests run: 352.
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
- 57 modules transformed.

## 13. Boundary Check

Backend API path changed: No  
Existing Reservation Create API path changed: No  
Existing Reservation CheckIn API path changed: No  
Existing Reservation Arrived Direct Seating API path changed: No  
Business state machine changed: No  
Queue API implemented: No  
Queue UI implemented: No  
No-show API implemented: No  
Cancellation API implemented: No  
Reservation list/calendar implemented: No  
Table map implemented: No  
Table assignment implemented: No  
Seating mutation implemented by Today View: No  
CheckIn mutation implemented by Today View: No  
Idempotency used by Today View: No  
Migration changed: No  
SQL changed: No  
Production database touched: No  
Seed data inserted: No  
New app key created: No  
New permission model created: No  
Frontend Vue/router/API client changed: No  

Additional boundary observations:

- `src/main/resources/db/migration` still uses the existing migration files.
- The only new permission metadata key is `reservation.today_view` under existing `reservation_queue`.
- The API reads existing Reservation and Customer data and returns a read-only projection.

## 14. Open Questions

- Future Today View UI should resolve the existing product tension between App Gate Option A app-level entry visibility and button-level `reservation.today_view` visibility before binding Staff Home controls to permissions.
- Future action shortcuts from Today View to CheckIn or Seating should be implemented only in a separately approved UI round and should call the existing action pages/APIs, not mutate inside Today View.

## 15. Open Conflicts

- No implementation conflict found in this API slice.

## 16. Next Step Recommendation

Recommended next step: implement the separately contracted Today View UI slice, including Staff Home visibility rules, while keeping Queue, No-show, Cancellation, Table map, new migrations, and production data changes out of scope unless separately approved.
