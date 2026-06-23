# Staff Home New UI V1.1 Lightweight Workbench Implementation Report

## Scope

Implemented Staff Home New UI V1.1 Lightweight Workbench as a mobile-first
staff home entry screen.

The implementation keeps behavior scoped to the existing Staff Home surface:

- Staff Home page composition
- Staff Home-only presentational components
- Staff Home App Gate validation test
- This implementation report

## Changed Files

- `src/pages/StoreStaffHomePage.vue`
- `src/components/staff-home/StaffHomeActionGroup.vue`
- `src/components/staff-home/StaffHomeTopBar.vue`
- `src/components/staff-home/StaffHomeWorkflowStrip.vue`
- `src/components/staff-home/staffHomeActions.ts`
- `src/components/staff-home/useCurrentClock.ts`
- `src/test/java/com/rpb/reservation/appgate/ui/StoreStaffHomePageAppGateRuntimeValidationTest.java`
- `docs/frontend/STAFF_HOME_NEW_UI_V1_1_LIGHTWEIGHT_WORKBENCH_IMPLEMENTATION_REPORT.md`

## Design Contract

Implemented against:

- `docs/frontend/STAFF_HOME_NEW_UI_V1_1_LIGHTWEIGHT_WORKBENCH_DESIGN_CONTRACT.md`

V1.1 follows the approved lightweight workbench direction:

- Top bar with staff brand, current time, current store, and app status
- Static workflow strip: `散客入座 -> 占用 -> 清台 -> 可用`
- Permission-driven operation groups
- Mobile-first touch targets
- No fabricated live status metrics
- No bottom navigation
- No table map
- No queue display or queue workbench
- No reservation calendar

## Implementation Summary

`StoreStaffHomePage.vue` now handles only:

- Loading `me/apps` through the existing `fetchMeApps` API
- Resolving the current store through the existing store context
- Computing existing App Gate permission predicates
- Building Staff Home action arrays
- Rendering Staff Home-only components

The page no longer carries all UI structure inline. The visual surface is split
into Staff Home-scoped components:

- `StaffHomeTopBar.vue`: brand, time, store, and app status pills
- `StaffHomeWorkflowStrip.vue`: lightweight staff flow reminder
- `StaffHomeActionGroup.vue`: reusable permission-driven action grid
- `staffHomeActions.ts`: action item type contract
- `useCurrentClock.ts`: current time display composable

## Entry Groups

The full-permission Staff Home baseline remains 10 entries, regrouped as:

- 接待
  - 散客直接入座
  - 预约到店
- 预约管理
  - 创建预约
  - 今日预约
  - 预约排队
  - 预约入座
- 排队管理
  - 排队列表
  - 排队叫号
  - 排队入座
- 桌台流转
  - 清台处理

Empty groups are hidden by computed group guards. No route target or permission
predicate was changed.

## Permission Behavior

The page still depends on the existing `reservation_queue` App Gate entry and
the existing permission strings:

- `walkin.direct_seating.create`
- `reservation.check_in`
- `reservation.create`
- `reservation.today_view`
- `reservation.queue`
- `reservation.seat`
- `queue.view`
- `queue.call`
- `queue.seat`
- `cleaning.start`
- `cleaning.complete`

No new permission metadata was added.

## Test Updates

Updated `StoreStaffHomePageAppGateRuntimeValidationTest` to validate:

- Staff Home still gates operations behind `me/apps`
- V1.1 component boundaries are present
- Full-permission 10-entry baseline is preserved
- The new grouped order is stable
- Prototype-only and unapproved workbench artifacts are absent

## Validation

- `mvn -q "-Dtest=StoreStaffHomePageAppGateRuntimeValidationTest" test`
  - Pass
- `mvn -q "-Dtest=*StaffHome*Test,*StoreStaffHome*Test" test`
  - Pass
- `cmd /c npm run build`
  - Pass

## Runtime Check

Started a temporary local Vite server for Staff Home validation:

- Requested port: `5176`
- Actual Vite URL: `http://127.0.0.1:5177/`
- Checked route:
  `http://127.0.0.1:5177/stores/20000000-0000-0000-0000-000000000983/staff`

Browser validation result:

- `#app` mounted successfully.
- Top bar rendered `食刻 · 管理`.
- Current time, current store, and app status rendered in the top bar.
- Workflow strip rendered `散客入座 -> 占用 -> 清台 -> 可用`.
- Permission-driven operation groups rendered.
- Mobile viewport check confirmed the app status pill remains visible.

## Boundary Result

No forbidden implementation area was changed:

- No router changed
- No permission metadata changed
- No backend API changed
- No migration changed
- No runtime configuration changed
- No dependency file changed
- No Maven Wrapper added
- No GitHub remote added
- No push performed

## Next Step Recommendation

Run a visual check with a real full-permission local staff session, then decide
whether to commit the scoped V1.1 files.

## Final Boundary Statement

Staff Home New UI V1.1 implemented: Yes
Staff Home Lightweight Workbench UI implemented: Yes
Full-permission 10-entry baseline preserved: Yes
Staff Home UI changed: Yes
Router changed: No
Permission metadata changed: No
Queue Display implemented: No
Queue Workbench implemented: No
Table Map implemented: No
Reservation Calendar implemented: No
No-show implemented: No
Cancellation implemented: No
Migration changed: No
Production database touched: No
GitHub remote added: No
GitHub push performed: No
Maven Wrapper added: No
