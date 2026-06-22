# Queue List UI Implementation Report

## 1. Read documents

- `docs/frontend/QUEUE_LIST_UI_CONTRACT.md`
- `docs/frontend/QUEUE_LIST_UI_CHECKLIST.md`
- `docs/api/QUEUE_LIST_API_CONTRACT.md`
- `docs/api/QUEUE_LIST_API_IMPLEMENTATION_REPORT.md`
- `QueueTicketListController`
- `QueueTicketListResponse` and related DTOs
- `QueueTicketListApiMapper`
- `QueueTicketListApiErrorMapper`
- `QueueTicketListApplicationService`
- `QueueTicketListApiIntegrationTest`
- `QueueTicketListControllerTest`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- `docs/frontend/SEATING_FROM_CALLED_QUEUE_UI_VALIDATION_REPORT.md`
- `src/pages/QueueCallPage.vue`
- `src/pages/SeatingFromCalledQueuePage.vue`
- `src/api/queueCallApi.ts`
- `src/api/seatingFromCalledQueueApi.ts`
- `src/types/queueCall.ts`
- `src/types/seatingFromCalledQueue.ts`
- `src/pages/StoreStaffHomePage.vue`
- `src/router/index.ts`
- `src/pages/ReservationTodayViewPage.vue`
- Existing frontend API client, type, and mobile-first page patterns
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `AppGateRequiredPermission`
- `/api/me/apps` controller and service behavior
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`

## 2. Created / updated files

- Created `src/pages/QueueTicketListPage.vue`.
- Created `src/api/queueTicketListApi.ts`.
- Created `src/types/queueTicketList.ts`.
- Updated `src/router/index.ts`.
- Updated `src/pages/StoreStaffHomePage.vue`.
- Created `docs/frontend/QUEUE_LIST_UI_IMPLEMENTATION_REPORT.md`.

## 3. Route

- Added route `/stores/:storeId/queue-tickets`.
- Route name: `queue-ticket-list`.
- Page component: `QueueTicketListPage.vue`.
- Page title: `排队列表`.
- Existing mutation routes remain separate: `/stores/:storeId/queue-tickets/call` and `/stores/:storeId/queue-tickets/seating/direct`.

## 4. API client

- Endpoint: `GET /api/v1/stores/{storeId}/queue-tickets`.
- Query allowlist: `status`, `limit`, `offset`.
- `all` filter omits `status`.
- Request headers: `Accept: application/json`.
- Idempotency-Key: not sent.
- Request body: not sent.
- Forbidden fields are not sent: `tenantId`, query/body `storeId`, `actorId`, mutation action, `tableId`, `tableGroupId`, `skipReason`, `rejoinReason`, or status update fields.
- Error responses preserve and surface raw `error.code` and `error.messageKey`; frontend fallback uses `UNKNOWN_ERROR` with `queue.list.unknown_error`.

## 5. Types

- Added `QueueTicketListQuery`.
- Added `QueueTicketListResponse`.
- Added `QueueTicketListItem`.
- Added `QueueTicketListPage`.
- Added `QueueTicketListApiErrorResponse`.
- Item fields match the API response: `queueTicketId`, `queueTicketNumber`, `queueTicketStatus`, `partySize`, `partySizeGroup`, `reservationId`, `reservationCode`, `reservationStatus`, `customerName`, `customerPhoneMasked`, `createdAt`, `calledAt`, `holdUntilAt`, and `expiresAt`.

## 6. Staff Home integration

- Added Staff Home entry label `排队列表`.
- Entry routes to `/stores/:storeId/queue-tickets`.
- Entry description is read-only: `查看等待、已叫号和已入座的排队票`.
- Added handoff note: `排队列表只读展示排队票，不执行叫号或入座。`
- Did not add Queue Workbench, Display, Skip, Rejoin, Table Map, Auto assignment, No-show, or Cancellation entries.

## 7. Permission display rule

- Staff Home displays `排队列表` only when the visible `reservation_queue` app entry permissions contain `queue.view`.
- Frontend permission logic is display control only.
- Backend Queue List API and App Gate remain final authorization.
- Confirmed `queue.view` is included in `reservation_queue` permission metadata by the completed API/App Gate reports.

## 8. Filter behavior

- Primary filters: `全部`, `等待中`, `已叫号`, `已入座`.
- `全部` maps to no `status` query param.
- `等待中` maps to `status=waiting`.
- `已叫号` maps to `status=called`.
- `已入座` maps to `status=seated`.
- Changing the filter resets `offset` to `0`.
- API-supported exception/terminal statuses remain type-supported but are not exposed as V1 main filter buttons.

## 9. Pagination behavior

- Default `limit = 50`.
- Default `offset = 0`.
- Previous page sets `offset = max(0, offset - page.limit)`.
- Next page increments by `page.limit` only when more rows are available.
- Refresh reuses current `status`, `limit`, and `offset`.
- Effective `limit`, `offset`, and `total` are displayed.
- Infinite scroll was not implemented.

## 10. List item display

- Each card displays `queueTicketNumber`, `queueTicketStatus`, `partySize`, `partySizeGroup`, `reservationCode`, `reservationStatus`, `customerName`, `customerPhoneMasked`, `createdAt`, `calledAt`, and `holdUntilAt`.
- Debug fields `queueTicketId`, `reservationId`, and `expiresAt` are visually secondary.
- Queue number is visually prominent.
- Raw `queueTicketStatus` is displayed as `状态代码`.
- Called tickets emphasize `holdUntilAt`.
- No Queue Call, Queue Seat, Skip, Rejoin, Display, Table map, Auto assignment, No-show, or Cancellation controls are present.

## 11. Time display

- `createdAt`, `calledAt`, `holdUntilAt`, and `expiresAt` are shown as readable date/time values.
- The page uses `Intl.DateTimeFormat` with `zh-CN` and `Asia/Singapore`, matching the current Singapore default where the Queue List API does not return store timezone.
- Missing nullable timestamps display `未返回`.

## 12. Error display

- Error panel displays `error.code`.
- Error panel displays `error.messageKey`.
- The UI does not translate or replace the backend `messageKey`.
- Actual backend codes such as `INVALID_STATUS`, `INVALID_LIMIT`, `INVALID_OFFSET`, `FORBIDDEN`, and `STORE_SCOPE_MISMATCH` are displayed raw.
- App Gate denial codes and Product Owner category aliases are displayed raw if returned by the API or fixtures.
- Network, invalid response, and unclassified failures use `UNKNOWN_ERROR`.

## 13. Mobile-first handling

- Single-column page shell.
- Top title displays `排队列表`.
- Status filters are compact buttons.
- Queue tickets render as cards, not a table.
- Queue number and status are prominent.
- Reservation information is secondary.
- Called `holdUntilAt` is highlighted.
- Loading, empty, and error states are explicit.
- Pagination controls are simple: `上一页`, `刷新`, `下一页`.
- Long IDs, codes, and message keys wrap within cards.

## 14. Commands executed

```powershell
$missing = @(); if (-not (Test-Path 'src/pages/QueueTicketListPage.vue')) { $missing += 'QueueTicketListPage.vue missing' }; if (-not (Test-Path 'src/api/queueTicketListApi.ts')) { $missing += 'queueTicketListApi.ts missing' }; if (-not (Test-Path 'src/types/queueTicketList.ts')) { $missing += 'queueTicketList.ts missing' }; $router = Get-Content -Raw 'src/router/index.ts'; if ($router -notmatch '/stores/:storeId/queue-tickets''') { $missing += 'queue ticket list route missing' }; if ($missing.Count -gt 0) { $missing; exit 1 }
```

Result before implementation:

```text
QueueTicketListPage.vue missing
queueTicketListApi.ts missing
queueTicketList.ts missing
queue ticket list route missing
```

Planned final verification:

```bash
npm run build
```

```powershell
$required = @('src/pages/QueueTicketListPage.vue','src/api/queueTicketListApi.ts','src/types/queueTicketList.ts','docs/frontend/QUEUE_LIST_UI_IMPLEMENTATION_REPORT.md'); $missing = $required | Where-Object { -not (Test-Path $_) }; if ($missing.Count) { $missing; exit 1 }; $api = Get-Content -Raw 'src/api/queueTicketListApi.ts'; $forbidden = @('Idempotency-Key','body:','tenantId','actorId','tableId','tableGroupId','skipReason','rejoinReason'); foreach ($pattern in $forbidden) { if ($api.Contains($pattern)) { "forbidden API token: $pattern"; exit 1 } }; $page = Get-Content -Raw 'src/pages/QueueTicketListPage.vue'; $forbiddenPage = @('Queue Skip','Queue Rejoin','Queue Display','Table map','Auto assignment','过号处理','重新入队','叫号屏','桌位图','自动分桌'); foreach ($pattern in $forbiddenPage) { if ($page.Contains($pattern)) { "forbidden page token: $pattern"; exit 1 } }; 'content boundary validation passed'
```

## 15. Build / test result

- `npm run build`: passed (`vue-tsc --noEmit` and `vite build`; 77 modules transformed).
- Content boundary validation: passed.
- `mvn test`: not run because no backend code was modified.

## 16. Boundary check

- Queue Workbench UI created: No.
- Queue Skip UI created: No.
- Queue Rejoin UI created: No.
- Queue Display UI created: No.
- Queue Call mutation from list: No.
- Queue Seat mutation from list: No.
- Table map created: No.
- Auto assignment UI created: No.
- No-show UI created: No.
- Cancellation UI created: No.
- Cleaning UI created: No.
- Turnover UI created: No.
- Backend API changed: No.
- App Gate metadata changed: No.
- Migration changed: No.
- SQL changed: No.
- Production config changed: No.
- Production database touched: No.
- Seed data inserted: No.

## 17. Open Questions

- The Queue List API response does not currently include store timezone. V1 uses the Singapore default `Asia/Singapore`; a later store-locale slice can replace this with store-provided timezone context if needed.

## 18. Next step recommendation

- Run frontend build verification and then perform browser-level route validation with representative API fixtures or a local backend.
- Keep Queue Skip, Queue Rejoin, Queue Display, Queue Workbench, Queue Call from list, Queue Seat from list, Table map, Auto assignment, No-show, Cancellation, backend API, App Gate metadata, and migration work in separate approved rounds.
