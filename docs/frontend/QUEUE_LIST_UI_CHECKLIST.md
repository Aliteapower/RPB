# Queue List UI Checklist V1

## 1. Contract Scope

- [x] This round only creates Queue List UI contract documentation.
- [x] `docs/frontend/QUEUE_LIST_UI_CONTRACT.md` is created.
- [x] `docs/frontend/QUEUE_LIST_UI_CHECKLIST.md` is created.
- [x] No Vue page is created.
- [x] No frontend API client is created.
- [x] No frontend type file is created.
- [x] No Vue Router entry is created.
- [x] No Staff Home source change is made.
- [x] No backend API change is made.
- [x] No App Gate metadata change is made.
- [x] No migration, SQL, database structure, production config, seed data, or production data change is made.

## 2. Baseline Reads

- [x] Queue List API contract considered.
- [x] Queue List API implementation report considered.
- [x] QueueTicketListController considered.
- [x] QueueTicketListResponse and related DTOs considered.
- [x] QueueTicketListApiMapper considered.
- [x] QueueTicketListApiErrorMapper considered.
- [x] QueueTicketListApplicationService considered.
- [x] QueueTicketListApiIntegrationTest considered.
- [x] QueueTicketListControllerTest considered.
- [x] Queue List saved test reports considered.
- [x] Queue Call UI validation report considered.
- [x] Seating From Called Queue UI validation report considered.
- [x] Queue Call API implementation report considered.
- [x] Seating From Called Queue API implementation report considered.
- [x] Existing Staff Home, router, page, API client, type, and mobile-first UI patterns considered.
- [x] App Gate permission metadata alignment and `/api/me/apps` behavior considered.
- [x] Governance, architecture, and reservation-system skill documents considered.

## 3. Read-only Contract

- [x] Queue List API is read-only.
- [x] Queue List UI is read-only.
- [x] UI does not change QueueTicket status.
- [x] UI does not change Reservation status.
- [x] UI does not create Seating.
- [x] UI does not change Table status.
- [x] UI does not write BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord.
- [x] UI does not require or send `Idempotency-Key`.

## 4. Route Contract

- [x] Future route is `/stores/:storeId/queue-tickets`.
- [x] Future page is `QueueTicketListPage.vue`.
- [x] Future title is `排队列表`.
- [x] Route remains a read-only QueueTicket list.
- [x] Route is not `/stores/:storeId/queue/workbench`.
- [x] Route is not `/stores/:storeId/queue/display`.
- [x] Route is not `/stores/:storeId/table-map`.
- [x] Route is not `/stores/:storeId/queue/skip`.
- [x] Route is not `/stores/:storeId/queue/rejoin`.

## 5. Staff Home Entry

- [x] Future Staff Home entry label is `排队列表`.
- [x] Future Staff Home entry target is `/stores/:storeId/queue-tickets`.
- [x] Entry appears only for visible/enabled `reservation_queue`.
- [x] Entry appears only when permissions contain `queue.view`.
- [x] Entry is hidden when `reservation_queue` is missing.
- [x] Entry is hidden when `reservation_queue.entryVisible` is false.
- [x] Entry is hidden when `queue.view` is missing.
- [x] Entry is display-only and does not authorize the backend API.
- [x] Staff Home must not add `排队工作台`.
- [x] Staff Home must not add `叫号屏`.
- [x] Staff Home must not add `过号处理`.
- [x] Staff Home must not add `重新入队`.
- [x] Staff Home must not add `自动分桌`.
- [x] Staff Home must not add `桌位图`.

## 6. Permission Contract

- [x] UI permission key is `queue.view`.
- [x] App key remains `reservation_queue`.
- [x] Frontend permission rule is only a display hint.
- [x] Backend Queue List API plus App Gate remain final authorization.
- [x] Backend gate remains `@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")`.
- [x] No new app key is designed.
- [x] No new permission model is designed.
- [x] Contract does not modify App Gate metadata.

## 7. API Call Contract

- [x] Future UI calls `GET /api/v1/stores/{storeId}/queue-tickets`.
- [x] `storeId` comes from the route path.
- [x] Query params are limited to `status`, `limit`, and `offset`.
- [x] `status` is optional.
- [x] `limit` is optional and defaults to `50`.
- [x] `offset` is optional and defaults to `0`.
- [x] `all` filter omits `status`.
- [x] Request does not send `Idempotency-Key`.
- [x] Request does not send a body.
- [x] Request does not send `tenantId`.
- [x] Request does not send `storeId` in query or body.
- [x] Request does not send `actorId`.
- [x] Request does not send mutation action.
- [x] Request does not send `tableId`.
- [x] Request does not send `tableGroupId`.
- [x] Request does not send `skipReason`.
- [x] Request does not send `rejoinReason`.
- [x] Request does not send status update fields.

## 8. Filter Contract

- [x] Primary filter option `all` is defined.
- [x] Primary filter option `waiting` is defined.
- [x] Primary filter option `called` is defined.
- [x] Primary filter option `seated` is defined.
- [x] `all` maps to no `status` query param.
- [x] `waiting` maps to `status=waiting`.
- [x] `called` maps to `status=called`.
- [x] `seated` maps to `status=seated`.
- [x] API-supported optional statuses `skipped`, `rejoined`, `expired`, and `cancelled` are documented as optional read-only filters.
- [x] UI must not invent statuses unsupported by the API.
- [x] Filter changes reset `offset` to `0`.

## 9. Pagination Contract

