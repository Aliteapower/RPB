# Reservation Today View UI Contract V1

## 1. Purpose

Design the minimum mobile-first read-only UI contract for `今日预约`.

The UI lets store staff:

```text
open 今日预约
-> see today's reservations for the current Store
-> find reservationCode / time / party size / status
-> copy reservationId
-> jump to existing 预约到店 or 预约入座 pages
```

This contract does not implement a Vue page, router entry, API client, Staff Home entry, backend API, App Gate metadata, migration, or seed data.

## 2. Route

Future route:

```text
/stores/:storeId/reservations/today
```

Future page:

```text
ReservationTodayViewPage.vue
```

Route rules:

- `storeId` comes from the path.
- Page must remain within the Store staff shell/operation pattern.
- Page must not be a calendar month/week view.
- Page must not be a table map.

## 3. Staff Home Entry

Future Staff Home entry:

```text
今日预约
```

Entry description:

```text
查看当天预约并进入到店或入座操作
```

Display condition:

```text
reservation_queue app available
+
permissions contains reservation.today_view
```

Implementation boundary:

- This contract declares the future rule only.
- This round does not modify `StoreStaffHomePage.vue`.
- Future implementation must align this with the App Gate `/api/me/apps` capability/entry contract before changing Staff Home.

## 4. Page Layout

Mobile-first, single-column layout.

Required areas:

```text
标题：今日预约
日期显示
状态筛选
预约卡片列表
空状态提示
加载状态
错误区域
返回员工首页
```

Suggested page order:

1. Header: `今日预约`.
2. Store/date summary: `营业日期 YYYY-MM-DD`.
3. Status segmented control.
4. Reservation cards.
5. Empty state or error state when applicable.

## 5. Status Filter UI

Default selected filter:

```text
进行中
```

Filter mapping:

| UI label | API status query |
| --- | --- |
| `进行中` | omitted or `operational` |
| `全部` | `all` |
| `已确认` | `confirmed` |
| `已到店` | `arrived` |
| `已入座` | `seated` |
| `已取消` | `cancelled` |
| `爽约` | `no_show` |
| `已完成` | `completed` |

Boundary:

- These are filters only.
- `已取消` and `爽约` must not create Cancellation or No-show operations.
- No Queue, Calendar, or Table map entry is introduced by the filter.

## 6. Reservation Card Fields

Each reservation card must display:

```text
预约编号
预约时间
人数
预约状态
客户姓名 / 昵称
手机号后四位或 masked phone
备注
预约 ID
```

Field mapping:

| UI copy | API field |
| --- | --- |
| `预约编号` | `reservationCode` |
| `预约时间` | `reservedStartAt` / `reservedEndAt`, displayed in Store timezone |
| `人数` | `partySize` |
| `预约状态` | `status` |
| `客户姓名` | `customerName` |
| `客户昵称` | `customerNickname` |
| `手机号` | `phoneMasked` |
| `备注` | `note` |
| `预约 ID` | `reservationId` |

Status display:

- Show a Chinese label for readability.
- Preserve raw status code in a small secondary line or data label where useful.
- Do not translate or mutate backend status values before action logic.

## 7. Actions

Allowed card actions:

```text
复制预约 ID
预约到店
预约入座
```

Action rules:

| Status | UI behavior |
| --- | --- |
| `confirmed` | Show `预约到店`; do not show `预约入座`. |
| `arrived` | Show `预约入座`; do not show `预约到店`. |
| `seated` | Show `已入座`; no mutation button. |
| `cancelled` | Read-only display only. |
| `no_show` | Read-only display only. |
| `completed` | Read-only display only. |

Copy behavior:

- `复制预约 ID` copies the raw `reservationId`.
- On success, show short Chinese feedback such as `已复制`.
- If browser clipboard is unavailable, keep the reservationId visible so staff can manually select it.

## 8. Jump Behavior

Today View must not execute CheckIn or Seating directly.

Jump targets:

```text
预约到店 -> /stores/:storeId/reservations/check-in?reservationId=...
预约入座 -> /stores/:storeId/reservations/seating/direct?reservationId=...
```

Future implementation requirement:

- If `ReservationCheckInPage.vue` and `ReservationArrivedDirectSeatingPage.vue` do not yet read query `reservationId`, the Today View UI implementation round must add prefill support in those pages or record it as a blocked follow-up before enabling the jump buttons.
- Jumping must preserve the current `storeId`.
- Jumping must not submit a status mutation automatically.

