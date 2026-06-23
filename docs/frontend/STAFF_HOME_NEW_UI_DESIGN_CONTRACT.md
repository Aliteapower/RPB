# Staff Home New UI Design Contract V1

## 1. Purpose

Define the approved visual and information-architecture direction for a future Staff Home redesign while
preserving the existing full-permission Staff Home baseline.

Target page:

```text
src/pages/StoreStaffHomePage.vue
```

Target route:

```text
/stores/{storeId}/staff
```

This document is contract only. It does not implement Staff Home UI, Vue Router changes, App Gate metadata
changes, backend APIs, database migrations, runtime configuration, dependency changes, seed data, or
production data.

## 2. Reference Materials Read

- `docs/frontend/STAFF_HOME_UI_BASELINE_CONTRACT.md`
- `src/pages/StoreStaffHomePage.vue`
- `src/router/index.ts`
- `src/api/meAppsApi.ts`
- `src/types/meApps.ts`
- `src/stores/storeContext.ts`
- `docs/frontend/QUEUE_LIST_UI_CONTRACT.md`
- `docs/frontend/QUEUE_SKIP_UI_CONTRACT.md`
- `docs/frontend/QUEUE_REJOIN_UI_CONTRACT.md`
- `docs/frontend/QUEUE_REJOIN_UI_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_SKIP_API_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_REJOIN_API_IMPLEMENTATION_REPORT.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/governance/DATA_STANDARD.md`
- Standalone HTML prototype from the task attachment

## 3. Baseline Decision

Future Staff Home New UI implementation must preserve the already approved full-permission Staff Home
baseline.

Full-permission baseline entries:

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

A narrow-permission runtime view is not the Staff Home baseline. Missing entries under narrow permissions
are expected and not a regression. Future redesign must preserve all 10 approved entries under full
permission.

## 4. Prototype Usage Decision

The standalone HTML prototype may be used only as visual and information-architecture inspiration.

Allowed inspiration:

- mobile-first restaurant staff workspace style;
- light gray page background;
- white rounded cards;
- orange primary action direction;
- compact operation cards;
- workflow or status banner inspiration;
- entry grouping inspiration;
- large tap target inspiration;
- bottom-sheet and action-sheet visual inspiration for future table-related slices.

Not allowed:

- direct code copy;
- standalone HTML structure migration;
- inline CSS wholesale copy;
- DOM event handlers;
- fake queue data;
- fake table data;
- local JavaScript business state;
- Queue Display implementation;
- table grid implementation;
- table status mutation;
- ActionSheet behavior implementation;
- walk-in fake flow;
- cleaning fake flow;
- direct seating fake flow;
- new backend API;
- new route;
- permission bypass.

## 5. Proposed Information Architecture

The future Staff Home New UI may group the existing entries as follows:

```text
接待
- 散客直接入座
- 创建预约

预约
- 今日预约
- 预约到店
- 预约排队
- 预约入座

排队
- 排队列表
- 排队叫号
- 排队入座

运营
- 清台处理
```

This grouping is presentation only. It must not change routes, permissions, App Gate metadata, backend
behavior, entry availability, or business workflow.

## 6. New UI Direction

The future redesign should make Staff Home feel like a mobile-first restaurant staff hub:

- max-width mobile container;
- light gray page background;
- white rounded cards;
- warm orange primary action color;
- dark slate primary text;
- gray secondary text;
- compact section cards;
- clear section titles;
- large touch targets;
- subtle border or shadow;
- workflow/status banner;
- permission-driven entry visibility.

The redesign should stay operational and compact. It should not become a marketing-style landing page,
analytics dashboard, queue workbench, table map, reservation calendar, or mutation console.

## 7. Suggested Visual Tokens

These are conceptual visual tokens for a future implementation. They are not source-style changes in this
contract slice.

| Token | Direction |
| --- | --- |
| Primary color | Warm orange |
| Background | Light gray |
| Card | White |
| Radius | Large rounded cards |
| Text primary | Dark slate |
| Text secondary | Gray |
| Button/action | Rounded card or pill style |
| Spacing | Compact mobile spacing |
| Safe area | Mobile friendly |
| Shadow | Subtle elevation or border |

