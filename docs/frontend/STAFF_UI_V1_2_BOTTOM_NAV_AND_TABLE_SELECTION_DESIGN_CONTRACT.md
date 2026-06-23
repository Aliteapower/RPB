# Staff UI Design Contract V1.2 - Bottom Navigation And Table Selection

## 1. Purpose

Finalize the next staff mobile UI direction after Staff Home V1.1 by adding:

- shared bottom navigation;
- a real `桌台` tab;
- a lightweight table selection surface backed by actual table resources;
- table picker integration for existing seating forms.

This contract exists because staff must be able to choose a table number without
memorizing or manually typing `tableId` / `tableGroupId`.

This is a design contract only. It does not implement Vue changes, route
changes, App Gate metadata changes, backend APIs, database migrations, runtime
configuration, dependency changes, seed data, production data, GitHub remote, or
push behavior.

## 2. Design Decision

Approved direction:

```text
Staff UI V1.2 - Bottom Navigation And Table Selection
```

The bottom navigation uses four tabs:

| Tab | Label | Required target |
| --- | --- | --- |
| Home | 首页 | existing Staff Home |
| Reservation | 预约 | existing Today Reservations |
| Queue | 排队 | existing Queue List |
| Table | 桌台 | new lightweight Table Status / Selection page |

Unlike V1.1, `桌台` is now in scope because it is required for selecting a table
number during seating.

V1.2 still does not approve a complex drag-and-drop floor plan. The approved
table surface is a lightweight, mobile-first list/grid of real resources.

## 3. Current Problem

Existing seating pages require staff to manually enter table identifiers:

```text
src/pages/WalkInDirectSeatingPage.vue
src/pages/ReservationArrivedDirectSeatingPage.vue
src/pages/SeatingFromCalledQueuePage.vue
```

These pages already use the correct backend seating APIs, but the UI exposes:

```text
tableId
tableGroupId
```

Manual UUID entry is operationally poor. Staff need to select by visible table
code, display name, capacity, and status.

## 3.1 Table Source Of Truth

Table numbers and table groups must come from backend table setup or POS
integration sync.

V1.2 does not create or edit table configuration. It reads existing configured
resources from:

```text
dining_tables
table_groups
table_group_members
```

Resource mapping:

| Staff UI concept | Backend source | Submitted field |
| --- | --- | --- |
| 桌号 / 单桌 | `dining_tables.table_code`, `display_name`, capacity, status | `tableId` |
| 分组 / 桌组 / 包间组合 | `table_groups.group_code`, capacity, status, plus `table_group_members` | `tableGroupId` |

Expected upstream setup:

- Store manager or admin configures areas, table codes, display names,
  capacities, and active table groups in backend/admin table setup.
- Or an approved POS/integration adapter synchronizes table numbers and groups
  into the same RPB table resource model.
- Staff UI reads those resources through a read-only API.
- Seating forms receive either the selected `tableId` or selected
  `tableGroupId` from the picker.
- Selecting a table clears any selected group.
- Selecting a group clears any selected table.

If no table resources are configured for the store, the Staff `桌台` page and
picker must show an empty state such as:

```text
暂无桌台，请先在后台配置桌台。
```

The empty state is informational only and must not create tables from the staff
UI.

Staff UI must not call POS APIs directly. POS-specific table formats must be
normalized behind backend integration/API boundaries before they are exposed to
staff pages.

## 4. Required Backend Capability

There is no existing REST API that lists table resources or their current status.
V1.2 therefore requires a new read-only API contract before implementation.

Recommended endpoint:

```text
GET /api/v1/stores/{storeId}/tables
```

Query parameters:

| Name | Required | Meaning |
| --- | --- | --- |
| `status` | No | Optional status filter such as `available`, `occupied`, `cleaning` |
| `partySize` | No | Optional candidate filter for seating suitability |
| `includeGroups` | No | Whether active table groups are included; default `true` |

Response shape:

