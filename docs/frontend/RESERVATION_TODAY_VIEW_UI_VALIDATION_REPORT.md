# Reservation Today View UI Validation Report V1

## 1. Read Documents

Read and checked:

- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_CONTRACT.md`
- `docs/api/RESERVATION_TODAY_VIEW_API_CONTRACT.md`
- `docs/api/RESERVATION_TODAY_VIEW_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`
- `docs/frontend/RESERVATION_CHECKIN_UI_VALIDATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `src/pages/ReservationTodayViewPage.vue`
- `src/api/reservationTodayViewApi.ts`
- `src/types/reservationTodayView.ts`
- `src/router/index.ts`
- `src/pages/StoreStaffHomePage.vue`
- `src/pages/ReservationCheckInPage.vue`
- `src/pages/ReservationArrivedDirectSeatingPage.vue`
- `src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/appgate/api/MeAppsController.java`
- `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeCurrentActorProvider.java`
- `src/main/resources/application.yml`

Confirmed from source and docs:

- `ReservationTodayViewPage.vue -> reservationTodayViewApi.ts -> GET /api/v1/stores/{storeId}/reservations/today -> App Gate reservation.today_view -> service -> UI cards -> jump links` is wired.
- `reservation.today_view` is registered under `reservation_queue`.
- `/api/me/apps` projects actor-owned entry permissions.
- Today View API is read-only.
- No Queue, No-show, Cancellation, Reservation list/calendar, Table map, auto assignment, edit, delete, backend API change, App Gate change, or migration is part of this round.

## 2. Validation Environment

- Frontend: `http://127.0.0.1:5173`, Vite dev server, `/api -> http://127.0.0.1:8080`
- Backend: `http://127.0.0.1:8080`, Spring Boot local profile
- Database: local PostgreSQL `127.0.0.1:63822/reservation_platform`
- Browser: Codex in-app Browser automation
- Store: `20000000-0000-0000-0000-000000000701`
- Tenant: `10000000-0000-0000-0000-000000000701`
- Local auth full-permission actor:
  - `reservation.create`
  - `reservation.check_in`
  - `reservation.seat`
  - `reservation.today_view`
  - `cleaning.start`
  - `cleaning.complete`
  - `walkin.direct_seating.create`

Local backend startup note:

- A fresh local backend startup against PostgreSQL 17.10 failed with `Unsupported Database: PostgreSQL 17.10` from Flyway.
- The schema already existed in the local validation database, so the local runtime was restarted with `spring.flyway.enabled=false` only for browser validation.
- No migration file or production configuration was changed.

Local validation fixture:

- Inserted local-only fixture data for `businessDate=2030-06-20`.
- Reservation codes:
  - `TV-UI-CONFIRMED`
  - `TV-UI-ARRIVED`
  - `TV-UI-SEATED`
  - `TV-UI-CANCELLED`
  - `TV-UI-NO-SHOW`
  - `TV-UI-COMPLETED`
- This data was inserted into the local database only. No production database was touched.

## 3. Routes Checked

- `GET /api/me/apps?storeId=20000000-0000-0000-0000-000000000701`
- `/stores/20000000-0000-0000-0000-000000000701/staff`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/today`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/check-in?reservationId=53000000-0000-0000-0000-000000000701`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct?reservationId=53000000-0000-0000-0000-000000000702`
- Error route: `/stores/20000000-0000-0000-0000-000000000999/reservations/today`

## 4. Staff Home Permission Check

With `reservation.today_view`:

- `/api/me/apps` returned `reservation_queue.permissions` containing `reservation.today_view`.
- Staff Home rendered 6 links.
- `今日预约` link was visible at `/stores/{storeId}/reservations/today`.
- Existing approved entries stayed visible:
  - `散客直接入座`
  - `清台处理`
  - `创建预约`
  - `预约到店`
  - `预约入座`

Without `reservation.today_view`:

- Local backend was restarted without `reservation.today_view` in local-auth permissions.
- `/api/me/apps` returned `reservation_queue.permissions` without `reservation.today_view`.
- Staff Home rendered 5 links.
- `今日预约` link was hidden.
- A small bug was found and fixed: a static handoff note still mentioned `今日预约` when the permission was absent.
- After the fix, Staff Home no longer displayed `今日预约` text or link without the permission.

Forbidden entries not rendered:

- `排队叫号`: No
- `预约列表`: No
- `今日预约列表` as a separate list entry: No
- `桌位图`: No
- `取消预约`: No
- `爽约处理`: No

## 5. API Client Check

Source and runtime checks confirmed:

- Method: `GET`
- Endpoint: `/api/v1/stores/{storeId}/reservations/today`
- Query supported:
  - `businessDate`
  - `status`
- Request body: No
- `Idempotency-Key`: No
- `Accept: application/json`: Yes
- Backend guard: `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")`