## 8. App Gate And Visibility Source

Staff Home currently loads app visibility from:

```text
GET /api/me/apps?storeId={storeId}
```

Current frontend sources:

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

Each visible Staff Home entry is then shown only when the matching permission exists in the returned
`permissions` list. Frontend checks are display hints only. Backend APIs and App Gate remain authoritative
for mutations and data access.

The backend registry currently also contains `queue.skip` and `queue.rejoin` for the `reservation_queue`
entry permission set. Those permissions do not create standalone Staff Home entries in this contract.
Queue Skip and Queue Rejoin remain Queue Ticket List actions governed by their separate contracts.

## 9. Entry Contract

| Entry label | Current target route | Required app_key | Required permission | Current implementation source | Visible under full permission | Hidden under narrow permission |
| --- | --- | --- | --- | --- | --- | --- |
| 散客直接入座 | `/stores/:storeId/walk-ins/direct-seating` | `reservation_queue` | `walkin.direct_seating.create` | `canSeatWalkInDirectly`, `walkInRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 清台处理 | `/stores/:storeId/cleaning` | `reservation_queue` | `cleaning.start` and `cleaning.complete` | `canHandleCleaning`, `cleaningRoute` in `StoreStaffHomePage.vue` | Yes | Expected if either permission missing |
| 创建预约 | `/stores/:storeId/reservations/create` | `reservation_queue` | `reservation.create` | `canCreateReservation`, `reservationRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 今日预约 | `/stores/:storeId/reservations/today` | `reservation_queue` | `reservation.today_view` | `canViewTodayReservations`, `reservationTodayViewRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 预约到店 | `/stores/:storeId/reservations/check-in` | `reservation_queue` | `reservation.check_in` | `canCheckInReservation`, `reservationCheckInRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 预约排队 | `/stores/:storeId/reservations/queue` | `reservation_queue` | `reservation.queue` | `canQueueArrivedReservation`, `reservationArrivedToQueueRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 排队列表 | `/stores/:storeId/queue-tickets` | `reservation_queue` | `queue.view` | `canViewQueueTickets`, `queueTicketListRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 排队叫号 | `/stores/:storeId/queue-tickets/call` | `reservation_queue` | `queue.call` | `canCallQueueTicket`, `queueCallRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 排队入座 | `/stores/:storeId/queue-tickets/seating/direct` | `reservation_queue` | `queue.seat` | `canSeatCalledQueueTicket`, `seatingFromCalledQueueRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |
| 预约入座 | `/stores/:storeId/reservations/seating/direct` | `reservation_queue` | `reservation.seat` | `canSeatArrivedReservation`, `reservationArrivedDirectSeatingRoute` in `StoreStaffHomePage.vue` | Yes | Expected if permission missing |

Do not invent new permissions. If a future entry requires a permission not present in current metadata, that
future slice must update the API or App Gate contract first.

## 10. Staff Home Behavior Rules

Staff Home is an entry hub, not a Workbench.

Staff Home may:

- show current store context;
- show app loading, unavailable, empty, or partial-permission states;
- show approved entry cards;
- group approved entries for scanning;
- route staff to approved pages and flows;
- show a high-level workflow/status banner.

Staff Home must not:

- directly perform queue skip, rejoin, call, seat, no-show, cancellation, cleaning, table, reservation, or
  turnover mutations;
- directly create or mutate table status;
- create Queue Display inside the entry page;
- create a Queue Workbench inside the entry page;
- create a Table Map inside the entry page;
- create a Reservation Calendar inside the entry page;
- infer authorization from role name alone;
- bypass backend App Gate;
- silently add, remove, or rename business routes.

Entry visibility must remain App Gate and permission driven.

## 11. Business Boundary

The redesign must preserve the project business object separations:

- Reservation is advance capacity intent, not a QueueTicket.
- WalkIn is an arrival scenario and can be seated directly when a suitable table is available.
- CheckIn confirms arrival and does not create occupancy.
- QueueTicket represents waiting after arrival when needed.
- Seating creates table occupancy.
- Cleaning releases occupied resources toward availability.
- Turnover is a result or metric, not a Staff Home action.