- [x] Default `limit = 50`.
- [x] Default `offset = 0`.
- [x] Backend maximum `limit = 100` is documented.
- [x] Previous page behavior is defined.
- [x] Next page behavior is defined.
- [x] Refresh behavior is defined.
- [x] Effective `page.limit`, `page.offset`, and `page.total` display is defined.
- [x] Infinite scroll is not part of V1.
- [x] Client-controlled sorting is not part of V1.

## 10. List Item Display

- [x] Display `queueTicketNumber`.
- [x] Display `queueTicketStatus`.
- [x] Display `partySize`.
- [x] Display `partySizeGroup`.
- [x] Display `reservationCode`.
- [x] Display `reservationStatus`.
- [x] Display `customerName`.
- [x] Display `customerPhoneMasked`.
- [x] Display `createdAt`.
- [x] Display `calledAt`.
- [x] Display `holdUntilAt`.
- [x] Optional weak display of `queueTicketId` is allowed.
- [x] Optional weak display of `reservationId` is allowed.
- [x] Optional weak display of `expiresAt` is allowed.
- [x] `queueTicketNumber` is visually prominent.
- [x] `holdUntilAt` is emphasized for called tickets.
- [x] Raw phone number is not displayed or reconstructed.

## 11. Error Display

- [x] Error display must show `error.code`.
- [x] Error display must show `error.messageKey`.
- [x] UI must not replace `messageKey` with hardcoded business copy.
- [x] Actual backend `INVALID_STATUS` is covered.
- [x] Actual backend `INVALID_LIMIT` is covered.
- [x] Actual backend `INVALID_OFFSET` is covered.
- [x] Product Owner category `QUEUE_LIST_INVALID_STATUS` is covered as a display category or fixture alias.
- [x] Product Owner category `QUEUE_LIST_INVALID_LIMIT` is covered as a display category or fixture alias.
- [x] Product Owner category `QUEUE_LIST_INVALID_OFFSET` is covered as a display category or fixture alias.
- [x] `APP_GATE_DENIED` is covered as an operational denial category.
- [x] Actual App Gate denial codes `TENANT_APP_NOT_ENABLED`, `STORE_APP_NOT_ENABLED`, and `PERMISSION_DENIED` are covered.
- [x] `FORBIDDEN` is covered.
- [x] `STORE_SCOPE_MISMATCH` is covered.
- [x] `UNKNOWN_ERROR` fallback is covered.

## 12. Mobile-first Contract

- [x] Single-column layout.
- [x] Top header shows `排队列表`.
- [x] Store context remains visible.
- [x] Status filter is simple and clear.
- [x] Queue tickets render as cards, not a complex table.
- [x] Queue number is prominent.
- [x] Status is obvious.
- [x] Reservation information is secondary.
- [x] `holdUntilAt` is emphasized for called tickets.
- [x] Loading state is clear.
- [x] Empty state is clear.
- [x] Error state is clear.
- [x] Previous page, next page, and refresh controls are simple.
- [x] Long IDs, codes, and message keys wrap.
- [x] No table map.
- [x] No drag-and-drop.
- [x] No Queue Display screen.

## 13. Future Test Contract

- [x] Route tests defined.
- [x] Staff Home permission tests defined.
- [x] API method/path/query tests defined.
- [x] Forbidden field tests defined.
- [x] Display tests for waiting ticket defined.
- [x] Display tests for called ticket defined.
- [x] Display tests for seated ticket defined.
- [x] Filter tests defined.
- [x] Pagination tests defined.
- [x] Empty state test defined.
- [x] Error display tests defined.
- [x] Boundary tests defined.

## 14. Boundary Checklist

- [x] Queue List UI implemented: No.
- [x] Queue Workbench designed: No.
- [x] Queue Skip designed: No.
- [x] Queue Rejoin designed: No.
- [x] Queue Display designed: No.
- [x] Queue Call mutation designed: No.
- [x] Queue Seat mutation designed: No.
- [x] Table map designed: No.
- [x] Auto assignment designed: No.
- [x] No-show designed: No.
- [x] Cancellation designed: No.
- [x] Cleaning designed: No.
- [x] Turnover designed: No.
- [x] Mutation fields sent: No.
- [x] Uses `queue.view` permission rule: Yes.
- [x] Backend App Gate remains final authorization: Yes.
- [x] Backend API changed: No.
- [x] App Gate metadata changed: No.
- [x] Migration changed: No.
- [x] SQL file changed: No.
- [x] Database structure changed: No.
- [x] Production config changed: No.
- [x] Production database touched: No.
- [x] Production seed data inserted: No.

## 15. Final Gate

- [x] Queue List UI contract is created.
- [x] Queue List UI checklist is created.
- [x] Route contract is clear.
- [x] Staff Home permission rule is clear.
- [x] API call contract is clear.
- [x] Filter contract is clear.
- [x] Pagination contract is clear.
- [x] List item display is clear.
- [x] Error display is clear.
- [x] Mobile-first rule is clear.
- [x] Future implementation test contract is clear.
- [x] No Vue implementation is included.
- [x] No frontend API client implementation is included.
- [x] No router change is included.
- [x] No Staff Home change is included.
- [x] No backend API change is included.
- [x] No App Gate metadata change is included.
- [x] No migration change is included.
- [x] No Queue Skip, Rejoin, Display, Call mutation, Seat mutation, Table map, Auto assignment, No-show, or Cancellation is included.

Next recommended gate:

```text
Queue List UI Implementation
```

Entry constraints for the next gate:

- Product Owner accepts this contract.
- Next implementation remains frontend-only unless a later request explicitly opens backend API, App Gate metadata, migration, Queue Skip, Queue Rejoin, Queue Display, Queue Call mutation from the list, Queue Seat mutation from the list, Table map, Auto assignment, No-show, Cancellation, Cleaning, Turnover, or production data changes.
