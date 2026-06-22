# Queue List UI Validation Report

## 1. Read documents

- `docs/frontend/QUEUE_LIST_UI_CONTRACT.md`
- `docs/frontend/QUEUE_LIST_UI_CHECKLIST.md`
- `docs/frontend/QUEUE_LIST_UI_IMPLEMENTATION_REPORT.md`
- `src/pages/QueueTicketListPage.vue`
- `src/api/queueTicketListApi.ts`
- `src/types/queueTicketList.ts`
- `src/router/index.ts`
- `src/pages/StoreStaffHomePage.vue`
- `docs/api/QUEUE_LIST_API_CONTRACT.md`
- `docs/api/QUEUE_LIST_API_IMPLEMENTATION_REPORT.md`
- `QueueTicketListController`
- `QueueTicketListResponse`
- `QueueTicketListApiMapper`
- `QueueTicketListApiErrorMapper`
- `QueueTicketListApplicationService`
- `QueueTicketListApiIntegrationTest`
- `QueueTicketListControllerTest`
- `QueueTicketListLocalRuntimeSecurityTest`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- `docs/frontend/SEATING_FROM_CALLED_QUEUE_UI_VALIDATION_REPORT.md`
- `src/pages/QueueCallPage.vue`
- `src/pages/SeatingFromCalledQueuePage.vue`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `AppGateRequiredPermission`
- `/api/me/apps` controller and service
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

## 2. Validation environment

- Frontend:
  - Full-permission browser validation used existing Vite server `http://127.0.0.1:5178`, proxied to backend `8080`.
  - No-`queue.view` Staff Home validation used existing Vite server `http://127.0.0.1:5179`, proxied to backend `8081`.
  - Empty-state validation used Vite server `http://127.0.0.1:5180`, proxied to backend `8082`.
  - Backend-unavailable error validation used Vite server `http://127.0.0.1:5181`, proxied to unused port `65500`.
- Backend:
  - Existing local Spring Boot jar on `http://127.0.0.1:8080` with `queue.view`.
  - Additional local Spring Boot jar on `http://127.0.0.1:8081` without `queue.view`.
  - Additional local Spring Boot jar on `http://127.0.0.1:8082` with `queue.view` for the empty store.
  - All Spring Boot runtimes used `spring.profiles.active=local`, `rpb.local-auth.enabled=true`, and `spring.flyway.enabled=false`.
- Database:
  - Local temporary PostgreSQL 17 on `127.0.0.1:50443`.
  - No production database was used.
  - Local-only validation fixtures were inserted before read-only baseline capture; no SQL file or migration was created.
- Auth:
  - Full actor store `20000000-0000-0000-0000-000000000983` included `queue.view`.
  - No-view actor store `20000000-0000-0000-0000-000000000983` included `reservation.create` and `queue.call`, but not `queue.view`.
  - Empty-store actor store `20000000-0000-0000-0000-000000000984` included `queue.view`.

## 3. Route validation

- Opened in a real browser:
  - `/stores/20000000-0000-0000-0000-000000000983/queue-tickets`
- Page title rendered:
  - `排队列表`
- Initial page summary rendered:
  - `1-50 / 64`
  - `limit 50 / offset 0 / total 64`
- Page used mobile-size viewport `390x844`.

## 4. Staff Home validation

- With `queue.view`, Staff Home displayed `排队列表`.
- The `排队列表` link resolved to:
  - `/stores/20000000-0000-0000-0000-000000000983/queue-tickets`
- Without `queue.view`, Staff Home hid `排队列表`.
- Staff Home did not render `排队工作台`, `叫号屏`, `过号处理`, `重新入队`, `自动分桌`, or `桌位图`.

## 5. Permission display validation

- `/api/me/apps` with `queue.view` returned `reservation_queue.permissions` containing `queue.view`.
- `/api/me/apps` without `queue.view` returned `reservation_queue.permissions` containing `reservation.create` and `queue.call`, but not `queue.view`.
- Frontend permission display followed the returned permission set.
- Backend Queue List API plus App Gate remain the final authorization source.

