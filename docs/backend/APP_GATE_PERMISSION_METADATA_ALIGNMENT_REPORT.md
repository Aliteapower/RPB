# App Gate Permission Metadata Alignment Report V6

## App Gate Permission Metadata Alignment Result

### 1. Read Documents

Read and checked:

- `docs/backend/APP_GATE_FOUNDATION_V1_IMPLEMENTATION_REPORT.md`
- `docs/backend/APP_GATE_RUNTIME_VALIDATION_V1_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF_REPORT.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_REJECTION_CODES.md`
- `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/api/RESERVATION_CHECKIN_API_CONTRACT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_TODAY_VIEW_API_CONTRACT.md`
- `docs/api/RESERVATION_TODAY_VIEW_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_TODAY_VIEW_CONTRACT.md`
- `docs/backend/RESERVATION_TODAY_VIEW_VERTICAL_SLICE_CHECKLIST.md`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`
- `src/main/java/com/rpb/reservation/appgate/**`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationController.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java`
- `src/test/java/com/rpb/reservation/appgate/**`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Confirmed:

- App Gate Runtime Validation V1 passed.
- App Gate Operational Handoff V1 passed.
- Reservation CheckIn API passed.
- Reservation Arrived Direct Seating API passed.
- Reservation Today View API passed.
- Current full backend verification passes with 428 tests.
- `npm run build` passes.
- App Gate guard enforces `reservation.check_in`.
- App Gate guard enforces `reservation.seat`.
- App Gate guard enforces `reservation.today_view`.
- App Gate guard enforces `reservation.queue`.
- App Gate guard enforces `queue.view`.
- App Gate guard enforces `queue.call`.
- App Gate guard enforces `queue.seat`.
- App Gate guard enforces `queue.skip`.
- App Gate deny audit works.
- No Queue UI, Queue skip/rejoin/display, No-show, Cancellation, Table map, or Reservation list/calendar slice was created in this alignment/API round.

### 2. Created / Updated Files

Updated:

- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/guard/AppGateGuardIntegrationTest.java`

Related Today View API files for the approved endpoint:

- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewApiMapper.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewApiErrorResponse.java`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationTodayViewApplicationService.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationTodayViewRow.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationTodayViewProjection.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `docs/api/RESERVATION_TODAY_VIEW_API_CONTRACT.md`
- `docs/api/RESERVATION_TODAY_VIEW_API_IMPLEMENTATION_REPORT.md`

No SQL, migration, production config, or frontend UI file was changed for permission metadata alignment.

### 3. Current Metadata Source

Current App Gate V1 has no permission metadata table and no permission seed.

Metadata comes from:

- `@RequireAppGate(appKey = "...", permission = "...")` on protected endpoints.
- `CurrentActor.permissions` from the server-side actor/RBAC boundary.
- `AppGateService.evaluate(...)`, which checks the annotation-required permission.
- `AppGateRequiredPermission`, the Java registry for current `reservation_queue` entry permissions.
- `/api/me/apps` projection through `AppGateService.visibleApps(...)` and `MeAppsResponse`.
- V002 app/entitlement/store-setting seed and backfill for `reservation_queue`, but not permission rows.

### 4. Confirmed Permission List

Current confirmed `reservation_queue` permission list:

```text
walkin.direct_seating.create
cleaning.start
cleaning.complete
reservation.create
reservation.check_in
reservation.seat
reservation.today_view
reservation.queue
queue.view
queue.call
queue.seat
queue.skip
```

### 5. reservation.check_in Mapping

```text
app_key: reservation_queue
permission: reservation.check_in
```

Endpoint:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")
```

### 6. reservation.seat Mapping

```text
app_key: reservation_queue
permission: reservation.seat
```

Endpoint:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")
```

### 7. reservation.today_view Mapping

```text
app_key: reservation_queue
permission: reservation.today_view
```

Endpoint:

```http
GET /api/v1/stores/{storeId}/reservations/today
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")
```

Do not create:

```text
reservation_today
today_reservations
reservation_calendar
reservation_list
today_view_app
```

### 8. reservation.queue Mapping

```text
app_key: reservation_queue
permission: reservation.queue
```

Endpoint:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")
```

Do not create:

```text
queue_app
reservation_waitlist
reservation.waitlist
queue.create
```

### 9. queue.view Mapping

```text
app_key: reservation_queue
permission: queue.view
```

Endpoint:

```http
GET /api/v1/stores/{storeId}/queue-tickets
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")
```

Do not create:

```text
queue_app
reservation.queue_view
reservation.view_queue
queue_ticket.view
```

### 10. queue.call Mapping

```text
app_key: reservation_queue
permission: queue.call
```

Endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")
```

Do not create:

```text
queue_app
reservation.queue_call
reservation.call
queue_ticket.call
```

### 11. queue.seat Mapping

```text
app_key: reservation_queue
permission: queue.seat
```

Endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")
```

### 12. queue.skip Mapping

```text
app_key: reservation_queue
permission: queue.skip
```

Endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")
```

Do not create:

```text
queue.skip UI
queue.rejoin
queue.display
queue.workbench
```

Do not create:

```text
queue_app
reservation.queue_seat
reservation.seat_from_queue
queue_ticket.seat
seating.create
```

### 12. /api/me/apps Decision

V1 decision:

```text
Option A: app-level entry only
```

Meaning:

- `/api/me/apps` is used for app-entry visibility and availability.
- Staff Home must not treat the response as a complete button/action capability matrix unless a later contract explicitly upgrades it.
- Concrete action authorization remains with backend business APIs and App Gate.
- Today View API access is enforced by `@RequireAppGate(... permission = "reservation.today_view")`.
- Arrived To Queue API access is enforced by `@RequireAppGate(... permission = "reservation.queue")`.
- Queue List Read API access is enforced by `@RequireAppGate(... permission = "queue.view")`.
- Queue Call API access is enforced by `@RequireAppGate(... permission = "queue.call")`.
- Seating From Called Queue API access is enforced by `@RequireAppGate(... permission = "queue.seat")`.
- Queue Skip API access is enforced by `@RequireAppGate(... permission = "queue.skip")`.

Compatibility note:

- The current wire response still includes the existing `permissions` field from the App Gate foundation implementation.
- That field is not a complete permission catalog.
- It is filtered to actor-owned entry permissions for the app projection.
- Future UI rounds must explicitly decide how Staff Home uses `reservation.today_view` for button visibility before adding or changing UI entries.

### 13. Staff Home Decision

No Queue List UI, Queue Call UI, or Queue Seat UI was created in this API/alignment round.

This round does not add:

```text
Queue UI
Queue skip/rejoin/display
No-show
Cancellation
Reservation Calendar/List
Table map
```

Manual URL entry for protected APIs remains governed by backend App Gate and endpoint-level permission checks.

### 14. Code Changes

Code now contains:

- `AppGateRequiredPermission.RESERVATION_CHECK_IN = "reservation.check_in"`
- `AppGateRequiredPermission.RESERVATION_SEAT = "reservation.seat"`
- `AppGateRequiredPermission.RESERVATION_TODAY_VIEW = "reservation.today_view"`
- `AppGateRequiredPermission.RESERVATION_QUEUE = "reservation.queue"`
- `AppGateRequiredPermission.QUEUE_VIEW = "queue.view"`
- `AppGateRequiredPermission.QUEUE_CALL = "queue.call"`
- `AppGateRequiredPermission.QUEUE_SEAT = "queue.seat"`
- `AppGateRequiredPermission.QUEUE_SKIP = "queue.skip"`
- current Reservation, Queue List, Queue Call, and Queue Seat permission keys in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`
- Queue List in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`
- Queue Call in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`
- Queue Seat in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`
- Queue Skip in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`
- `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")` on the CheckIn endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")` on the Arrived Direct Seating endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")` on the Today View endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")` on the Arrived To Queue endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")` on the Queue List endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")` on the Queue Call endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")` on the Seating From Called Queue endpoint
- `@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")` on the Queue Skip endpoint
- App Gate denial audit for denied CheckIn, Arrived Direct Seating, Today View, Arrived To Queue, Queue List, Queue Call, and Seating From Called Queue requests

### 15. Tests

`mvn -q "-Dtest=ReservationArrivedToQueueControllerTest,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest,LocalRuntimeReservationArrivedToQueueSecurityTest" test`:

```text
Exit code: 0
```

`mvn -q "-Dtest=ReservationArrivedToQueueApiIntegrationTest" test`:

```text
Exit code: 0
```

`mvn -q "-Dtest=ReservationTodayView*Test" test`:

```text
Exit code: 0
```

`mvn -q "-Dtest=LocalRuntimeReservationTodayViewSecurityTest" test`:

```text
Exit code: 0
```

`mvn -q "-Dtest=QueueTicketList*Test,QueueList*Test,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test`:

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
Exit code: 0
vue-tsc --noEmit passed
vite build passed
65 modules transformed
```

Queue Call API validation is recorded in `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`.
Queue List Read API validation is recorded in `docs/api/QUEUE_LIST_API_IMPLEMENTATION_REPORT.md`.

### 16. Boundary Check

New app_key created: No  
New permission model created: No  
Migration created: No  
SQL files changed: No  
Business state machine changed: No  
Existing CheckIn path changed: No  
Existing Create Reservation path changed: No  
Existing Arrived Direct Seating path changed: No  
Reservation Today View UI created: No  
Queue arrived-to-waiting API implemented: Yes  
Queue list read API implemented: Yes, approved read-only Queue List API only  
Queue UI implemented: No  
Queue call implemented: Yes, approved Queue Call API only  
Queue skip/rejoin/display implemented: No  
No-show implemented: No  
Cancellation implemented: No  
Reservation list/calendar implemented: No  
Table map implemented: No  
Production database touched: No  
Seed data inserted: No  

Additional boundary checks:

- Flyway migration files changed: No
- WalkIn business behavior changed: No
- Cleaning business behavior changed: No
- Today View is read-only and does not write business records on success.
- Only App Gate denial may write `app_gate_audit_logs`.

### 17. Open Questions

- Future Staff Home or Today View UI work must decide whether `/api/me/apps` remains app-entry-only or receives a separately approved capability-level response contract.
- Future Today View action shortcuts should remain navigation/action handoffs to approved CheckIn or Seating flows, not direct mutations inside the read-only Today View API.
- Future Queue skip/rejoin/display work must define a separate API/UI contract and permission decision before implementation.

### 18. Next Step Recommendation

- Keep CheckIn under `reservation_queue` with `permission = reservation.check_in`.
- Keep Reservation Arrived Direct Seating under `reservation_queue` with `permission = reservation.seat`.
- Keep Today View under `reservation_queue` with `permission = reservation.today_view`.
- Keep Reservation Arrived To Queue under `reservation_queue` with `permission = reservation.queue`.
- Keep Queue List Read under `reservation_queue` with `permission = queue.view`.
- Keep Queue Call under `reservation_queue` with `permission = queue.call`.
- Keep Seating From Called Queue under `reservation_queue` with `permission = queue.seat`.
- Keep Queue Skip under `reservation_queue` with `permission = queue.skip`.
- Next implement only the separately contracted Queue UI or Queue skip/rejoin/display slice, while keeping No-show, Cancellation, Table map, new migrations, and production data changes out of scope unless separately approved.
