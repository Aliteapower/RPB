# Staff UI V1.2 Bottom Navigation And Table Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a mobile bottom navigation with a real `桌台` tab and let staff select actual table numbers or groups instead of manually typing table IDs.

**Architecture:** Implement this as a read-only table resource vertical slice plus shared frontend components. Backend/admin table setup or approved POS integration adapters own upstream table numbers and table groups; this slice only reads normalized RPB table resources. Frontend renders a table page and reusable picker that feeds existing seating forms without changing seating command semantics.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/JPA, App Gate, Vue 3, TypeScript, Vue Router, Vite.

---

## File Structure

Create or modify these files only when executing the implementation:

Backend API contract and report:

- Create: `docs/api/TABLE_RESOURCE_LIST_API_CONTRACT.md`
- Create: `docs/frontend/STAFF_UI_V1_2_BOTTOM_NAV_AND_TABLE_SELECTION_IMPLEMENTATION_REPORT.md`

Backend implementation:

- Create: `src/main/java/com/rpb/reservation/table/api/TableResourceListController.java`
- Create: `src/main/java/com/rpb/reservation/table/api/TableResourceListResponse.java`
- Create: `src/main/java/com/rpb/reservation/table/api/TableResourceItemResponse.java`
- Create: `src/main/java/com/rpb/reservation/table/api/TableResourceListApiErrorResponse.java`
- Create: `src/main/java/com/rpb/reservation/table/api/TableResourceListApiErrorMapper.java`
- Create: `src/main/java/com/rpb/reservation/table/application/TableResourceListQuery.java`
- Create: `src/main/java/com/rpb/reservation/table/application/TableResourceListResult.java`
- Create: `src/main/java/com/rpb/reservation/table/application/TableResourceItem.java`
- Create: `src/main/java/com/rpb/reservation/table/application/service/TableResourceListApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/port/out/DiningTableRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/table/application/port/out/TableGroupRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/adapter/DiningTablePersistenceAdapter.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/adapter/TableGroupPersistenceAdapter.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/repository/DiningTableJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/table/persistence/repository/TableGroupJpaRepository.java`
- Modify: `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`

Backend tests:

- Create: `src/test/java/com/rpb/reservation/table/api/TableResourceListControllerTest.java`
- Create: `src/test/java/com/rpb/reservation/table/application/TableResourceListApplicationServiceTest.java`
- Create: `src/test/java/com/rpb/reservation/table/persistence/TableResourceListPersistenceAdapterTest.java`

Frontend implementation:

- Create: `src/api/tableResourceApi.ts`
- Create: `src/types/tableResource.ts`
- Create: `src/components/staff/StaffBottomNav.vue`
- Create: `src/components/staff/staffBottomNavItems.ts`
- Create: `src/components/staff-table/TableResourcePicker.vue`
- Create: `src/pages/TableResourceListPage.vue`
- Modify: `src/router/index.ts`
- Modify: `src/pages/StoreStaffHomePage.vue`
- Modify: `src/pages/ReservationTodayViewPage.vue`
- Modify: `src/pages/QueueTicketListPage.vue`
- Modify: `src/pages/WalkInDirectSeatingPage.vue`
- Modify: `src/pages/ReservationArrivedDirectSeatingPage.vue`
- Modify: `src/pages/SeatingFromCalledQueuePage.vue`

Frontend/static tests:

- Create: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StoreStaffHomePageAppGateRuntimeValidationTest.java`

## Task 1: API Contract

**Files:**

- Create: `docs/api/TABLE_RESOURCE_LIST_API_CONTRACT.md`

- [ ] **Step 1: Write the API contract**

Create the contract with this endpoint:

```text
GET /api/v1/stores/{storeId}/tables
```

Document query parameters:

```text
status: optional table status filter
partySize: optional positive integer
includeGroups: optional boolean, default true
```

Document that table resources come from backend/admin table setup or POS sync,
but Staff UI reads only normalized RPB resources:

```text
桌号 / 单桌 -> dining_tables -> tableId
分组 / 桌组 -> table_groups + table_group_members -> tableGroupId
POS-specific table payloads -> integration adapter -> normalized RPB table resource
```

Document response DTO:

```json
{
  "success": true,
  "resources": [
    {
      "resourceType": "dining_table",
      "resourceId": "70000000-0000-0000-0000-000000000001",
      "code": "A01",
      "displayName": "A01",
      "areaName": "大厅",
      "capacityMin": 1,
      "capacityMax": 4,
      "status": "available",
      "selectable": true,
      "selectionDisabledReason": null,
      "memberTableCodes": []
    }
  ]
}
```

Document App Gate:

```text
appKey: reservation_queue
permission: table.view
```

Document non-scope:

```text
No table number or group creation.
No replacement for backend/admin table setup.
No direct POS call from Staff UI or GET /tables.
No POS-specific table payload in Vue components.
No table mutation.
No drag-and-drop map.
No temporary table group creation.
No auto assignment.
No migration.
```

- [ ] **Step 2: Review the contract against API and database rules**

Check:

```text
Path uses /api/v1.
Response DTO does not expose persistence entities.
Tenant/store scope is explicit.
No idempotency is needed because endpoint is read-only.
No migration is required.
```

## Task 2: Backend Table Resource Query Tests

**Files:**

- Create: `src/test/java/com/rpb/reservation/table/application/TableResourceListApplicationServiceTest.java`
- Create: `src/test/java/com/rpb/reservation/table/api/TableResourceListControllerTest.java`
- Create: `src/test/java/com/rpb/reservation/table/persistence/TableResourceListPersistenceAdapterTest.java`

- [ ] **Step 1: Add application service tests**

Cover:

```text
lists dining tables by store scope
includes active table groups when includeGroups=true
returns empty list when no table numbers or groups are configured
filters by status=available
filters by partySize when present
marks occupied/cleaning resources selectable=false
does not mutate table or group status
```

- [ ] **Step 2: Add controller tests**

Cover:

```text
GET /api/v1/stores/{storeId}/tables returns resources
App Gate permission table.view is required
permission denial returns App Gate denial envelope
invalid partySize returns 400
```

- [ ] **Step 3: Add persistence adapter tests**

Cover tenant/store isolation:

```text
same table code in another store is not returned
deleted tables are not returned
inactive tables may be returned only when status filter allows them
active groups include member table codes
```

- [ ] **Step 4: Run failing tests**

Run:

```powershell
mvn -q "-Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest,TableResourceListPersistenceAdapterTest" test
```

Expected before implementation:

```text
FAIL because classes and endpoint do not exist yet.
```

## Task 3: Backend Read-Only Table API

**Files:**

- Create backend implementation files listed in File Structure.
- Modify App Gate permission metadata.
- Modify table repository ports and adapters.

- [ ] **Step 1: Add response DTOs**

Use explicit DTOs:

```java
public record TableResourceListResponse(
    boolean success,
    List<TableResourceItemResponse> resources
) {
}
```

```java
public record TableResourceItemResponse(
    String resourceType,
    UUID resourceId,
    String code,
    String displayName,
    String areaName,
    int capacityMin,
    int capacityMax,
    String status,
    boolean selectable,
    String selectionDisabledReason,
    List<String> memberTableCodes
) {
}
```

- [ ] **Step 2: Add application query model**

Use:

```java
public record TableResourceListQuery(
    StoreScope scope,
    String status,
    Integer partySize,
    boolean includeGroups
) {
}
```

- [ ] **Step 3: Add repository read methods**

Extend the ports with read-only methods:

```java
List<DiningTable> findVisibleResources(StoreScope scope, String status, PartySize partySize);
List<TableGroup> findVisibleGroups(StoreScope scope, String status, PartySize partySize);
```

Keep existing seating candidate methods unchanged.

- [ ] **Step 4: Implement service and controller**

Controller path:

```java
@RequestMapping("/api/v1/stores/{storeId}/tables")
```

Guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "table.view")
```

- [ ] **Step 5: Run backend tests**

Run:

```powershell
mvn -q "-Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest,TableResourceListPersistenceAdapterTest" test
```