## 6. API client validation

- Endpoint:
  - `GET /api/v1/stores/{storeId}/queue-tickets`
- Query allowlist:
  - `status`
  - `limit`
  - `offset`
- `all` filter:
  - Omits `status`.
- `Idempotency-Key`:
  - Not sent.
- Request body:
  - Not sent.
- Forbidden fields excluded:
  - `tenantId`
  - query/body `storeId`
  - `actorId`
  - mutation action
  - `tableId`
  - `tableGroupId`
  - `skipReason`
  - `rejoinReason`
  - status update fields
- Source-level API boundary validation passed.

## 7. Filter validation

- All:
  - Selected `全部`.
  - Displayed mixed `waiting`, `called`, and `seated` cards.
  - Page summary: `1-50 / 64`, `limit 50 / offset 0 / total 64`.
- Waiting:
  - Selected `等待中`.
  - Displayed only `状态代码：waiting`.
  - Page summary: `1-50 / 57`, `limit 50 / offset 0 / total 57`.
  - Fixture `R-LIST-UI-WAITING` was visible.
- Called:
  - Selected `已叫号`.
  - Displayed only `状态代码：called`.
  - Page summary: `1-1 / 1`, `limit 50 / offset 0 / total 1`.
  - Fixture `R-LIST-UI-CALLED` was visible.
- Seated:
  - Selected `已入座`.
  - Displayed only `状态代码：seated`.
  - Page summary: `1-6 / 6`, `limit 50 / offset 0 / total 6`.
  - Fixture `R-LIST-UI-SEATED` was visible.
- Filter reset:
  - After navigating to `offset 50`, selecting `已叫号` reset offset to `0`.

## 8. Pagination validation

- Default:
  - `limit 50 / offset 0 / total 64`.
  - `上一页` disabled.
  - `下一页` enabled.
- Next page:
  - Clicking `下一页` displayed `51-64 / 64`.
  - Effective offset became `50`.
- Refresh:
  - Clicking `刷新` on page 2 kept `offset 50`.
- Previous page:
  - Clicking `上一页` returned to `1-50 / 64`.
  - Effective offset became `0`.
- Offset did not go below `0`.
- No infinite scroll or workbench pagination behavior was introduced.

## 9. Success display result

- Waiting:
  - Displayed `#30`, `queueTicketStatus=waiting`, `partySize=4`, `partySizeGroup=3-4`.
  - Displayed `reservationCode=R-LIST-UI-WAITING`, `reservationStatus=arrived`.
  - Displayed `customerName=Queue List Waiting Guest`, `customerPhoneMasked=****0990`.
  - Displayed readable `createdAt=06-20 10:00`.
- Called:
  - Displayed `#31`, `queueTicketStatus=called`, `partySize=4`, `partySizeGroup=3-4`.
  - Displayed `reservationCode=R-LIST-UI-CALLED`, `reservationStatus=arrived`.
  - Displayed `customerName=Queue List Called Guest`, `customerPhoneMasked=****0991`.
  - Displayed readable `createdAt=06-20 10:01`, `calledAt=06-20 12:00`, and `holdUntilAt=06-20 12:03`.
  - `holdUntilAt` was later than `calledAt` and was emphasized in the called card.
- Seated:
  - Displayed `#32`, `queueTicketStatus=seated`, `partySize=4`, `partySizeGroup=3-4`.
  - Displayed `reservationCode=R-LIST-UI-SEATED`, `reservationStatus=seated`.
  - Displayed `customerName=Queue List Seated Guest`, `customerPhoneMasked=****0992`.
  - Displayed readable `createdAt=06-20 10:02`.

## 10. Empty state result

- Opened empty store:
  - `/stores/20000000-0000-0000-0000-000000000984/queue-tickets`
- UI displayed:
  - `暂无排队票`
  - `0 / 0`
  - `limit 50 / offset 0 / total 0`
- No cards were rendered.
- No error panel was rendered.
- Loading did not hang.

## 11. Error display result

- Error scenario:
  - Backend unavailable through `http://127.0.0.1:5181`.
