# Reservation Today View Vertical Slice Checklist V1

## 1. Purpose

This checklist protects the future Reservation Today View vertical slice from expanding beyond the approved read-only scope.

Selected vertical slice:

```text
Reservation Today View
```

## 2. Required Documents

- [x] Backend contract: `docs/backend/RESERVATION_TODAY_VIEW_CONTRACT.md`
- [x] UI contract: `docs/frontend/RESERVATION_TODAY_VIEW_UI_CONTRACT.md`
- [x] Vertical slice checklist: `docs/backend/RESERVATION_TODAY_VIEW_VERTICAL_SLICE_CHECKLIST.md`

## 3. Scope Gate

| Check | Required answer |
| --- | --- |
| Is this slice read-only? | Yes |
| Does it show current Store reservations for one business date? | Yes |
| Does default date use Store timezone? | Yes |
| Does it sort by reservation time? | Yes |
| Does it support status filtering? | Yes |
| Does it expose reservationId for copy/jump? | Yes |
| Does it directly mutate Reservation status? | No |
| Does it directly create Seating? | No |
| Does it directly create QueueTicket? | No |

## 4. Endpoint Gate

Future endpoint:

```http
GET /api/v1/stores/{storeId}/reservations/today
```

Checklist:

- [ ] Method is `GET`.
- [ ] Path is `/api/v1/stores/{storeId}/reservations/today`.
- [ ] `storeId` comes from path.
- [ ] `tenantId` comes from actor/security context.
- [ ] No trusted `tenantId` from request body or query.
- [ ] No request body.
- [ ] No `Idempotency-Key` required.
- [ ] No existing API path changed.

## 5. Data Source Gate

Allowed sources:

- [ ] `reservations`
- [ ] `customers`
- [ ] `stores`

Forbidden sources/artifacts:

- [ ] No new table.
- [ ] No materialized view.
- [ ] No Flyway migration.
- [ ] No SQL file.
- [ ] No seed data.
- [ ] No production database access.

## 6. Sorting / Filter Gate

- [ ] Default status filter is `operational`.
- [ ] `operational` means `confirmed`, `arrived`, `seated`.
- [ ] `status=all` includes `confirmed`, `arrived`, `seated`, `cancelled`, `no_show`, `completed`.
- [ ] Single status filters work for all supported statuses.
- [ ] Invalid status returns `INVALID_STATUS_FILTER`.
- [ ] Invalid date returns `INVALID_BUSINESS_DATE`.
- [ ] Sorting is `reservedStartAt ASC`.
- [ ] Tie-breaker is `createdAt ASC`.

## 7. Response Gate

Each item includes:

- [ ] `reservationId`
- [ ] `reservationCode`
- [ ] `status`
- [ ] `partySize`
- [ ] `reservedStartAt`
- [ ] `reservedEndAt`
- [ ] `holdUntilAt`
- [ ] `businessDate`
- [ ] nullable `customerName`
- [ ] nullable `customerNickname`
- [ ] nullable `phoneMasked`
- [ ] nullable `note`

V1 capability rule:

- [ ] Backend does not need to return `canCheckIn`.
- [ ] Backend does not need to return `canSeat`.
- [ ] Frontend derives jump display from `status`.
- [ ] Backend App Gate remains final authorization for mutation endpoints.

## 8. Permission / App Gate Gate

Required app mapping:

```text
app_key = reservation_queue
permission = reservation.today_view
```

Checklist:

- [ ] App key is `reservation_queue`.
- [ ] Permission is `reservation.today_view`.
- [ ] No new app key is created.
- [ ] Future API uses `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")` or project-equivalent annotation.
- [ ] App Gate denial happens before reservation data is returned.
- [ ] App Gate denial writes `app_gate_audit_logs`.
- [ ] App Gate denial does not write business events, state transitions, audit logs, idempotency records, QueueTicket, Seating, Table, or Cleaning state.

## 9. Staff Home / UI Gate

Future route:

```text
/stores/:storeId/reservations/today
```

Future Staff Home entry:

```text
今日预约
```

Checklist:

- [ ] Staff Home entry is shown only when `reservation.today_view` is available under `reservation_queue`.
- [ ] Page title is `今日预约`.
- [ ] Empty state is `今日暂无预约`.
- [ ] Error title is `加载失败`.
- [ ] Error panel displays `错误代码`.
- [ ] Error panel displays `消息键`.
- [ ] `error.code` raw value is visible.
- [ ] `error.messageKey` raw value is visible.
- [ ] Cards show reservation code, time, party size, status, customer hints, masked phone, note, and reservationId.
- [ ] `复制预约 ID` copies or leaves reservationId visibly selectable.
- [ ] `confirmed` shows jump to `预约到店`.
- [ ] `arrived` shows jump to `预约入座`.
- [ ] `seated` shows `已入座` only.
- [ ] `cancelled`, `no_show`, and `completed` are read-only.

## 10. Jump Gate

- [ ] CheckIn jump uses `/stores/:storeId/reservations/check-in?reservationId=...`.
- [ ] Direct Seating jump uses `/stores/:storeId/reservations/seating/direct?reservationId=...`.
- [ ] Jump preserves `storeId`.
- [ ] Jump does not auto-submit any form.
- [ ] Existing CheckIn / Direct Seating pages support query prefill or the implementation records this as a required follow-up before enabling jump buttons.

## 11. Boundary Checklist

| Boundary | Required answer |
| --- | --- |
| 是否只读 | Yes |
| 是否没有 Queue | Yes |
| 是否没有 Seating decision | Yes |
| 是否没有 status mutation | Yes |
| 是否没有 No-show | Yes |
| 是否没有 Cancellation | Yes |
| 是否没有 Calendar | Yes |
| 是否没有 Table map | Yes |
| 是否没有 Migration | Yes |
| 是否 app_key = reservation_queue | Yes |
| 是否 permission = reservation.today_view | Yes |

Expanded boundary:

- [ ] No Queue API created.
- [ ] No Queue UI created.
- [ ] No Seating decision created.
- [ ] No auto assignment created.
- [ ] No Reservation status mutation in Today View.
- [ ] No No-show API/UI created.
- [ ] No Cancellation API/UI created.
- [ ] No Reservation edit/delete created.
- [ ] No Calendar month/week view created.
- [ ] No Table map created.
- [ ] No Flyway migration changed.
- [ ] No SQL file changed.
- [ ] No production config changed.
- [ ] No production database touched.
- [ ] No seed data inserted.

## 12. Required Future Tests

Backend:

- [ ] Today reservations return current Store only.
- [ ] Tenant isolation.
- [ ] Store isolation.
- [ ] Store timezone default business date.
- [ ] Optional `businessDate`.
- [ ] Default operational status filter.
- [ ] Explicit `all` status filter.
- [ ] Individual status filters.
- [ ] Sorting by `reservedStartAt ASC`, `createdAt ASC`.
- [ ] Response fields present.
- [ ] Phone is masked.
- [ ] App Gate allowed.
- [ ] App Gate denied and audited.

Frontend:

- [ ] Route opens.
- [ ] Staff Home shows `今日预约` only with `reservation.today_view`.
- [ ] List renders cards.
- [ ] Empty state renders.
- [ ] Loading state is Chinese.
- [ ] `error.code` renders.
- [ ] `error.messageKey` renders.
- [ ] Copy reservationId works or degrades safely.
- [ ] `confirmed` item shows `预约到店`.
- [ ] `arrived` item shows `预约入座`.
- [ ] `seated` item shows `已入座`.
- [ ] Terminal statuses are read-only.
- [ ] No Queue / No-show / Cancellation / Calendar / Table map buttons render.

## 13. Current Round Result

This contract round created documentation only.

Created:

- `docs/backend/RESERVATION_TODAY_VIEW_CONTRACT.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_CONTRACT.md`
- `docs/backend/RESERVATION_TODAY_VIEW_VERTICAL_SLICE_CHECKLIST.md`

Not created:

- Java Controller
- Java Application Service
- Repository implementation
- API DTO
- Vue page
- API client
- Vue Router entry
- Staff Home entry
- App Gate metadata
- Flyway migration
- SQL file
- Queue
- Seating decision
- No-show
- Cancellation
- Calendar
- Table map