Direct API runtime results:

- `status=operational`: returned confirmed, arrived, seated only.
- `status=all`: returned 6 fixture reservations.
- `status=confirmed`: returned only `confirmed`.
- `status=arrived`: returned only `arrived`.
- `status=seated`: returned only `seated`.
- `status=cancelled`: returned only `cancelled`.
- `status=no_show`: returned only `no_show`.
- `status=completed`: returned only `completed`.
- `status=invalid`: returned `400` with:
  - `code = INVALID_STATUS_FILTER`
  - `messageKey = reservation.today_view.invalid_status_filter`

## 6. Page Copy Check

Today View page:

- Page title: `今日预约`
- Date label: `营业日期`
- Status label: `状态筛选`
- Status buttons:
  - `进行中`
  - `全部`
  - `已确认`
  - `已到店`
  - `已入座`
  - `已取消`
  - `爽约`
  - `已完成`
- Loading copy from source:
  - `加载中...`
  - `正在读取当前门店预约。`
- Empty title:
  - `今日暂无预约`
- Error title and fields:
  - `加载失败`
  - `错误代码`
  - `消息键`

## 7. Card Display Check

Cards displayed:

- `reservationCode`
- reserved time range
- `partySize`
- status label and raw status code
- customer name
- customer nickname
- masked phone, for example `****0701`
- note
- full `reservationId`

UUID wrapping/readability:

- Full reservation IDs were visible in cards.
- Long IDs did not break the page interaction.

## 8. Status Filter Check

Browser validation on `businessDate=2030-06-20`:

- `进行中`: displayed `TV-UI-CONFIRMED`, `TV-UI-ARRIVED`, `TV-UI-SEATED`.
- `全部`: displayed all 6 fixture reservations.
- `已确认`: displayed only `TV-UI-CONFIRMED`.
- `已到店`: displayed only `TV-UI-ARRIVED`.
- `已入座`: displayed only `TV-UI-SEATED`.
- `已取消`: displayed only `TV-UI-CANCELLED`.
- `爽约`: displayed only `TV-UI-NO-SHOW`.
- `已完成`: displayed only `TV-UI-COMPLETED`.

Invalid status:

- Invalid status is not selectable through the UI.
- Direct backend API validation returned structured `INVALID_STATUS_FILTER / reservation.today_view.invalid_status_filter`.

## 9. Action Jump Check

Confirmed reservation:

- Card: `TV-UI-CONFIRMED`
- Action: `预约到店`
- Jumped to:
  - `/stores/{storeId}/reservations/check-in?reservationId=53000000-0000-0000-0000-000000000701`
- CheckIn page title: `预约到店`
- Reservation ID input was prefilled with `53000000-0000-0000-0000-000000000701`.
- No auto-submit occurred.

Arrived reservation:

- Card: `TV-UI-ARRIVED`
- Action: `预约入座`
- Jumped to:
  - `/stores/{storeId}/reservations/seating/direct?reservationId=53000000-0000-0000-0000-000000000702`
- Direct Seating page title: `预约入座`
- Reservation ID input was prefilled with `53000000-0000-0000-0000-000000000702`.
- No auto-submit occurred.

Seated reservation:

- Card: `TV-UI-SEATED`
- Displayed `已入座`.
- No state-changing action rendered.

Terminal reservations:

- `cancelled`, `no_show`, and `completed` displayed `只读`.
- No cancellation, no-show, edit, or delete action was created.

## 10. Copy ReservationId Check