- UI displayed:
  - Heading: `加载失败`
  - `错误代码：UNKNOWN_ERROR`
  - `消息键：queue.list.unknown_error`
- The UI showed raw `error.code` and `error.messageKey`; it did not replace the message key with hardcoded business copy.

## 12. Database assertions

Baseline after local fixture setup and before Queue List UI reads:

```text
queue_status|called|1
queue_status|seated|6
queue_status|waiting|57
table_status|occupied|6
audit_logs|5
business_events|16
cleanings|0
idempotency_records|6
seating_resources|5
seatings|5
state_transition_logs|17
turnovers|0
```

After browser Queue List reads and refresh:

```text
queue_status|called|1
queue_status|seated|6
queue_status|waiting|57
table_status|occupied|6
audit_logs|5
business_events|16
cleanings|0
idempotency_records|6
seating_resources|5
seatings|5
state_transition_logs|17
turnovers|0
```

Detailed assertions:

- QueueTicket:
  - Fixture `990` remained `waiting`.
  - Fixture `991` remained `called`, `calledAt=2030-06-20 12:00:00+08`, `expiresAt=2030-06-20 12:03:00+08`.
  - Fixture `992` remained `seated`.
- Reservation:
  - Reservation `990` remained `arrived`.
  - Reservation `991` remained `arrived`.
  - Reservation `992` remained `seated`.
- Table:
  - `dining_tables` status counts remained `occupied=6`.
- Seating:
  - Count remained `5`.
- SeatingResource:
  - Count remained `5`.
- BusinessEvent:
  - Count remained `16`.
- StateTransitionLog:
  - Count remained `17`.
- AuditLog:
  - Business `audit_logs` count remained `5`.
- IdempotencyRecord:
  - Count remained `6`.
- Cleaning:
  - Count remained `0`.
- Turnover:
  - Count remained `0`.
- Boundary:
  - No Queue Skip, Queue Rejoin, Queue Display, Queue Call, Queue Seat, Table map, Auto assignment, No-show, Cancellation, Cleaning, Turnover, backend API, App Gate metadata, migration, SQL file, production config, production database, or production seed side effect was produced.

## 13. Commands executed

```bash
npm run build
```

```bash
mvn -q "-Dtest=QueueTicketList*Test,QueueList*Test" test
```

```powershell
# API client boundary validation:
# confirmed GET, endpoint path, status/limit/offset allowlist,
# no Idempotency-Key, no body, and no forbidden mutation fields.
```

```powershell
# Local temporary PostgreSQL psql assertions:
# captured queue/reservation/table/count baselines before and after UI reads.
```

## 14. Build / test result

- `npm run build`: passed.
  - `vue-tsc --noEmit && vite build`
  - `77` modules transformed.
- Queue list tests: passed.
  - `mvn -q "-Dtest=QueueTicketList*Test,QueueList*Test" test`
- `mvn test`: not run because no backend source, API contract, App Gate metadata, persistence, migration, or business state machine code was changed.

## 15. Files changed

- Created `docs/frontend/QUEUE_LIST_UI_VALIDATION_REPORT.md`.
- No frontend source file was changed.
- No backend source file was changed.
- No migration, SQL file, database schema, or production configuration was changed.

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
- SQL file changed: No.
- Production config changed: No.
- Production database touched: No.
- Seed data inserted into production: No.

## 17. Open Questions

- The Queue List API response still does not include store timezone. The UI currently displays readable times using the Singapore default `Asia/Singapore`.
- Existing local validation runtime reused a PostgreSQL 17 temporary database with `spring.flyway.enabled=false`, consistent with prior UI validation reports.

## 18. Next step recommendation

- Accept Queue List UI V1 as validated for the read-only list scope.
- Keep Queue Skip, Queue Rejoin, Queue Display, Queue Workbench, Queue Call from list, Queue Seat from list, Table map, Auto assignment, No-show, Cancellation, Cleaning, Turnover, backend API changes, App Gate metadata changes, and migrations in separate approved rounds.
