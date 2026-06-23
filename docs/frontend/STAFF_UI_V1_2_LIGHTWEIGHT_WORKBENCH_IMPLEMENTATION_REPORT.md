# Staff UI V1.2 Lightweight Workbench Implementation Report

## Scope

Implemented Staff UI V1.2 bottom navigation and lightweight table-resource selection.

This implementation adds:

- Staff bottom navigation: 首页 / 预约 / 排队 / 桌台
- Read-only table-resource list page
- Shared table-resource picker for direct seating forms
- Reservation today lightweight workbench UI with quick actions, month calendar, and create-reservation dialog
- Read-only table-resource API: `GET /api/v1/stores/{storeId}/tables`
- App Gate permission: `table.view`
- Table number and table group support

## POS Integration Boundary

Different POS systems may provide table setup APIs.

RPB still owns the staff-facing contract:

- POS or admin setup may populate/sync table numbers and table groups into RPB backend tables.
- Staff UI reads only normalized RPB table resources.
- Staff UI does not call POS APIs directly.
- Staff UI does not expose POS-specific payloads.
- POS adapter work remains a separate integration task.

## Data Source

The table-resource API reads configured resources from existing RPB table domain storage:

- `dining_tables`
- `table_groups`
- `table_group_members`

No migration was added.

## UI Changes

Added:

- `src/components/staff/StaffBottomNav.vue`
- `src/components/staff/staffBottomNavItems.ts`
- `src/components/reservation-workbench/CreateReservationDialog.vue`
- `src/components/reservation-workbench/ReservationMonthCalendar.vue`
- `src/components/reservation-workbench/ReservationQuickActionPanel.vue`
- `src/components/staff/StaffTimeWheelPicker.vue`
- `src/components/staff-table/TableResourcePicker.vue`
- `src/pages/TableResourceListPage.vue`
- `src/api/tableResourceApi.ts`
- `src/types/tableResource.ts`
- `src/styles/staffWorkbench.css`

Updated:

- `src/pages/StoreStaffHomePage.vue`
- `src/pages/ReservationTodayViewPage.vue`
- `src/pages/QueueTicketListPage.vue`
- `src/pages/WalkInDirectSeatingPage.vue`
- `src/pages/ReservationArrivedDirectSeatingPage.vue`
- `src/pages/SeatingFromCalledQueuePage.vue`
- `src/router/index.ts`

Visual follow-up:

- Aligned reservation and queue pages to the same 520px lightweight workbench width as the bottom navigation.
- Extracted the shared staff workbench shell background, bottom safe area, width, and desktop side borders to `src/styles/staffWorkbench.css`.
- Added bottom scroll safe space to reservation and queue pages so fixed bottom navigation does not cover the tail of long lists.
- Reworked the reservation today page as a lightweight reservation workbench with four real navigation entries and a month calendar.
- The reservation calendar only changes the existing `GET /api/v1/stores/{storeId}/reservations/today` query date; it does not create, cancel, or mutate reservations.
- The create-reservation entry now opens a mobile-first modal and uses the existing `createReservation` API.
- The create-reservation modal keeps table number optional and does not send table assignment data in the request.
- The create-reservation modal uses the reusable 24-hour two-column `StaffTimeWheelPicker` for time input.
- Reworked the reservation check-in page to the same lightweight reservation workbench visual system, including bottom navigation and mobile-first form controls.
- Reworked the reservation today list into modular `ReservationTodayListPanel` and `ReservationTodayListItem` components.
- The reservation today list now uses a compact staff-facing row layout with phone and party-size filters, status chips, and direct links into the existing reservation check-in and arrived direct-seating workflows.
- Removed manual reservation ID copy from the staff UI; reservation IDs remain internal route query data only.
- Cancellation is shown only as a disabled boundary action because no cancellation API contract, App Gate permission, idempotent command, or audit flow exists yet.

## Backend Changes

Added:

- `TableResourceListController`
- `TableResourceListApplicationService`
- Table-resource application DTOs and API DTOs
- Read methods on existing table repository ports and persistence adapters

Updated:

- `AppGateRequiredPermission.TABLE_VIEW`
- Reservation queue entry permission set now includes `table.view`

## Verification

Passed:

- `mvn -q "-Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest,TableResourceListPersistenceAdapterTest,StaffUiV12TableSelectionValidationTest" test`
- `mvn -q "-Dtest=AppGateRequiredPermissionTest,QueueCallUiImplementationValidationTest,SeatingFromCalledQueueUiImplementationValidationTest,CleaningControllerTest,ReservationControllerTest,WalkInDirectSeatingControllerTest" test`
- `mvn -q "-Dtest=TableResourceListApplicationServiceTest,TableResourceListControllerTest,TableResourceListPersistenceAdapterTest,StaffUiV12TableSelectionValidationTest,AppGateRequiredPermissionTest,QueueCallUiImplementationValidationTest,SeatingFromCalledQueueUiImplementationValidationTest,CleaningControllerTest,ReservationControllerTest,WalkInDirectSeatingControllerTest" test`
- `mvn -q test`
- `cmd /c npm run build`

The first full test run exposed old static-contract expectations that did not yet include the newly approved table-resource API and UI files. Those tests were updated. The final full Maven run completed successfully.

## Release Notes

Version / Date:

- Staff UI V1.2 follow-up, 2026-06-23

Changed:

- The today reservation list is now compact, mobile-first, and split into reusable reservation workbench components.
- Staff can enter existing reservation check-in and arrived direct-seating workflows from each reservation row without copying IDs manually.

Risk:

- Cancellation is intentionally not shipped as a mutation in this round because the backend cancellation API, permission, idempotency, and audit contract has not been approved or implemented.

Rollback:

- Revert `ReservationTodayListPanel`, `ReservationTodayListItem`, and the `ReservationTodayViewPage` component wiring to return to the previous verbose reservation cards.

## Boundary Confirmation

- Complex Table Map implemented: No
- Table status mutation from table page implemented: No
- Auto table assignment implemented: No
- Queue Display implemented: No
- Workbench large screen implemented: No
- Lightweight reservation date calendar implemented: Yes
- Complex reservation calendar/status workflow implemented: No
- No-show implemented: No
- Cancellation implemented: No
- Cancellation UI mutation implemented: No
- Manual reservation ID copy exposed: No
- Reservation table assignment implemented: No
- Migration changed: No
- Production database touched: No
- Staff UI direct POS call: No
- POS adapter implemented: No
- GitHub remote added: No
- GitHub push performed: No
