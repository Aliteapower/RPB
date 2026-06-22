# App Gate Permission Metadata Alignment V6

## 1. Purpose

This document fixes the App Gate permission metadata alignment for the already implemented Reservation CheckIn API, Reservation Arrived Direct Seating API, Reservation Today View API, Reservation Arrived To Queue API, Queue List Read API, Queue Call API, Seating From Called Queue API, and Queue Skip API.

Confirmed gates:

```text
app_key = reservation_queue
permission = reservation.check_in

app_key = reservation_queue
permission = reservation.seat

app_key = reservation_queue
permission = reservation.today_view

app_key = reservation_queue
permission = reservation.queue

app_key = reservation_queue
permission = queue.view

app_key = reservation_queue
permission = queue.call

app_key = reservation_queue
permission = queue.seat

app_key = reservation_queue
permission = queue.skip
```

This alignment does not create Queue UI, Queue rejoin/display, Reservation list/calendar, No-show, Cancellation, a new app key, a new permission model, a migration, SQL changes, seed data, or production data changes.

## 2. Current State

App Gate V1 has one platform app for the reservation operations loop:

```text
reservation_queue
```

`V002__app_gate_foundation.sql` stores:

- `platform_apps`
- `tenant_app_entitlements`
- `store_app_settings`
- `app_gate_audit_logs`

V002 does not define a permission table and does not seed individual permission rows.

Current permission metadata sources:

- `@RequireAppGate(appKey = "...", permission = "...")` on protected endpoints.
- `CurrentActor.permissions` from the server-side actor/RBAC boundary.
- `AppGateService.evaluate(...)`, which checks the annotation-required permission against the actor.
- `AppGateRequiredPermission`, the Java registry for the current `reservation_queue` entry permission set.
- `AppGateService.visibleApps(...)`, which projects visible app entries for `/api/me/apps`.

Current baseline confirmations:

- App Gate Runtime Validation V1 passed.
- App Gate Operational Handoff V1 passed.
- Reservation CheckIn API passed.
- Reservation Arrived Direct Seating application slice passed.
- Reservation Arrived Direct Seating API uses App Gate for endpoint authorization.
- Reservation Today View API uses App Gate for endpoint authorization.
- Reservation Arrived To Queue API uses App Gate for endpoint authorization.
- Queue List Read API uses App Gate for endpoint authorization.
- Queue Call API uses App Gate for endpoint authorization.
- Seating From Called Queue API uses App Gate for endpoint authorization.
- Queue Skip API uses App Gate for endpoint authorization.
- App Gate deny audit writes `APP_GATE_DENIED`.
- No Queue rejoin/display, Queue UI, No-show, Cancellation, or Reservation list/calendar slice exists.

## 3. Confirmed Permission Keys

Confirmed current `reservation_queue` permission keys:

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

These are stable RBAC capability keys. They are not page names, controller names, URL fragments, or temporary labels.

## 4. Confirmed Runtime Permissions

Reservation CheckIn:

```text
permission = reservation.check_in
```

It is used by:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")
```

Reservation Arrived Direct Seating:

```text
permission = reservation.seat
```

It is used by:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")
```

Reservation Today View:

```text
permission = reservation.today_view
```

It is used by:

```http
GET /api/v1/stores/{storeId}/reservations/today
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")
```

Reservation Arrived To Queue:

```text
permission = reservation.queue
```

It is used by:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")
```

Queue List Read:

```text
permission = queue.view
```

It is used by:

```http
GET /api/v1/stores/{storeId}/queue-tickets
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")
```

Queue Call:

```text
permission = queue.call
```

It is used by:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")
```

Seating From Called Queue:

```text
permission = queue.seat
```

