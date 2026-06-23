# Staff Home UI Baseline Contract V1

## 1. Purpose

Freeze the approved full-permission Store Staff Home baseline before any future visual redesign.

Target page:

```text
src/pages/StoreStaffHomePage.vue
```

Target route:

```text
/stores/{storeId}/staff
```

This document is contract only. It does not change Staff Home UI, Vue Router, permissions, App Gate
metadata, backend APIs, database migrations, runtime configuration, seed data, or production data.

## 2. Baseline Decision

Future Staff Home UI work must use the latest full-permission `/staff` page as the baseline.

The full-permission baseline contains the approved staff entry hub with these 10 visible entries:

1. 散客直接入座
2. 清台处理
3. 创建预约
4. 今日预约
5. 预约到店
6. 预约排队
7. 排队列表
8. 排队叫号
9. 排队入座
10. 预约入座

A narrow-permission runtime that only shows `queue.view` / `queue.rejoin` is not the Staff Home
baseline. Missing entries under narrow permissions are expected permission behavior and must not be
treated as an old or incomplete page version.

## 3. Product Decisions

1. Staff Home is an entry hub, not a Workbench.
2. Staff Home routes staff to existing approved pages and flows.
3. Staff Home must not perform queue, rejoin, skip, call, seat, cleaning, reservation, table, no-show,
   cancellation, or turnover mutations directly.
4. Entry visibility is driven by App Gate app visibility and permissions from `/api/me/apps`.
5. Missing entries under narrow permissions are expected and are not a regression.
6. UI optimization must preserve all 10 baseline entries unless a separate product decision removes one.
7. Future visual redesign must not silently change permissions or routes.

## 4. App Gate Source

Staff Home loads:

```text
GET /api/me/apps?storeId={storeId}
```

Current frontend source:

```text
src/api/meAppsApi.ts
src/types/meApps.ts
```

Current metadata shape:

```text
appKey
appName
status
entryRoute
entryVisible
permissions
```

Staff Home currently looks for:

```text
appKey = reservation_queue
entryVisible = true
```

Each entry is then shown only when the matching permission exists in the returned `permissions` list.
Frontend checks are display hints only. Backend APIs and App Gate remain authoritative for mutations and
data access.

## 5. Entry Baseline Table

| Entry label | Target route | Required app_key | Required permission | Current implementation source | Current status |
| --- | --- | --- | --- | --- | --- |
| 散客直接入座 | `/stores/:storeId/walk-ins/direct-seating` | `reservation_queue` | `walkin.direct_seating.create` | `canSeatWalkInDirectly`, `walkInRoute` in `StoreStaffHomePage.vue` | Implemented |
| 清台处理 | `/stores/:storeId/cleaning` | `reservation_queue` | `cleaning.start` and `cleaning.complete` | `canHandleCleaning`, `cleaningRoute` in `StoreStaffHomePage.vue` | Implemented |
| 创建预约 | `/stores/:storeId/reservations/create` | `reservation_queue` | `reservation.create` | `canCreateReservation`, `reservationRoute` in `StoreStaffHomePage.vue` | Implemented |
| 今日预约 | `/stores/:storeId/reservations/today` | `reservation_queue` | `reservation.today_view` | `canViewTodayReservations`, `reservationTodayViewRoute` in `StoreStaffHomePage.vue` | Implemented |
| 预约到店 | `/stores/:storeId/reservations/check-in` | `reservation_queue` | `reservation.check_in` | `canCheckInReservation`, `reservationCheckInRoute` in `StoreStaffHomePage.vue` | Implemented |
| 预约排队 | `/stores/:storeId/reservations/queue` | `reservation_queue` | `reservation.queue` | `canQueueArrivedReservation`, `reservationArrivedToQueueRoute` in `StoreStaffHomePage.vue` | Implemented |
| 排队列表 | `/stores/:storeId/queue-tickets` | `reservation_queue` | `queue.view` | `canViewQueueTickets`, `queueTicketListRoute` in `StoreStaffHomePage.vue` | Implemented |
| 排队叫号 | `/stores/:storeId/queue-tickets/call` | `reservation_queue` | `queue.call` | `canCallQueueTicket`, `queueCallRoute` in `StoreStaffHomePage.vue` | Implemented |
| 排队入座 | `/stores/:storeId/queue-tickets/seating/direct` | `reservation_queue` | `queue.seat` | `canSeatCalledQueueTicket`, `seatingFromCalledQueueRoute` in `StoreStaffHomePage.vue` | Implemented |
| 预约入座 | `/stores/:storeId/reservations/seating/direct` | `reservation_queue` | `reservation.seat` | `canSeatArrivedReservation`, `reservationArrivedDirectSeatingRoute` in `StoreStaffHomePage.vue` | Implemented |

