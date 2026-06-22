# Queue Call UI Validation Result

## 1. Read Documents

- Read `docs/skills/reservation-system/SKILL.md`.
- Read Queue Call UI docs: `docs/frontend/QUEUE_CALL_UI_CONTRACT.md`, `docs/frontend/QUEUE_CALL_UI_CHECKLIST.md`, `docs/frontend/QUEUE_CALL_UI_IMPLEMENTATION_REPORT.md`.
- Read Queue Call API/backend docs: `docs/api/QUEUE_CALL_API_CONTRACT.md`, `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`, `docs/backend/QUEUE_CALL_APPLICATION_IMPLEMENTATION_REPORT.md`.
- Read Reservation Arrived To Queue references: `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`, `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_VALIDATION_REPORT.md`, `src/pages/ReservationArrivedToQueuePage.vue`.
- Read App Gate references: `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`, `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`, `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`, `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`.
- Read governance and architecture references: `docs/governance/BUSINESS_RULES.md`, `docs/governance/BUSINESS_GLOSSARY.md`, `docs/governance/DATA_STANDARD.md`, `docs/architecture/ARCHITECTURE.md`.
- Read runtime and fixture references: `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`, `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeCurrentActorProvider.java`, `src/main/java/com/rpb/reservation/walkin/auth/LocalAuthProperties.java`, `src/test/java/com/rpb/reservation/queue/api/QueueCallApiIntegrationTest.java`, `src/test/java/com/rpb/reservation/queue/api/QueueCallLocalRuntimeSecurityTest.java`.

## 2. Validation Environment

- Frontend:
  - Full-permission UI validation used existing Vite server at `http://127.0.0.1:5176`.
  - No-`queue.call` permission Staff Home validation used Vite server at `http://127.0.0.1:5177`, proxied to backend `8081`.
- Backend:
  - Full-permission backend ran from `target/reservation-platform-0.0.1-SNAPSHOT.jar` at `http://127.0.0.1:8080`.
  - No-`queue.call` backend ran from the same jar at `http://127.0.0.1:8081`.
  - Both used `spring.profiles.active=local` and `rpb.local-auth.enabled=true`.
- Database:
  - Local temporary PostgreSQL 17.10 on `127.0.0.1:52209`.
  - Applied `V001__reservation_platform_bootstrap.sql` and `V002__app_gate_foundation.sql` with `psql`.
  - Spring Boot runtime used `spring.flyway.enabled=false`, matching existing integration-test runtime style for PostgreSQL 17.10.
- Auth:
  - Full-permission actor had `reservation.create`, `reservation.check_in`, `reservation.queue`, `queue.call`, `reservation.today_view`, `reservation.seat`, `walkin.direct_seating.create`, `cleaning.start`, `cleaning.complete`.
  - No-`queue.call` actor had only `reservation.create` and `reservation.check_in`.
- Fixture:
  - Store: `20000000-0000-0000-0000-000000000982`.
  - Waiting ticket: `91000000-0000-0000-0000-000000000982`.
  - Already-called evidence was validated by submitting the same ticket again with a new UI-generated idempotency key.
  - Error ticket: `91000000-0000-0000-0000-000000000985`, status `skipped`.

## 3. Route Validation

- Route opened successfully:
  - `/stores/20000000-0000-0000-0000-000000000982/queue-tickets/call`
- Page title rendered:
  - `排队叫号`
- The page displayed the expected store scope:
  - `门店 20000000-0000-0000-0000-000000000982`

## 4. Staff Home Validation

- With `queue.call`, Staff Home displayed the `排队叫号` entry.
- Entry route resolved to:
  - `/stores/20000000-0000-0000-0000-000000000982/queue-tickets/call`
- Entry copy rendered:
  - `输入排队记录 ID 并执行叫号`
- Staff Home did not render Queue List, Queue Display, Queue Skip, Queue Rejoin, or Seating from Queue entries.

## 5. Permission Display

- `/api/me/apps?storeId=20000000-0000-0000-0000-000000000982` on backend `8080` returned `reservation_queue` with `queue.call`.
- Staff Home with `queue.call` showed `排队叫号`.
- `/api/me/apps?storeId=20000000-0000-0000-0000-000000000982` on backend `8081` returned only `reservation.check_in` and `reservation.create`.
- Staff Home without `queue.call` did not show `排队叫号`.
- No App Gate denial audit was produced during the permission-display validation because no protected Queue Call API was invoked by the no-permission actor.

## 6. API Client Validation

- Endpoint confirmed in `src/api/queueCallApi.ts`:
  - `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call`
- Header confirmed:
  - `Idempotency-Key`
- Body allowlist confirmed:
  - `calledAt`
  - `reasonCode`
  - `note`
- Blank optional fields are converted to `null` and omitted by `toApiBody`.
- Forbidden command/body fields are not sent:
  - `reservationStatus`
  - `tableId`
  - `tableGroupId`
  - `seatingId`
  - `cleaningId`
  - `turnoverId`
  - `skipReason`
  - `rejoinReason`

## 7. Form Validation

- Initial form rendered:
  - `排队票 ID`
  - `叫号时间（可选）`
  - `补充信息`
  - `执行叫号`
- Blank `queueTicketId` kept submit disabled.
- After filling `queueTicketId`, submit became enabled.
- `calledAt` accepted a datetime-local value and converted it to ISO8601 for the API call.
- Optional `reasonCode` and `note` fields were available after expanding `补充信息`.
- Submit generated UI idempotency keys with prefix `queue:call:`.
- Submit loading/disabled behavior is wired through `isSubmitting` and `canSubmit`.