Browser action:

- Clicked `复制预约 ID` on `TV-UI-CONFIRMED`.
- Browser clipboard write was unavailable in the automation context.
- UI displayed fallback feedback: `请手动复制`.
- The full reservation ID remained visible on the card.
- No business API call or state change was triggered.

## 11. Empty And Error Check

Empty state:

- Date: `2040-01-01`
- UI displayed `今日暂无预约`.
- No error state was displayed.
- Card count: `0`

Error state:

- Route: `/stores/20000000-0000-0000-0000-000000000999/reservations/today`
- UI displayed:
  - `加载失败`
  - `错误代码：REQUEST_FAILED`
  - `消息键：reservation.today_view.request_failed`
- This route produced a bodyless denied response in local runtime, so the frontend fallback error was shown.
- Direct backend invalid-status validation confirmed backend-shaped raw error preservation for:
  - `INVALID_STATUS_FILTER`
  - `reservation.today_view.invalid_status_filter`

## 12. Read-only / DB Side Effect Check

Baseline before browser read-only actions:

- Fixture reservation statuses:
  - `53000000-0000-0000-0000-000000000701=confirmed`
  - `53000000-0000-0000-0000-000000000702=arrived`
  - `53000000-0000-0000-0000-000000000703=seated`
  - `53000000-0000-0000-0000-000000000704=cancelled`
  - `53000000-0000-0000-0000-000000000705=no_show`
  - `53000000-0000-0000-0000-000000000706=completed`
- Counts:
  - `queue_tickets=0`
  - `seatings=3`
  - `business_events=34`
  - `audit_logs=26`
  - `idempotency_records=28`
  - `app_gate_audit_logs=0`

After browser read-only actions:

- Fixture reservation statuses stayed unchanged.
- Counts stayed unchanged:
  - `queue_tickets=0`
  - `seatings=3`
  - `business_events=34`
  - `audit_logs=26`
  - `idempotency_records=28`
  - `app_gate_audit_logs=0`

Conclusion:

- Today View browsing, filters, copy fallback, and action-link navigation did not mutate business state.

## 13. Commands

`npm run build`:

```text
Exit code: 0
vue-tsc --noEmit passed
vite build passed
61 modules transformed
```

`mvn -q "-Dtest=ReservationTodayView*Test" test`:

```text
Exit code: 0
```

`mvn test`:

```text
Not run in this round because no backend code was changed.
```

## 14. Files Changed

- `src/pages/StoreStaffHomePage.vue`
  - Added `v-if="canViewTodayReservations"` to the static Today View handoff note so no-permission Staff Home does not display `今日预约`.
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_VALIDATION_REPORT.md`
  - Added this validation report.

## 15. Boundary Check

Backend Controller changed: No

Backend Application Service changed: No

Backend DTO changed: No

Backend API path changed: No

Business state machine changed: No

App Gate logic changed: No

Permission metadata changed: No

Migration changed: No

SQL file changed: No

Queue UI/API created: No

No-show UI/API created: No

Cancellation UI/API created: No

Reservation list/calendar created: No

Table map created: No

Auto assignment UI created: No

Edit/delete action created: No

Production config changed: No

Production database touched: No

Production seed data inserted: No

Local validation fixture inserted: Yes, local PostgreSQL only.

## 16. Open Questions

- The bodyless local App Gate denial path surfaces frontend fallback `REQUEST_FAILED / reservation.today_view.request_failed`; backend-shaped application errors preserve raw `code/messageKey`. If product wants every App Gate denial to surface a raw JSON envelope in the UI, that should be handled in a separate backend/API error-envelope round.
- The local PostgreSQL 17.10 and current Flyway version mismatch requires `spring.flyway.enabled=false` for this local runtime validation. A separate local runtime maintenance task should align Flyway/PostgreSQL compatibility.

## 17. Next Step Recommendation

- Treat Today View UI validation as passed after the Staff Home no-permission copy fix.
- Keep Today View read-only.
- Next approved slice can build on the Today View handoff into CheckIn and Direct Seating, while keeping Queue, No-show, Cancellation, Reservation list/calendar, Table map, and migrations out of scope unless separately approved.