Current backend permission metadata source:

```text
src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java
```

## 6. Route Baseline

Current route source:

```text
src/router/index.ts
```

The Staff Home route is:

```text
/stores/:storeId/staff
```

The local validation default store is:

```text
20000000-0000-0000-0000-000000000983
```

The default store is resolved from:

```text
src/stores/storeContext.ts
VITE_DEFAULT_STORE_ID, falling back to the local validation store id
```

Future redesign must preserve route names and route targets unless a separate route contract approves a
change.

## 7. Operational Boundary

Staff Home may:

- show the current store context;
- show app/loading/unavailable states;
- show approved entry cards;
- route staff to approved pages;
- describe high-level operation paths.

Staff Home must not:

- call Queue Skip or Queue Rejoin APIs directly;
- call Queue Call or Queue Seat mutation APIs directly;
- mutate Reservation, QueueTicket, Seating, Table, Cleaning, No-show, Cancellation, or Turnover state;
- create a Queue Workbench, Queue Display, Table Map, or Reservation Calendar inside the entry page;
- infer authorization from role name alone;
- bypass backend App Gate.

## 8. Narrow-Permission Clarification

The same `StoreStaffHomePage.vue` can render fewer entries when `/api/me/apps` returns fewer
permissions. This is intentional.

Examples:

| Runtime metadata | Expected Staff Home behavior |
| --- | --- |
| `reservation_queue` visible with all 10 entry permissions | Full baseline with all 10 entries visible |
| `reservation_queue` visible with only `queue.view` | Only `排队列表` is visible |
| `reservation_queue` visible with only `queue.view` and `queue.rejoin` | `排队列表` is visible; `queue.rejoin` has no Staff Home entry in V1 |
| `reservation_queue` missing or `entryVisible = false` | No operation list; app unavailable/empty state appears |

Do not label a narrow-permission runtime view as an old Staff Home version. It is a permission-filtered
rendering of the same current page.

## 9. Future UI Improvement Areas

Future redesign may improve the visual structure without changing behavior:

- group entries by workflow: Reservation, Queue, Seating, Cleaning, WalkIn;
- separate operational sections while keeping Staff Home as an entry hub;
- improve mobile card density and scan order;
- add compact status descriptions;
- make permission-filtered empty or partial states clearer;
- avoid confusing narrow-permission views with old page versions;
- keep card text short enough for mobile wrapping;
- preserve all 10 baseline entries unless separately removed by product decision.

Any future visual redesign must keep route and permission behavior stable unless a separate contract changes
them.

## 10. Validation Contract For Future Redesign

Future Staff Home UI work should verify:

- full-permission runtime shows all 10 baseline entries;
- `今日预约` appears when `reservation.today_view` is present;
- missing entries under narrow permissions are treated as expected;
- `queue.rejoin` alone does not create a standalone Staff Home entry;
- Staff Home remains an entry hub and does not perform mutations directly;
- route targets remain correct;
- `/api/me/apps` remains the app/permission visibility source;
- backend App Gate remains authoritative.

## 11. Non-Scope

This contract does not implement:

- Staff Home UI redesign;
- new Staff Home entries;
- route changes;
- permission changes;
- App Gate metadata changes;
- Queue Display;
- Queue Workbench;
- Table Map;
- Reservation Calendar;
- No-show;
- Cancellation;
- Turnover;
- migration;
- production database changes;
- GitHub remote;
- GitHub push.

## 12. Open Questions

- Should a future visual redesign group entries by workflow sections or keep one flat card list?
- Should a future no-permission or partial-permission view show a clearer explanation that entries are hidden
  by permissions?

## 13. Open Conflicts

None for V1. The known confusion came from a narrow-permission runtime, not a duplicate old Staff Home page.

## 14. Next Step Recommendation

Next approved slice may design a visual Staff Home UI redesign using this baseline contract. That slice
should start by validating the full-permission `/staff` page and should preserve all 10 approved entries,
routes, and permission checks unless a separate product decision changes them.

## 15. Boundary Statement

Staff Home UI baseline contract created: Yes
Staff Home UI changed: No
Router changed: No
Permission metadata changed: No
Queue Display implemented: No
Workbench implemented: No
Table Map implemented: No
Reservation Calendar implemented: No
No-show implemented: No
Cancellation implemented: No
Migration changed: No
Production database touched: No
GitHub remote added: No
GitHub push performed: No