Expected:

```text
PASS
```

## Task 4: Frontend Types And API Client

**Files:**

- Create: `src/types/tableResource.ts`
- Create: `src/api/tableResourceApi.ts`

- [ ] **Step 1: Add TypeScript types**

Define:

```ts
export type TableResourceType = 'dining_table' | 'table_group'

export type TableResourceItem = {
  resourceType: TableResourceType
  resourceId: string
  code: string
  displayName: string
  areaName: string | null
  capacityMin: number
  capacityMax: number
  status: string
  selectable: boolean
  selectionDisabledReason: string | null
  memberTableCodes: string[]
}

export type TableResourceListResponse = {
  success: true
  resources: TableResourceItem[]
}
```

- [ ] **Step 2: Add API client**

Implement:

```ts
export async function fetchTableResources(
  storeId: string,
  options: {
    status?: string
    partySize?: number
    includeGroups?: boolean
  } = {}
): Promise<TableResourceListResponse>
```

Build URL:

```text
/api/v1/stores/${storeId}/tables
```

- [ ] **Step 3: Run frontend build**

Run:

```powershell
cmd /c npm run build
```

Expected:

```text
PASS
```

## Task 5: Shared Bottom Navigation

**Files:**

- Create: `src/components/staff/StaffBottomNav.vue`
- Create: `src/components/staff/staffBottomNavItems.ts`
- Modify: `src/router/index.ts`
- Create: `src/pages/TableResourceListPage.vue`

- [ ] **Step 1: Add route**

Add:

```ts
{
  path: '/stores/:storeId/tables',
  name: 'table-resource-list',
  component: TableResourceListPage
}
```

- [ ] **Step 2: Add nav item model**

Create four items:

```ts
export const staffBottomNavItems = [
  { id: 'home', label: '首页', symbol: '⌂', routeName: 'store-staff-home' },
  { id: 'reservation', label: '预约', symbol: '日', routeName: 'reservation-today-view' },
  { id: 'queue', label: '排队', symbol: '列', routeName: 'queue-ticket-list' },
  { id: 'table', label: '桌台', symbol: '▦', routeName: 'table-resource-list' }
] as const
```

- [ ] **Step 3: Add component**

`StaffBottomNav.vue` props:

```ts
defineProps<{
  activeTab: 'home' | 'reservation' | 'queue' | 'table'
  storeId: string
}>()
```

Render `RouterLink` for each item and apply active orange state.

## Task 6: Table Resource List Page

**Files:**

- Create: `src/pages/TableResourceListPage.vue`

- [ ] **Step 1: Build page states**

The page must include:

```text
loading
error
empty
resource list
status filter chips
```

- [ ] **Step 2: Render real resources**

Each card shows:

```text
code
displayName
areaName
capacityMin-capacityMax
status
memberTableCodes for table groups
```

Empty state:

```text
暂无桌台，请先在后台配置桌台。
```

- [ ] **Step 3: Add bottom nav**

Use:

```vue
<StaffBottomNav active-tab="table" :store-id="storeId" />
```

- [ ] **Step 4: Confirm no mutations**

The table page must not call seating, cleaning, queue, or reservation mutation APIs.

## Task 7: Reusable Table Picker

**Files:**

- Create: `src/components/staff-table/TableResourcePicker.vue`
- Create: `src/components/staff-table/tableResourceTypes.ts`

- [ ] **Step 1: Add picker props and emits**

Props:

```ts
defineProps<{
  storeId: string
  selectedTableId?: string
  selectedTableGroupId?: string
  partySize?: number
}>()
```

Emits:

```ts
defineEmits<{
  'select-table': [tableId: string]
  'select-table-group': [tableGroupId: string]
  'clear-selection': []
}>()
```

- [ ] **Step 2: Add selection behavior**

Rules:

```text
Selecting a dining table emits select-table and clears tableGroupId in parent.
Selecting a table group emits select-table-group and clears tableId in parent.
Disabled resources cannot be selected.
Empty picker shows 暂无桌台，请先在后台配置桌台。
```