## 9. Empty State

When the API succeeds with zero items:

```text
今日暂无预约
```

Rules:

- Empty state is not an error.
- Do not show `error.code` or `error.messageKey` for empty state.
- Keep date and filter controls visible so staff can change the filter.

## 10. Loading State

Loading copy:

```text
加载中...
```

Rules:

- Loading text must be Chinese.
- Loading must not hide the page title.
- Loading must not show stale reservation cards for a different Store or date.

## 11. Error Display

Error title:

```text
加载失败
```

The error area must show:

```text
错误代码：{{ error.code }}
消息键：{{ error.messageKey }}
```

Rules:

- Preserve raw `error.code`.
- Preserve raw `error.messageKey`.
- Do not replace `messageKey` with hardcoded Chinese business copy.
- `details` may be shown only if safe and useful.

## 12. Mobile-First Rules

Required UI rules:

- Single-column layout.
- Cards use stable dimensions and no nested cards.
- Buttons fit inside their container on narrow screens.
- Reservation ID can wrap or use monospace overflow-safe styling.
- Status filter can horizontally scroll or wrap cleanly.
- Date/time display uses Store timezone and locale.
- No table grid, drag map, month calendar, or dense desktop-only table is introduced in V1.

## 13. API Client Contract

Future frontend client:

```text
GET /api/v1/stores/{storeId}/reservations/today?businessDate=YYYY-MM-DD&status=operational
```

Client rules:

- `storeId` comes from route params.
- `businessDate` is optional.
- `status` is optional and defaults to `operational`.
- No request body.
- No `Idempotency-Key`.
- Parse success envelope and items.
- Parse error envelope with `error.code` and `error.messageKey`.

## 14. Permission Contract

Future UI visibility:

```text
app_key = reservation_queue
permission = reservation.today_view
```

Rules:

- Staff Home entry is hidden when the actor lacks `reservation.today_view`.
- Backend App Gate remains final authorization.
- Manual route entry without permission must fail at backend API level.
- This contract does not modify App Gate metadata.

## 15. Future Implementation Tests

Frontend tests must cover:

- Route opens at `/stores/:storeId/reservations/today`.
- Staff Home shows `今日预约` only with `reservation.today_view`.
- Staff Home does not show `今日预约` without `reservation.today_view`.
- API success renders cards.
- Empty API success renders `今日暂无预约`.
- Loading copy is Chinese.
- Error panel renders `加载失败`, `错误代码`, and `消息键`.
- `error.code` raw value is visible.
- `error.messageKey` raw value is visible.
- `复制预约 ID` copies the reservationId or leaves it visibly selectable when clipboard is unavailable.
- `confirmed` item shows `预约到店`.
- `arrived` item shows `预约入座`.
- `seated` item shows `已入座` and no mutation button.
- `cancelled`, `no_show`, and `completed` items are read-only.
- Jump to CheckIn uses `?reservationId=...`.
- Jump to Direct Seating uses `?reservationId=...`.
- No Queue, No-show action, Cancellation action, Calendar, or Table map buttons are rendered.

## 16. Boundary Check

API implemented: No

UI implemented: No

Staff Home changed: No

Vue Router changed: No

Queue designed: No

Seating decision designed: No

No-show action designed: No

Cancellation action designed: No

Calendar designed: No

Table map designed: No

Migration changed: No

Production database touched: No

## 17. Open Questions

- Should the future UI implementation add query prefill support to CheckIn and Direct Seating pages in the same round as Today View?
- Should terminal statuses be visible behind filters in V1 UI, or deferred until the API implementation proves the operational filter first?

## 18. Open Conflicts

- Earlier App Gate guidance described `/api/me/apps` as app-level entry visibility. This UI contract needs `reservation.today_view` for Staff Home button visibility. Future implementation must resolve this by either formalizing entry-permission usage for this button or adding a capability-level response contract.

## 19. Next Step Recommendation

- Implement the backend read-only API first.
- Then implement the UI route, Staff Home entry, copy behavior, and jump prefill support.
- Keep Queue, direct state mutation, No-show, Cancellation, Calendar, and Table map out of the Today View V1 implementation.
