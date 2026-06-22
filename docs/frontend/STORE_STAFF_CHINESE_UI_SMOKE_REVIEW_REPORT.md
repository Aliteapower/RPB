# Store Staff Chinese UI Smoke Review Report

## Store Staff Chinese UI Smoke Review Result

### 1. Read Documents

- `docs/frontend/STORE_STAFF_CHINESE_COPY_UNIFICATION_REPORT.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `src/pages/StoreStaffHomePage.vue`
- `src/pages/ReservationCreatePage.vue`
- `src/pages/ReservationCheckInPage.vue`
- `src/pages/ReservationArrivedDirectSeatingPage.vue`
- `src/pages/WalkInDirectSeatingPage.vue`
- `src/pages/CleaningCompletePage.vue`
- `src/components/DateTimeWheelPicker.vue`
- `src/router/index.ts`
- `/api/me/apps` frontend usage in `src/api/meAppsApi.ts`

Confirmed before smoke:

- Chinese copy unification report exists and records the completed staff-side Chinese copy pass.
- Previous `npm run build` was recorded as passed in the Chinese copy unification report.
- Staff Home is expected to expose only the current five staff operation entries.
- Frontend app visibility is still driven by `/api/me/apps`; backend App Gate remains final authorization.
- Error panels are expected to preserve raw `error.code` and `error.messageKey`.

### 2. Validation Environment

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://127.0.0.1:8080`
- Browser: Codex in-app browser smoke review
- Store: `20000000-0000-0000-0000-000000000701`
- Auth/app source: local `/api/me/apps?storeId=20000000-0000-0000-0000-000000000701`

Observed `/api/me/apps` response:

- `success: true`
- visible app: `reservation_queue`
- permissions:
  - `reservation.check_in`
  - `reservation.seat`
  - `walkin.direct_seating.create`
  - `cleaning.start`
  - `reservation.create`
  - `cleaning.complete`

### 3. Routes Checked

- `/stores/20000000-0000-0000-0000-000000000701/staff`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/create`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/check-in`
- `/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct`
- `/stores/20000000-0000-0000-0000-000000000701/cleaning`
- `/stores/20000000-0000-0000-0000-000000000701/walk-ins/direct-seating`

Result:

- All six routes opened successfully in the browser.
- Main page headings rendered in Chinese.
- No incomplete entries were observed on Staff Home.

### 4. Staff Home Chinese Entry Check

Required Chinese entries observed:

- `创建预约`
- `预约到店`
- `预约入座`
- `清台处理`
- `散客直接入座`

Forbidden entries not observed:

- `排队叫号`
- `预约列表`
- `今日预约`
- `桌位图`
- `取消预约`
- `爽约处理`

Staff Home operation entry display now maps to concrete permission checks:

- `创建预约`: `reservation.create`
- `预约到店`: `reservation.check_in`
- `预约入座`: `reservation.seat`
- `清台处理`: `cleaning.start` and `cleaning.complete`
- `散客直接入座`: `walkin.direct_seating.create`

### 5. Permission Display Check

- `/api/me/apps` returned normally from the local backend.
- `reservation.create` controls the `创建预约` entry.
- `reservation.check_in` controls the `预约到店` entry.
- `reservation.seat` controls the `预约入座` entry.
- `cleaning.start / cleaning.complete` control the `清台处理` entry because the page exposes both start and complete operations.
- `walkin.direct_seating.create` controls the `散客直接入座` entry.
- Frontend permission logic remains display-only.
- Backend App Gate remains the final authorization layer.

### 6. Page Copy Check

Create Reservation:

- Title: `创建预约`
- Main labels are Chinese, including `预约时间`, `客户电话`, `人数`, `客户称呼`, `客户信息`, `备注`
- Date/time picker labels are Chinese: `年`, `月`, `日`, `时`, `分`
- Submit button: `提交预约`
- Error title: `创建失败`

CheckIn:

- Title: `预约到店`
- Main labels are Chinese, including `预约 ID`, `到店信息`, `备注`
- Submit button: `确认到店`
- Success area title: `到店确认成功`
- Error title: `到店确认失败`

Direct Seating:

- Title: `预约入座`
- Main labels are Chinese, including `预约 ID`, `桌台 ID`, `桌组 ID`, `调整信息`, `备注`
- Resource rule copy is Chinese: `桌台 ID 和桌组 ID 必须二选一`
- Submit button: `确认入座`
- Success area title: `入座成功`
- Error title: `入座失败`

Cleaning:

- Title: `清台处理`
- Main labels are Chinese, including `入座记录 ID`, `开始清台`, `完成清台`
- Submit buttons: `开始清台`, `完成清台`
- Success area title: `清台操作成功`
- Error title: `清台操作失败`

WalkIn:

- Title: `散客直接入座`
- Main labels are Chinese, including `门店 ID`, `客户电话`, `人数`, `客户称呼`, `客户信息`, `桌台选择`, `调整信息`, `备注`
- Submit button: `确认入座`
- Success area title: `入座成功`
- Error title: `入座失败`

### 7. Error Display Preservation

Browser smoke used local safe failure scenarios to confirm error panels still expose raw code and message key values.

- Create Reservation:
  - error title: `创建失败`
  - error.code: `INVALID_PHONE_E164`
  - error.messageKey: `reservation.invalid_phone_e164`
- CheckIn:
  - error title: `到店确认失败`
  - error.code: `RESERVATION_NOT_FOUND`
  - error.messageKey: `reservation.not_found`
- Direct Seating:
  - error title: `入座失败`
  - error.code: `RESERVATION_NOT_FOUND`
  - error.messageKey: `reservation.not_found`
- Cleaning:
  - error title: `清台操作失败`
  - error.code: `SEATING_NOT_FOUND`
  - error.messageKey: `cleaning.seating_not_found`
- WalkIn:
  - error title: `入座失败`
  - error.code: `INVALID_PHONE_E164`
  - error.messageKey: `walkin.direct_seating.invalid_phone_e164`

Static source check also confirmed all five pages still render:

- `错误代码：{{ apiError.error.code }}`
- `消息键：{{ apiError.error.messageKey }}`

### 8. Commands

- `npm run build`: Passed.
- `mvn test`: Not run, because this round only changed frontend display logic and documentation.
- `/api/me/apps` local runtime check: Passed.
- Browser smoke review: Passed for all six required routes.

### 9. Files Changed

- `src/pages/StoreStaffHomePage.vue`
- `docs/frontend/STORE_STAFF_CHINESE_UI_SMOKE_REVIEW_REPORT.md`

### 10. Boundary Check

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

### 11. Open Questions

- None for this smoke review.

### 12. Next Step Recommendation

- Continue with the next explicitly scoped staff-side business slice.
- Keep login/default store selection as a separate UI/auth scope if needed later.