- [ ] **Step 3: Add loading/error/empty states**

Use the same API client as the table page.

## Task 8: Integrate Picker Into Seating Forms

**Files:**

- Modify: `src/pages/WalkInDirectSeatingPage.vue`
- Modify: `src/pages/ReservationArrivedDirectSeatingPage.vue`
- Modify: `src/pages/SeatingFromCalledQueuePage.vue`

- [ ] **Step 1: Replace manual-first resource selection with picker**

Keep the existing `form.tableId` and `form.tableGroupId` fields.

Add handlers:

```ts
function selectTable(tableId: string): void {
  form.tableId = tableId
  form.tableGroupId = ''
}

function selectTableGroup(tableGroupId: string): void {
  form.tableGroupId = tableGroupId
  form.tableId = ''
}
```

- [ ] **Step 2: Preserve manual fallback**

Keep a collapsed manual ID section for support/debug situations:

```text
手动填写资源 ID
```

- [ ] **Step 3: Keep payload unchanged**

Verify the request still sends:

```ts
{
  tableId: optionalValue(form.tableId),
  tableGroupId: optionalValue(form.tableGroupId)
}
```

## Task 9: Static UI Validation Tests

**Files:**

- Create: `src/test/java/com/rpb/reservation/appgate/ui/StaffUiV12TableSelectionValidationTest.java`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StoreStaffHomePageAppGateRuntimeValidationTest.java`

- [ ] **Step 1: Add static validation**

Assert:

```text
StaffBottomNav.vue exists
labels 首页/预约/排队/桌台 exist
route names store-staff-home/reservation-today-view/queue-ticket-list/table-resource-list exist
TableResourceListPage.vue calls fetchTableResources
TableResourcePicker.vue emits select-table and select-table-group
seating pages import TableResourcePicker
```

- [ ] **Step 2: Add forbidden artifact checks**

Assert absence of:

```text
Queue Display
大屏
Reservation Calendar
ActionSheet
screen-overlay
fake
Font Awesome
queue.skip
queue.rejoin
drag
drop
```

- [ ] **Step 3: Run focused validation**

Run:

```powershell
mvn -q "-Dtest=StaffUiV12TableSelectionValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest" test
```

Expected:

```text
PASS
```

## Task 10: Runtime And Build Validation

**Files:**

- Create: `docs/frontend/STAFF_UI_V1_2_BOTTOM_NAV_AND_TABLE_SELECTION_IMPLEMENTATION_REPORT.md`

- [ ] **Step 1: Run frontend build**

Run:

```powershell
cmd /c npm run build
```

Expected:

```text
PASS
```

- [ ] **Step 2: Run backend focused tests**

Run:

```powershell
mvn -q "-Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest,TableResourceListPersistenceAdapterTest,StaffUiV12TableSelectionValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest" test
```

Expected:

```text
PASS
```

- [ ] **Step 3: Browser-check mobile pages**

Check these routes:

```text
/stores/{storeId}/staff
/stores/{storeId}/reservations/today
/stores/{storeId}/queue-tickets
/stores/{storeId}/tables
/stores/{storeId}/walk-ins/direct-seating
/stores/{storeId}/reservations/seating/direct
/stores/{storeId}/queue-tickets/seating/direct
```

Confirm:

```text
bottom nav is visible on top-level tabs
table page loads real API data
picker can select dining_table and table_group
content does not overlap bottom nav
Chinese labels fit
```

- [ ] **Step 4: Write implementation report**

Include:

```text
files changed
API contract implemented
permission metadata changed
router changed
table page scope
picker integration
tests run
runtime browser results
boundary statement
```

## Boundary Statement For Implementation

The implementation slice is allowed to change:

```text
Router: Yes, to add /stores/:storeId/tables
Permission metadata: Yes, to add table.view
Backend API: Yes, to add read-only GET /tables
Frontend pages/components: Yes
Migration: No
Production database: No
Queue Display: No
Complex drag-and-drop Table Map: No
Table status mutation from table page: No
Auto table assignment: No
No-show/Cancellation: No
```