## 8. Success Submit

- Submitted waiting ticket:
  - `91000000-0000-0000-0000-000000000982`
- UI result:
  - Heading: `叫号成功`
  - `queueTicketNumber = 12`
  - `queueTicketStatus = called`
  - `reservationId = 50000000-0000-0000-0000-000000000982`
  - `reservationCode = R-CALL-UI-0982`
  - `reservationStatus = arrived`
  - `calledAt = 2030-06-20T03:30:00Z`
  - `holdUntilAt = 2030-06-20T03:34:00Z`
  - `alreadyCalled = false`
  - `events = queue_ticket.called`
  - `idempotency.status = completed`
  - `idempotency.replayed = false`

## 9. AlreadyCalled Result

- Re-submitted the same ticket with a new UI-generated idempotency key.
- UI result:
  - Heading: `已叫号`
  - `queueTicketStatus = called`
  - `reservationStatus = arrived`
  - `calledAt = 2030-06-20T03:30:00Z`
  - `holdUntilAt = 2030-06-20T03:34:00Z`
  - `alreadyCalled = true`
  - `events = []`
  - `idempotency.status = completed`
  - `idempotency.replayed = false`
- Database evidence confirmed no duplicate `queue_ticket.called` event, no duplicate `queue_ticket.call` transition, and no duplicate `queue.call` audit.

## 10. Error Display

- Submitted skipped ticket:
  - `91000000-0000-0000-0000-000000000985`
- UI result:
  - Heading: `叫号失败`
  - `error.code = QUEUE_TICKET_STATUS_NOT_WAITING`
  - `error.messageKey = queue.call.queue_ticket_status_not_waiting`
- The skipped ticket remained `skipped` with no `called_at` or `expires_at`.

## 11. Database Assertions

- Fresh Queue Call:
  - `queue_tickets.status = called`
  - `queue_tickets.called_at = 2030-06-20 11:30:00+08`
  - `queue_tickets.expires_at = 2030-06-20 11:34:00+08`
  - `reservations.status = arrived`
  - `business_events queue_ticket.called count = 1`
  - `state_transition_logs queue_ticket waiting -> called count = 1`
  - `audit_logs queue.call count = 1`
  - `idempotency_records completed count = 2`
- Error path:
  - `audit_logs queue.call.failed count = 1`
  - `idempotency_records failed count = 1`
  - skipped ticket remained `skipped`.
- Boundary assertions:
  - `app_gate_audit_logs APP_GATE_DENIED count = 0`
  - `seatings count = 0`
  - `seating_resources count = 0`
  - `table_locks count = 0`
  - `reservation_preassignments count = 0`
  - `cleanings count = 0`
  - `turnovers count = 0`
  - `dining_tables count = 0`
  - `reservations no_show/cancelled count = 0`

## 12. Commands

- `npm run build`: Passed.
  - `vue-tsc --noEmit && vite build`
  - 69 modules transformed.
- `mvn -q "-Dtest=QueueCall*Test" test`: Passed.
- `mvn test`: Not run in this validation round because no backend code was modified; the validation contract allows skipping full `mvn test` when only frontend/documentation validation is changed, provided this is reported.
- Additional runtime command:
  - `mvn -q -DskipTests package`: Passed, used only to create the local backend jar for runtime validation.

## 13. Files Changed

- Created `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`.
- No production source code was changed in this validation round.
- No backend code was changed.
- No frontend code was changed.

## 14. Boundary Check

- Queue Call UI route validated: Yes.
- Queue Call UI real backend submit validated: Yes.
- QueueTicket waiting -> called validated: Yes.
- Reservation status remains arrived: Yes.
- StorePolicy hold behavior validated with `queue_call_hold_minutes = 4`: Yes.
- AlreadyCalled success-like UI validated: Yes.
- Error display validated: Yes.
- Idempotency key generation and result display validated: Yes.
- Business event written once: Yes.
- State transition written once: Yes.
- Audit written once for success: Yes.
- Failure audit written for error: Yes.
- Seating implemented: No.
- Seating from queue implemented: No.
- Table status changed: No.
- Queue List implemented: No.
- Queue Display implemented: No.
- Queue Skip implemented: No.
- Queue Rejoin implemented: No.
- Table assignment implemented: No.
- No-show implemented: No.
- Cancellation implemented: No.
- Cleaning implemented: No.
- Turnover implemented: No.
- Controller created: No.
- API DTO created: No.
- Backend API changed: No.
- UI feature changed beyond validation: No.
- Migration changed: No.
- Production database touched: No.
- Seed data inserted into production: No.

## 15. Open Questions

- None for this validation scope.

## 16. Open Conflicts

- Runtime note: Spring Boot auto-Flyway startup failed against local PostgreSQL 17.10 with `Unsupported Database: PostgreSQL 17.10`. Existing project integration tests use manual `psql` migration application and `spring.flyway.enabled=false`; this validation followed that established local-test pattern. No dependency or migration change was made in this round.

## 17. Next Step Recommendation

- Proceed to the next approved Queue slice only after Product Owner confirms the next boundary, likely Queue Display/List or Seating from Queue.
- If future local-runtime validation will repeatedly use PostgreSQL 17.10, consider a separately approved infrastructure maintenance task to align Flyway PostgreSQL support. Do not mix that into Queue feature work.