It is used by:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")
```

Queue Skip:

```text
permission = queue.skip
```

It is used by:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
```

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")
```

Do not rename these permissions to:

```text
reservation.arrive
reservation.arrival
reservation.checkin
checkin.create
reservation.direct_seat
reservation.direct_seating
reservation.seating
seating.create
reservation.today
reservation.list.today
reservation.calendar.today
today_reservations
reservation.waitlist
queue.create
reservation.queue_view
reservation.view_queue
queue_ticket.view
reservation.queue_call
reservation.call
queue_ticket.call
queue_ticket.seat
reservation.queue_seat
reservation.seat_from_queue
```

## 5. app_key Mapping

`reservation.check_in`, `reservation.seat`, `reservation.today_view`, `reservation.queue`, `queue.view`, `queue.call`, `queue.seat`, and `queue.skip` belong to:

```text
app_key = reservation_queue
```

Do not create:

```text
reservation_checkin
checkin_app
arrival_app
reservation_seating
seating_app
direct_seating_app
```

The mapping stays inside the existing operational loop:

```text
Reservation / WalkIn
-> CheckIn when applicable
-> QueueTicket when waiting is needed
-> Seating
-> Cleaning
-> Turnover
```

## 6. /api/me/apps V1 Behavior

Endpoint:

```text
GET /api/me/apps?storeId=xxx
```

V1 product decision:

```text
Option A: app-level entry only
```

Meaning:

- `/api/me/apps` is used to decide whether the `reservation_queue` app entry is visible and usable for the current actor/store.
- Staff Home should treat the endpoint as an app-entry source, not as a button-level capability matrix.
- Backend business APIs plus App Gate remain the final authorization source for every concrete action.
- CheckIn API access is still enforced by `@RequireAppGate(... permission = "reservation.check_in")`.
- Arrived Direct Seating API access is still enforced by `@RequireAppGate(... permission = "reservation.seat")`.
- Today View API access is still enforced by `@RequireAppGate(... permission = "reservation.today_view")`.
- Arrived To Queue API access is still enforced by `@RequireAppGate(... permission = "reservation.queue")`.
- Queue List Read API access is still enforced by `@RequireAppGate(... permission = "queue.view")`.
- Queue Call API access is still enforced by `@RequireAppGate(... permission = "queue.call")`.
- Seating From Called Queue API access is still enforced by `@RequireAppGate(... permission = "queue.seat")`.

Current wire response remains compatible with the existing App Gate foundation response shape:

```text
appKey
appName
status
entryRoute
entryVisible
permissions
```

The existing `permissions` field is not a complete permission catalog and is not a V1 capability-level contract. It is a filtered, actor-owned entry-permission metadata field used by the backend projection to decide app-entry eligibility. V1 Staff Home must not use it to add or hide CheckIn or Seating buttons.

This V1 does not return:

```text
capabilities
actions
buttons
complete permission matrix
permissions the actor does not have
```

For `reservation_queue`, the backend registry of entry permissions is:

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

If an actor has only `reservation.check_in`, only `reservation.seat`, only `reservation.today_view`, only `reservation.queue`, only `queue.view`, only `queue.call`, only `queue.seat`, or only `queue.skip`, the backend may recognize that actor as eligible for the `reservation_queue` app entry. That does not mean Staff Home should render new CheckIn, Seating, Today View, Queue, Queue List, Queue Call, Queue Seat, or Queue Skip entries outside a separately approved UI contract.

## 7. Staff Home Behavior

This alignment round does not create or change UI.

Staff Home continues to show only the existing approved operational entries unless a separate UI contract has already implemented another entry:

```text
WalkIn Direct Seating
Cleaning Complete
Create Reservation
Reservation CheckIn, only if already approved by the Reservation CheckIn UI slice
Reservation Today View, only if already approved by the Reservation Today View UI slice
```

Staff Home must not add:

```text
Queue UI
Queue Call UI
Reservation Arrived Direct Seating UI, unless separately approved
No-show
Cancellation
Reservation Calendar/List
```

Manual URL entry for protected APIs remains governed by backend App Gate and endpoint-level permission checks.

## 8. Future Capability Model

Future work may introduce:

```text
Option B: capability-level response
```

Only use Option B in a separately approved UI/API-product round, for example when Staff Home needs button-level dynamic visibility for CheckIn, Queue, Seating, No-show, or Cancellation actions.

Future Option B must:

- keep `app_key = reservation_queue`.
- keep `permission = reservation.check_in` for CheckIn.
- keep `permission = reservation.seat` for Reservation Arrived Direct Seating.
- keep `permission = reservation.today_view` for Today View.
- keep `permission = reservation.queue` for Reservation Arrived To Queue.
- keep `permission = queue.view` for Queue List Read.
- keep `permission = queue.call` for Queue Call.
- keep `permission = queue.seat` for Seating From Called Queue.
- keep `permission = queue.skip` for Queue Skip.
- expose only actor-owned capabilities.
- avoid leaking unavailable tenant/store/app permissions.
- keep backend App Gate as final authorization.
- define an explicit response contract before frontend code binds to it.

Future Option B must not:

- create a new app key for CheckIn.
- create a new app key for Seating.
- create an unapproved permission model.
- add a migration without approval.
- bind Staff Home to a capability matrix before the UI contract exists.

## 9. Tests / Validation

Existing App Gate, Reservation CheckIn, and Reservation Arrived Direct Seating coverage validates:

- `reservation.check_in` is a stable App Gate permission key.
- `reservation.seat` is a stable App Gate permission key.
- `reservation.check_in` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `reservation.seat` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `reservation.today_view` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `reservation.queue` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `queue.view` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `queue.call` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `queue.seat` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `queue.skip` is in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- App Gate guard enforces `reservation.check_in`.
- App Gate guard enforces `reservation.seat`.
- App Gate guard enforces `reservation.today_view`.
- App Gate guard enforces `reservation.queue`.
- App Gate guard enforces `queue.view`.
- App Gate guard enforces `queue.call`.
- App Gate guard enforces `queue.seat`.
- App Gate guard enforces `queue.skip`.
- App Gate denials for CheckIn, Arrived Direct Seating, Today View, Queue List, Queue Call, and Seating From Called Queue are audited.
- `/api/me/apps` still returns `reservation_queue` when the app is visible, enabled, and actor-eligible.
- Staff Home gates the existing surface by `reservation_queue`.

Required validation for this alignment:

```text
mvn -q "-Dtest=QueueCall*Test" test
mvn -q "-Dtest=ReservationArrivedToQueue*Test" test
mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test
mvn -q "-Dtest=LocalRuntimeReservationArrivedDirectSeatingSecurityTest" test
mvn -q "-Dtest=ReservationTodayView*Test" test
mvn -q "-Dtest=LocalRuntimeReservationTodayViewSecurityTest" test
mvn -q "-Dtest=QueueTicketList*Test,QueueList*Test" test
mvn -q "-Dtest=SeatingFromCalledQueue*Test" test
mvn test
npm run build
```

## 10. Non-Scope

This alignment does not implement or modify:

- Queue UI.
- Queue skip/rejoin/display.
- Queue Call UI.
- Reservation Arrived Direct Seating UI.
- Reservation Today View UI.
- No-show API or UI.
- Cancellation API or UI.
- Reservation list/calendar.
- Business state machine.
- WalkIn business behavior.
- Cleaning business behavior.
- Existing business API paths except the approved Arrived Direct Seating endpoint.
- Flyway migrations.
- SQL files.
- Database schema.
- Production config.
- Production database access.
- Production seed data.