```json
{
  "success": true,
  "resources": [
    {
      "resourceType": "dining_table",
      "resourceId": "uuid",
      "code": "A01",
      "displayName": "A01",
      "areaName": "大厅",
      "capacityMin": 1,
      "capacityMax": 4,
      "status": "available",
      "selectable": true,
      "selectionDisabledReason": null
    }
  ]
}
```

For table groups:

```json
{
  "resourceType": "table_group",
  "resourceId": "uuid",
  "code": "VIP-1",
  "displayName": "VIP-1",
  "areaName": "包间",
  "capacityMin": 8,
  "capacityMax": 12,
  "status": "active",
  "selectable": true,
  "selectionDisabledReason": null,
  "memberTableCodes": ["V01", "V02"]
}
```

The API must return DTOs, not persistence entities or domain objects.

## 5. App Gate Boundary

The read-only table list should use the existing `reservation_queue` app
boundary unless a later App Gate decision creates a dedicated table app.

Recommended permission:

```text
table.view
```

This is a new permission metadata change and must be explicitly implemented,
tested, and documented in the implementation slice.

Existing mutation permissions remain unchanged:

```text
walkin.direct_seating.create
reservation.seat
queue.seat
cleaning.start
cleaning.complete
```

The table page only reads resources. It must not mutate table status.

## 6. Bottom Navigation

Create a shared bottom navigation component instead of copying markup into
pages.

Recommended files:

```text
src/components/staff/StaffBottomNav.vue
src/components/staff/staffBottomNavItems.ts
```

Navigation items:

| Label | Route name |
| --- | --- |
| 首页 | `store-staff-home` |
| 预约 | `reservation-today-view` |
| 排队 | `queue-ticket-list` |
| 桌台 | `table-resource-list` |

The component is navigation-only.

It must not:

- call APIs directly;
- infer role permissions;
- perform business mutations;
- store local table state.

## 7. Table Page

Create a lightweight table resource page.

Recommended route:

```text
/stores/:storeId/tables
```

Recommended route name:

```text
table-resource-list
```

Recommended page file:

```text
src/pages/TableResourceListPage.vue
```

Page behavior:

- loads real table resources from `GET /api/v1/stores/{storeId}/tables`;
- shows loading, empty, error, and permission-denied states;
- groups or filters by status;
- renders mobile-friendly table cards;
- shows status colors:
  - available: green;
  - occupied: blue/slate;
  - cleaning: orange;
  - locked/reserved: amber;
  - inactive: gray;
- does not let staff change status directly;
- does not implement drag/drop floor plan.

## 8. Table Picker Integration

Add a reusable table picker component for existing seating forms.

Recommended files:

```text
src/components/staff-table/TableResourcePicker.vue
src/components/staff-table/tableResourceTypes.ts
src/api/tableResourceApi.ts
src/types/tableResource.ts
```

The picker should:

- call the read-only table resource API;
- show dining tables and table groups;
- allow selecting exactly one resource;
- emit either:
  - `tableId`, or
  - `tableGroupId`;
- clear the other field when one resource is selected;
- keep existing form validation that requires exactly one resource.

Initial integration targets:

```text
src/pages/WalkInDirectSeatingPage.vue
src/pages/ReservationArrivedDirectSeatingPage.vue
src/pages/SeatingFromCalledQueuePage.vue
```

These pages already submit seating commands. V1.2 only improves how resource
IDs are selected.

## 9. Target Pages For Bottom Navigation

Add bottom navigation to these primary staff pages:

```text
src/pages/StoreStaffHomePage.vue
src/pages/ReservationTodayViewPage.vue
src/pages/QueueTicketListPage.vue
src/pages/TableResourceListPage.vue
```

The table page replaces the previous idea of using `清台` as the fourth tab.

Do not add bottom navigation to mutation form pages in V1.2 unless the form page
has explicit unsaved-input handling. The three seating forms receive the table
picker but not necessarily the bottom nav in the first implementation slice.

## 10. Business Boundary

V1.2 may:

- add a read-only table resource API;
- add a table resource list page;
- add a shared bottom navigation component;
- add a reusable table picker component;
- connect existing seating forms to the picker;
- add route and App Gate permission metadata needed for the new read-only table
  list.

V1.2 must not:

- create or edit table numbers from staff UI;
- replace backend/admin table setup;
- call POS APIs directly from Staff UI;
- encode POS-specific table formats in Vue pages;
- mutate table status from the table page;
- implement drag-and-drop floor plan;
- implement table merge/split;
- create temporary table groups from the UI;
- implement auto table assignment;
- implement Queue Display or `大屏`;
- implement Reservation Calendar;
- implement no-show or cancellation;
- change seating command semantics;
- bypass existing backend validation;
- use mock table data as runtime business state.

## 11. Database Boundary

No migration is expected for V1.2 because the current schema already contains:

```text
dining_tables
table_groups
table_group_members
seating_resources
cleanings
```

Implementation may add repository queries, projections, DTOs, and read services.

If implementation discovers a missing index or column, stop and create a
separate database review task before adding migrations.

## 12. Required Tests

API tests must cover:

- table list success for a store;
- tenant/store scoping;
- App Gate denial;
- empty list;
- status filtering;
- party-size candidate filtering if implemented;
- table group inclusion;
- no mutation of table status.

Frontend/static tests must cover:

- bottom nav labels include `首页`, `预约`, `排队`, `桌台`;
- bottom nav links to existing route names plus `table-resource-list`;
- table page uses the read-only table API;
- picker emits exactly one of `tableId` or `tableGroupId`;
- seating forms still submit existing command payload shapes;
- no `Queue Display`, `大屏`, `Reservation Calendar`, `ActionSheet`,
  `screen-overlay`, `fake`, `Font Awesome`, `queue.skip`, or `queue.rejoin` is
  introduced by the table slice.

## 13. Runtime Validation

Implementation should verify mobile rendering in a browser for:

```text
/stores/{storeId}/staff
/stores/{storeId}/reservations/today
/stores/{storeId}/queue-tickets
/stores/{storeId}/tables
/stores/{storeId}/walk-ins/direct-seating
/stores/{storeId}/reservations/seating/direct
/stores/{storeId}/queue-tickets/seating/direct
```

Validation must confirm:

- bottom nav is visible on top-level tabs;
- `桌台` tab opens the real table resource page;
- table cards use real API data;
- selecting a table fills `tableId`;
- selecting a group fills `tableGroupId`;
- content does not overlap the bottom nav or picker;
- Chinese labels fit on mobile.

## 14. Acceptance Criteria

V1.2 is acceptable when:

- staff can open `桌台` from the bottom nav;
- staff can choose a real table or table group for seating;
- existing seating APIs still receive the same payload structure;
- table page is read-only;
- all new API behavior is App Gate protected and store-scoped;
- no migration is added unless separately approved;
- frontend build passes;
- focused backend and frontend validation tests pass;
- implementation report documents API, UI, permissions, and boundaries.

## 15. Next Step Recommendation

Proceed to:

```text
Staff UI V1.2 Bottom Navigation And Table Selection Implementation Plan
```

Recommended sequence:

1. Write API contract for `GET /api/v1/stores/{storeId}/tables`.
2. Add backend tests for table list query and App Gate.
3. Implement read-only table resource API.
4. Add frontend table resource API client and types.
5. Add shared bottom nav.
6. Add table page.
7. Add reusable table picker.
8. Integrate picker into seating forms.
9. Validate mobile runtime and write implementation report.

## 16. Boundary Statement

Staff UI V1.2 design finalized: Yes
Bottom navigation approved: Yes
Table tab approved: Yes
Read-only table resource API required: Yes
Table picker approved: Yes
Complex drag-and-drop table map approved: No
Table status mutation from table page approved: No
Auto table assignment approved: No
Router changed: No
Permission metadata changed: No
Backend API changed: No
Migration changed: No
Production database touched: No
GitHub remote added: No
GitHub push performed: No
Maven Wrapper added: No
