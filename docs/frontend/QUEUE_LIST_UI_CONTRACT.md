# Queue List UI Contract V1

## 1. Purpose

Define the future Store Staff UI contract for reading QueueTicket list data from the completed Queue List Read API.

Target flow:

```text
Store staff opens Queue List page
-> UI reads storeId from the route
-> UI calls GET /api/v1/stores/{storeId}/queue-tickets
-> UI sends optional status, limit, and offset query params only
-> UI displays waiting, called, seated, and other API-supported QueueTicket summaries
-> UI does not change QueueTicket, Reservation, Seating, Table, Cleaning, or Turnover state
```

This document is contract only. It does not create a Vue page, route, API client, type file, backend API, App Gate metadata, migration, SQL, seed data, production configuration, runtime fixture data, or production database data.

## 2. Scope

In scope for a future minimum UI:

- Read-only QueueTicket list page.
- Status filter.
- `limit` / `offset` pagination.
- Refresh action.
- Queue ticket summary display.
- Reservation summary display.
- `customerPhoneMasked` display.
- `calledAt`, `holdUntilAt`, and `expiresAt` display where returned.
- Loading, empty, and error states.
- Error display using raw `error.code` and `error.messageKey`.
- Staff Home entry rule for a later UI implementation.
- Permission display rule using `queue.view`.
- Mobile-first layout rules.
- Future implementation test contract.

## 3. Non-Scope

Out of scope:

- Queue Workbench.
- Queue Skip action.
- Queue Rejoin action.
- Queue Display screen.
- Queue Call button or mutation from this list.
- Queue Seat button or mutation from this list.
- Table map.
- Drag-and-drop table layout.
- Auto assignment.
- Recommended table.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.
- Reservation mutation.
- QueueTicket status mutation.
- Backend API changes.
- App Gate metadata changes.
- Migration changes.
- SQL changes.
- Database structure changes.
- Production database access.
- Production seed data.

## 4. Baseline Confirmation

This contract is based on the completed Queue List Read API slice:

