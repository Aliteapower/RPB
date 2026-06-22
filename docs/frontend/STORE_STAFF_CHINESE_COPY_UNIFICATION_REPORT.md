# Store Staff Chinese Copy Unification Result

## 1. Read Documents

- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`
- `src/pages/StoreStaffHomePage.vue`
- `src/pages/ReservationCreatePage.vue`
- `src/pages/ReservationCheckInPage.vue`
- `src/pages/ReservationArrivedDirectSeatingPage.vue`
- `src/pages/WalkInDirectSeatingPage.vue`
- `src/pages/CleaningCompletePage.vue`
- `src/components/DateTimeWheelPicker.vue`
- `src/api/meAppsApi.ts`
- `src/api/reservationCreateApi.ts`
- `src/api/reservationCheckInApi.ts`
- `src/api/reservationArrivedDirectSeatingApi.ts`
- `src/api/walkInDirectSeatingApi.ts`
- `src/api/cleaningApi.ts`
- `src/types/meApps.ts`
- `src/types/reservation.ts`
- `src/types/reservationCheckIn.ts`
- `src/types/reservationArrivedDirectSeating.ts`
- `src/types/walkInDirectSeating.ts`
- `src/types/cleaning.ts`
- `src/router/index.ts`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed:

- Reservation Staff End-to-End Smoke Review passed in the prior round.
- Staff Home had the completed V1 entries before this round.
- No Queue, No-show, Cancellation, Reservation list/calendar, or Table map exists in the current staff UI.
- This round is UI copy only.

## 2. Created / Updated Files

Updated:

- `src/pages/StoreStaffHomePage.vue`
- `src/pages/ReservationCreatePage.vue`
- `src/pages/ReservationCheckInPage.vue`
- `src/pages/ReservationArrivedDirectSeatingPage.vue`
- `src/pages/WalkInDirectSeatingPage.vue`
- `src/pages/CleaningCompletePage.vue`
- `src/components/DateTimeWheelPicker.vue`

Created:

- `docs/frontend/STORE_STAFF_CHINESE_COPY_UNIFICATION_REPORT.md`

Note:

- `DateTimeWheelPicker.vue` was updated only for visible date/time column copy (`year/month/day/hour/minute` -> `年/月/日/时/分`) because it renders inside the Reservation Create page.

## 3. Staff Home Copy

Updated Staff Home visible copy:

- `Store staff` -> `门店员工`
- `现场闭环` -> `员工工作台`
- `Store ...` -> `门店 ...`
- Loading and unavailable panels were localized.
- Operation entries were localized:
  - `散客直接入座`
  - `清台处理`
  - `创建预约`
  - `预约到店`
  - `预约入座`

No unfinished entries were added.

## 4. Reservation Create Copy

Updated visible copy:

- Page title: `创建预约`
- Main fields: `人数`, `预约开始时间`, `预约结束时间（可选）`
- Customer fields: `客户 ID（可选）`, `客户姓名（可选）`, `客户昵称（可选）`, `手机号（可选）`
- Button: `提交预约`, `提交中...`
- Success area: `预约创建成功`, `预约编号`, `预约状态`, `人数`, `开始时间`, `结束时间`, `保留到`, `营业日期`, `客户信息`, `事件`, `幂等状态`
- Error area: `创建失败`, `错误代码`, `消息键`

## 5. Reservation CheckIn Copy

Updated visible copy:

- Page title: `预约到店`
- Fields: `预约 ID`, `到店时间（可选）`, `原因代码（可选）`, `备注（可选）`
- Button: `确认到店`, `提交中...`
- Success area: `到店确认成功`, `预约编号`, `预约状态`, `到店时间`, `是否已到店`, `事件`, `幂等状态`
- `alreadyArrived=true` helper copy: `该预约此前已完成到店确认`
- Error area: `到店确认失败`, `错误代码`, `消息键`

## 6. Reservation Direct Seating Copy

Updated visible copy:

- Page title: `预约入座`
- Fields: `预约 ID`, `桌台 ID`, `桌组 ID`, `调整原因代码（可选）`, `调整说明（可选）`, `备注（可选）`
- Resource rule: `桌台 ID 和桌组 ID 必须二选一`
- Local resource error hints:
  - `请填写桌台 ID 或桌组 ID`
  - `桌台 ID 和桌组 ID 只能选择一个`
- Button: `确认入座`, `提交中...`
- Success area: `入座成功`, `预约编号`, `预约状态`, `入座记录 ID`, `入座状态`, `资源类型`, `资源 ID`, `是否已入座`, `事件`, `幂等状态`
- `alreadySeated=true` helper copy: `该预约此前已完成入座`
- Error area: `入座失败`, `错误代码`, `消息键`

## 7. Cleaning Copy

Updated visible copy:

- Page title: `清台处理`
- Forms: `开始清台`, `完成清台`
- Fields: `入座记录 ID`, `清台记录 ID`, `原因代码（可选）`, `备注（可选）`
- Buttons: `开始清台`, `完成清台`, `提交中...`
- Success area: `清台操作成功`, `桌位已释放`, `清台状态`, `桌台状态`, `清台记录 ID`, `事件`, `幂等状态`
- Error area: `清台操作失败`, `错误代码`, `消息键`

## 8. WalkIn Copy

Updated visible copy:

- Page title: `散客直接入座`
- Fields: `人数`, `桌台 ID`, `桌组 ID`, `客户姓名（可选）`, `客户昵称（可选）`, `手机号（可选）`
- Existing override fields were localized as `调整原因代码（可选）` and `调整说明（可选）`.
- Button: `确认入座`, `提交中...`
- Success area: `散客入座成功`, `散客记录 ID`, `入座记录 ID`, `桌台状态`, `幂等状态`
- Error area: `入座失败`, `错误代码`, `消息键`

## 9. Common Copy

Common visible copy was unified:

- `返回员工首页`
- `提交中...`
- `错误代码`
- `消息键`
- `幂等键`
- `幂等状态`
- `事件`
- `备注`
- `状态：confirmed`
- `状态：arrived`
- `状态：seated`

Raw status values are still displayed as backend values.

## 10. Error Display Preservation

Preserved:

- `apiError.error.code`
- `apiError.error.messageKey`

The labels around them were localized, but the raw values are still rendered directly.

## 11. Permission Display Preservation

Preserved:

- Staff Home still calls `fetchMeApps(storeId)`.
- Staff Home still resolves the visible `reservation_queue` entry through `/api/me/apps`.
- `预约到店` still uses `canCheckInReservation` with `permissions.includes('reservation.check_in')`.
- `预约入座` still uses `canSeatArrivedReservation` with `permissions.includes('reservation.seat')`.
- Backend App Gate remains the final authorization boundary.

Runtime `/api/me/apps` check returned visible `reservation_queue` with:

```text
reservation.check_in
reservation.seat
walkin.direct_seating.create
cleaning.start
reservation.create
cleaning.complete
```

## 12. Route Check

Checked in browser against local Vite + local backend:

- `/stores/20000000-0000-0000-0000-000000000701/staff`
  - H1: `员工工作台`
  - Visible entries: `散客直接入座`, `清台处理`, `创建预约`, `预约到店`, `预约入座`
  - Forbidden entries: none found
- `/stores/20000000-0000-0000-0000-000000000701/reservations/create`
  - H1: `创建预约`
  - Button: `提交预约`
  - Date picker columns: `年`, `月`, `日`, `时`, `分`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/check-in`
  - H1: `预约到店`
  - Button: `确认到店`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct`
  - H1: `预约入座`
  - Button: `确认入座`
- `/stores/20000000-0000-0000-0000-000000000701/cleaning`
  - H1: `清台处理`
  - Buttons: `开始清台`, `完成清台`
- `/stores/20000000-0000-0000-0000-000000000701/walk-ins/direct-seating`
  - H1: `散客直接入座`
  - Button: `确认入座`

Forbidden visible English technical copy checked and not found on the relevant routes:

```text
WalkIn Direct Seating
Cleaning Complete
Create Reservation
Check In Reservation
Seat Arrived Reservation
Submitting
Back to staff home
year
month
day
hour
minute
```

## 13. Commands

- `npm run build`: passed
- `mvn test`: not run
- `pg_ctl -D target/local-runtime/20260621-160607/pgdata ... start`: used only to restart the existing local temporary PostgreSQL for route validation
- `mvn spring-boot:run`: used only to restart local backend for `/api/me/apps` validation
- Browser route validation: passed

## 14. Build / Test Result

- `npm run build`: passed
  - `vue-tsc --noEmit`: passed
  - `vite build`: passed
  - Vite transformed 57 modules
- `mvn test`: not run because this round changed frontend/UI copy only and no backend code was modified.

## 15. Boundary Check

Backend API changed: No  
Business state machine changed: No  
App Gate changed: No  
Migration changed: No  
Reservation list created: No  
Calendar created: No  
Table map created: No  
Queue created: No  
No-show created: No  
Cancellation created: No  
Production database touched: No  
Seed data inserted: No  
Backend source changed: No  
API path changed: No  
Permission key changed: No  
Full i18n framework added: No  
Language switcher added: No  

## 16. Open Questions

- None for this copy-only round.

## 17. Next Step Recommendation

- Keep this Chinese copy as the V1 staff-facing baseline.
- Plan full i18n extraction later as a separate round, after more staff flows stabilize.
- Keep Queue, Reservation list/calendar, Table map, No-show, and Cancellation out until each has its own approved contract.