The Staff Home page must not hold state machine logic for Reservation, QueueTicket, Table, Seating,
Cleaning, or Turnover. Those state transitions belong to their approved pages, APIs, services, and backend
state machines.

## 12. Prototype Non-Scope Boundary

The following prototype features are future/non-scope and must not be implemented in Staff Home New UI
Design Contract V1 or Staff Home UI Implementation V1:

- Queue Display / 大屏;
- table grid / 桌台网格;
- table status mutation;
- table action sheet;
- walk-in fake data flow;
- queue fake data flow;
- direct seating mutation;
- cleaning mutation;
- reservation calendar;
- workbench mutation;
- local DOM-only state.

These may inspire future contracts, but each requires its own approved scope.

## 13. Future Implementation Scope

Future Staff Home New UI implementation should be limited to:

```text
src/pages/StoreStaffHomePage.vue
existing Staff Home validation tests if present
optional local style adjustments inside the page if project pattern allows
```

Do not create:

- new Staff Home route;
- new backend API;
- new App Gate permission;
- new migration;
- new Queue Display page;
- new Table Map page;
- new Workbench page;
- new Reservation Calendar page.

## 14. Future Tests To Define

Future implementation should verify:

- full-permission Staff Home shows all 10 baseline entries;
- narrow-permission Staff Home hides unauthorized entries without being treated as old UI;
- entry labels remain stable;
- entry routes remain stable;
- permission visibility follows existing App Gate metadata;
- new grouped layout does not remove approved entries;
- `queue.skip` does not create a Staff Home entry;
- `queue.rejoin` does not create a Staff Home entry;
- no direct queue skip, rejoin, call, seat, table, cleaning, no-show, cancellation, or reservation mutation is introduced on Staff Home;
- no Queue Display, Table Map, Workbench, or Reservation Calendar artifacts are introduced;
- mobile layout builds;
- `npm run build` passes.

## 15. TDD Review Notes

This is a documentation-only contract slice. No runtime code changed and no executable test was added by
this document.

Required future tests are defined in Section 14. A future implementation slice should add or update tests
before or alongside the UI change and should treat missing full-permission baseline coverage as a blocker.

## 16. UI Review Notes

The visual direction is approved only at the contract level. Future implementation should be reviewed for:

- mobile readability;
- thumb-friendly tap targets;
- compact operational density;
- clear grouping;
- loading, unavailable, empty, and partial-permission states;
- no overlap or clipped Chinese text;
- no mutation controls on Staff Home;
- no one-note palette dominated by a single hue beyond the controlled warm-orange action emphasis.

## 17. Release Note Draft

Version / Date:

```text
Staff Home New UI Design Contract V1 / 2026-06-23
```

New:

- Added a documentation contract for the future Staff Home New UI direction.
- Preserved the 10-entry full-permission Staff Home baseline.
- Defined grouped presentation direction, conceptual visual tokens, prototype usage rules, future scope, and
  future tests.

Changed:

- No runtime behavior changed.

Migration:

- No migration.

Permission:

- No permission or App Gate metadata change.

Risk:

- Low documentation-only risk. Main future risk is accidentally converting Staff Home into a mutation
  workbench or dropping entries under full permission.

Rollback:

- Remove this contract document if the design direction is superseded by a later approved contract.

## 18. Open Questions

- Should future implementation show a clearer partial-permission explanation when some entries are hidden by
  App Gate metadata?
- Should the future grouped layout order exactly match `接待`, `预约`, `排队`, `运营`, or should runtime
  permission visibility allow empty groups to be hidden?

## 19. Open Conflicts

None for this contract. The standalone prototype contains Queue Display, table grid, local queue state,
local table state, and mutation-like behavior that are explicitly non-scope for Staff Home.

## 20. Next Step Recommendation

Next approved slice:

```text
Staff Home New UI Implementation V1
```

Recommended future implementation boundary:

- Update `src/pages/StoreStaffHomePage.vue` only.
- Preserve the current route and all target route names.
- Preserve `/api/me/apps` as the visibility source.
- Preserve all 10 full-permission baseline entries.
- Add or update focused Staff Home validation tests.
- Run `npm run build`.

## 21. Boundary Statement

Staff Home New UI design contract created: Yes
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