- Endpoint: `GET /api/v1/stores/{storeId}/queue-tickets`.
- App Gate: `app_key = reservation_queue`, `permission = queue.view`.
- API guard: `@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")`.
- API is read-only.
- API has no request body.
- API does not require `Idempotency-Key`.
- API accepts only optional `status`, `limit`, and `offset` query params.
- API response includes `items` and `page`.
- `holdUntilAt` is mapped from `queue_tickets.expires_at`.
- Queue List tests passed according to the implementation report and local surefire reports.
- `queue.view` is registered in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`.

The UI must consume the existing API contract and must not redefine backend state machines or permission metadata.

## 5. Route Contract

Future route:

```text
/stores/:storeId/queue-tickets
```

Future page:

```text
QueueTicketListPage.vue
```

Future page title:

```text
排队列表
```

Rationale:

- V1 is a read-only QueueTicket list.
- The route is resource-based and matches the completed read API path.
- It is not a queue workbench.
- It is not a Queue Display screen.
- It does not perform Queue Skip, Queue Rejoin, Queue Call, Queue Seat, table map, or auto assignment behavior.

Do not design these routes for V1:

```text
/stores/:storeId/queue/workbench
/stores/:storeId/queue/display
/stores/:storeId/table-map
/stores/:storeId/queue/skip
/stores/:storeId/queue/rejoin
```

Existing related routes remain separate flows:

```text
/stores/:storeId/queue-tickets/call
/stores/:storeId/queue-tickets/seating/direct
```

The future Queue List page must not merge those mutation flows into the list.

## 6. Staff Home Entry Rule

This contract does not modify Staff Home.

In a future UI implementation, Staff Home may add:

```text
排队列表
```

Route target:

```text
/stores/:storeId/queue-tickets
```

Recommended description:

```text
查看等待、已叫号和已入座的排队票
```

Display condition:

```text
reservation_queue app is visible and enabled for the store
and
reservation_queue permissions contains queue.view
```

Do not show the entry when:

- `/api/me/apps` does not return `reservation_queue`.
- `reservation_queue.entryVisible` is false.
- `reservation_queue.permissions` does not contain `queue.view`.

Staff Home must not add these entries in this UI slice:

```text
排队工作台
叫号屏
过号处理
重新入队
自动分桌
桌位图
```

## 7. Permission Rule

UI visibility rule:

```text
Show Queue List entry only when actor permissions include queue.view.
Hide Queue List entry when actor permissions do not include queue.view.
```

Security rule:

```text
Frontend permission checks are display hints only.
Backend Queue List API plus App Gate remain the final authorization source.
```

Backend gate remains:

```text
@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")
```

`/api/me/apps` V1 remains an app-entry source. The current `permissions` field may be used for this approved Staff Home display rule, but it must not be treated as a complete capability matrix for unapproved Queue Skip, Rejoin, Display, Call, Seat, Table map, No-show, or Cancellation controls.

## 8. API Call Contract

Endpoint:

```http
GET /api/v1/stores/{storeId}/queue-tickets
```

Sources:

| Value | UI/API source |
| --- | --- |
| `storeId` | Route path `/stores/:storeId/queue-tickets` |
| `status` | Optional filter query param |
| `limit` | Optional pagination query param |
| `offset` | Optional pagination query param |

Headers:

```text
Accept: application/json
```

Do not send:

```text
Idempotency-Key
Content-Type for a body
request body
tenantId
storeId in query/body
actorId
mutation action
tableId
tableGroupId
skipReason
rejoinReason
status update
```

Query params may contain only:

```text
status
limit
offset
```

The UI must not send blank query params when a value is intentionally absent. For the `all` filter, omit `status` instead of sending `status=all`, because the completed API accepts enum status codes and treats absent status as all statuses.

## 9. Filter Contract

Primary V1 status filter options:

```text
all
waiting
called
seated
```

Mapping:

| UI option | API query |
| --- | --- |
| `all` | omit `status` |
| `waiting` | `status=waiting` |
| `called` | `status=called` |
| `seated` | `status=seated` |

The completed backend enum also supports:

```text
skipped
rejoined
expired
cancelled
```

The future UI may expose those additional read-only filters only if the implementation intentionally supports them and labels them as list filters. The UI must not invent statuses not supported by the API.

Changing the status filter must reset:

```text
offset = 0
```

The filter must not trigger a mutation and must not call Queue Skip, Queue Rejoin, Queue Call, Queue Seat, Display, Table map, or Auto assignment APIs.

## 10. Pagination Contract

Defaults:

```text
limit = 50
offset = 0
```

Backend maximum:

```text
limit = 100
```

V1 controls:

```text
上一页
下一页
刷新
```

Rules:

- `limit` defaults to `50`.
- `offset` defaults to `0`.
- Previous page sets `offset = max(0, offset - limit)`.
- Next page sets `offset = offset + limit`.
- Refresh reuses the current `status`, `limit`, and `offset`.
- Previous is disabled when `offset <= 0`.
- Next may be disabled when `offset + items.length >= page.total`.
- The UI should display effective `page.limit`, `page.offset`, and `page.total`.
- Do not add infinite scroll in V1.
- Do not add client-controlled sorting in V1.

Backend sorting remains fixed:

```text
createdAt asc
queueTicketNumber asc
```

## 11. List Item Display Contract

Each QueueTicket card must display at least:

```text
queueTicketNumber
queueTicketStatus
partySize
partySizeGroup
reservationCode
reservationStatus
customerName
customerPhoneMasked
createdAt
calledAt
holdUntilAt
```

Optional debug display:

```text
queueTicketId
reservationId
expiresAt
```

ID fields should be visually secondary and may use smaller or monospace styling for debugging. They must not dominate the operational card.

Priority display:

```text
queueTicketNumber
queueTicketStatus
partySize / partySizeGroup
reservationCode
holdUntilAt for called tickets
customerPhoneMasked
```

Display notes:

- `queueTicketNumber` is the strongest visual element.
- `queueTicketStatus` must be obvious and must show the raw status code.
- `partySize` and `partySizeGroup` must be visible for operational triage.
- Reservation data is secondary context, not a Reservation mutation surface.
- `customerPhoneMasked` must be displayed as returned by the API; raw phone numbers must not be reconstructed.
- For `called` tickets, `holdUntilAt` must be visually emphasized.
- `calledAt`, `holdUntilAt`, `expiresAt`, and `createdAt` are ISO8601 API values. The future implementation may additionally format for Store timezone, but it must preserve the returned value or avoid misleading timezone display.

The list card must not include:

```text
Queue Skip button
Queue Rejoin button
Queue Display control
Queue Call button
Queue Seat button
Table map control
Auto assignment control
No-show control
Cancellation control
Cleaning control
```

## 12. Error Display Contract

The future UI must display:

```text
error.code
error.messageKey
```

The UI must not replace `messageKey` with hardcoded Chinese business copy. It may show fixed field labels such as `错误代码` and `消息键`, but the backend `messageKey` value itself must remain visible.

Actual Queue List API errors to support:

```text
INVALID_QUERY
INVALID_STATUS
INVALID_LIMIT
INVALID_OFFSET
STORE_NOT_FOUND
STORE_SCOPE_MISMATCH
FORBIDDEN
PERSISTENCE_ERROR
TENANT_APP_NOT_ENABLED
STORE_APP_NOT_ENABLED
PERMISSION_DENIED
UNKNOWN_ERROR
```

Minimum Product Owner display categories:

```text
QUEUE_LIST_INVALID_STATUS
QUEUE_LIST_INVALID_LIMIT
QUEUE_LIST_INVALID_OFFSET
APP_GATE_DENIED
FORBIDDEN
STORE_SCOPE_MISMATCH
UNKNOWN_ERROR
```

Compatibility notes:

| Code or category | Display behavior |
| --- | --- |
| `INVALID_STATUS` | Actual backend code for invalid status. Display raw `error.code` and `error.messageKey`. |
| `INVALID_LIMIT` | Actual backend code for invalid limit. Display raw `error.code` and `error.messageKey`. |
| `INVALID_OFFSET` | Actual backend code for invalid offset. Display raw `error.code` and `error.messageKey`. |
| `QUEUE_LIST_INVALID_STATUS` | Product Owner category or fixture alias. If present in tests or mocks, display it without translating or normalizing away the raw code. |
| `QUEUE_LIST_INVALID_LIMIT` | Product Owner category or fixture alias. If present in tests or mocks, display it without translating or normalizing away the raw code. |
| `QUEUE_LIST_INVALID_OFFSET` | Product Owner category or fixture alias. If present in tests or mocks, display it without translating or normalizing away the raw code. |
| `APP_GATE_DENIED` | Operational denial category and audit action. If present in a response or fixture, display it like any other code. |
| `TENANT_APP_NOT_ENABLED`, `STORE_APP_NOT_ENABLED`, `PERMISSION_DENIED` | Actual App Gate denial response codes observed in Queue List integration tests. Display raw code and message key. |
| `FORBIDDEN` | Controller or authorization fallback. Display raw code and message key. |
| `STORE_SCOPE_MISMATCH` | Store access mismatch. Display raw code and message key. |
| `UNKNOWN_ERROR` | Frontend fallback for network failure, invalid response shape, or unclassified response. |

## 13. Mobile-first Rules

The future Queue List page must be mobile-first:

- Single-column layout.
- Top header shows `排队列表`.
- Store context remains visible.
- Status filter is simple and clear.
- Queue tickets render as cards, not a complex table.
- Queue number is prominent.
- Status is obvious.
- Reservation information is secondary.
- `holdUntilAt` is emphasized for `called` tickets.
- Loading state is clear and does not hide the page title.
- Empty state is clear when no items are returned.
- Error state clearly shows `error.code` and `error.messageKey`.
- Previous page, next page, and refresh controls are simple and thumb-friendly.
- Long IDs, codes, and message keys wrap without clipping.
- Text must fit within cards and controls on mobile.
- Do not add a desktop back-office table as the primary V1 layout.
- Do not add table map, drag-and-drop, Queue Display, Queue Skip, Queue Rejoin, Queue Call mutation, Queue Seat mutation, Auto assignment, No-show, or Cancellation UI.

## 14. Future Implementation Test Contract

Route tests:

- `/stores/:storeId/queue-tickets` opens the future page.
- Page title renders `排队列表`.
- Route does not use `/stores/:storeId/queue/workbench`, `/queue/display`, `/queue/skip`, `/queue/rejoin`, or `/table-map`.

Staff Home tests:

- Staff Home shows `排队列表` when `reservation_queue` is visible and permissions contains `queue.view`.
- Staff Home hides `排队列表` when permissions does not contain `queue.view`.
- Staff Home hides the entry when `/api/me/apps` does not return `reservation_queue`.
- UI permission logic is display-only; backend App Gate denial still renders returned API error.
- Staff Home does not add Queue Workbench, Queue Display, Queue Skip, Queue Rejoin, Table map, Auto assignment, No-show, or Cancellation entries.

API client tests:

- Uses `GET`.
- Calls `/api/v1/stores/{storeId}/queue-tickets`.
- Query contains only `status`, `limit`, and `offset`.
- `all` filter omits `status`.
- Does not send `Idempotency-Key`.
- Does not send request body.
- Does not send forbidden fields: `tenantId`, body/query `storeId`, `actorId`, mutation action, `tableId`, `tableGroupId`, `skipReason`, `rejoinReason`, or status update fields.

Display tests:

- Displays waiting ticket.
- Displays called ticket.
- Displays seated ticket.
- Displays `queueTicketNumber`.
- Displays `queueTicketStatus`.
- Displays `partySize`.
- Displays `partySizeGroup`.
- Displays `reservationCode`.
- Displays `reservationStatus`.
- Displays `customerName`.
- Displays `customerPhoneMasked`.
- Displays `createdAt`.
- Called ticket displays and emphasizes `holdUntilAt`.
- Optional debug display may show `queueTicketId`, `reservationId`, and `expiresAt` as secondary content.

Filter tests:

- `status=waiting`.
- `status=called`.
- `status=seated`.
- `all` / no status query.
- Optional API-supported statuses are not invented if not configured in the UI.
- Changing status resets `offset` to `0`.

Pagination tests:

- Default `limit=50`.
- Default `offset=0`.
- Next page increments offset by limit.
- Previous page decrements offset but not below `0`.
- Refresh keeps current query.
- Effective `page.limit`, `page.offset`, and `page.total` display correctly.

Empty tests:

- Empty `items` displays an empty state.
- Empty state does not show mutation actions.

Error tests:

- Displays `error.code`.
- Displays `error.messageKey`.
- Does not hardcode translated business copy instead of `messageKey`.
- Handles invalid status.
- Handles invalid limit.
- Handles invalid offset.
- Handles App Gate denial codes.
- Handles `FORBIDDEN`.
- Handles `STORE_SCOPE_MISMATCH`.
- Handles unknown or network fallback as `UNKNOWN_ERROR`.

Boundary tests:

- No Queue Skip.
- No Queue Rejoin.
- No Queue Display.
- No Queue Call mutation.
- No Queue Seat mutation.
- No Table map.
- No Auto assignment.
- No No-show.
- No Cancellation.
- No backend API change.
- No App Gate metadata change.
- No migration change.

## 15. Boundary

This contract does not implement:

- `QueueTicketListPage.vue`.
- `queueTicketListApi.ts`.
- `queueTicketList.ts`.
- Vue Router changes.
- `StoreStaffHomePage.vue` changes.
- Backend Controller changes.
- Backend DTO changes.
- Application Service changes.
- App Gate metadata changes.
- Flyway migration changes.
- SQL changes.
- Database structure changes.
- Production configuration changes.
- Production database access.
- Queue Workbench.
- Queue Skip.
- Queue Rejoin.
- Queue Display.
- Queue Call mutation from list.
- Queue Seat mutation from list.
- Table map.
- Auto assignment.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.

## 16. Open Questions

- Should the future UI expose only the primary V1 filters `all`, `waiting`, `called`, and `seated`, or also expose API-supported terminal/exception statuses `skipped`, `rejoined`, `expired`, and `cancelled`?
- Should the future implementation show formatted Store-timezone timestamps next to raw ISO8601 values, or show raw API timestamps only in V1?

## 17. Next Step Recommendation

Next approved round may implement the minimum Queue List UI:

- Add `QueueTicketListPage.vue`.
- Add typed Queue List response/error types.
- Add a read-only Queue List API client.
- Add route `/stores/:storeId/queue-tickets`.
- Add Staff Home `排队列表` entry behind `queue.view`.

The implementation round must still keep Queue Workbench, Queue Skip, Queue Rejoin, Queue Display, Queue Call mutation from the list, Queue Seat mutation from the list, Table map, Auto assignment, No-show, Cancellation, backend API changes, App Gate metadata changes, migrations, SQL, and production data out of scope unless separately approved.
