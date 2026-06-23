# Queue Rejoin API Implementation Report V1

## 1. Read Documents

- `docs/api/QUEUE_REJOIN_API_CONTRACT.md`
- `docs/api/QUEUE_SKIP_API_CONTRACT.md`
- `docs/api/QUEUE_SKIP_API_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/api/SEATING_FROM_CALLED_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_SKIP_UI_CONTRACT.md`
- `docs/frontend/QUEUE_SKIP_UI_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/governance/*`
- Existing Queue Skip, Queue Call, Queue List, and Seating From Called Queue API/controller/service/test patterns
- Existing App Gate metadata, guard, visible app, local runtime security, audit, idempotency, queue, reservation, and persistence patterns

## 2. Endpoint

- Method: `POST`
- Path: `/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin`
- Required header: `Idempotency-Key`
- Request body: `{}` or `{ "note": "optional staff note" }`
- App Gate: `reservation_queue.queue.rejoin`

## 3. Created Files

- `src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketRequest.java`
- `src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketResponse.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorCode.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorResponse.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorMapper.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiMapper.java`
- `src/main/java/com/rpb/reservation/queue/api/QueueRejoinController.java`
- `src/main/java/com/rpb/reservation/queue/application/QueueRejoinError.java`
- `src/main/java/com/rpb/reservation/queue/application/QueueRejoinResult.java`
- `src/main/java/com/rpb/reservation/queue/application/command/RejoinQueueTicketCommand.java`
- `src/main/java/com/rpb/reservation/queue/application/rule/QueueRejoinRule.java`
- `src/main/java/com/rpb/reservation/queue/application/rule/QueueRejoinEvidenceRule.java`
- `src/main/java/com/rpb/reservation/queue/application/service/QueueRejoinApplicationService.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueRejoinControllerTest.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueRejoinApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/queue/api/QueueRejoinLocalRuntimeSecurityTest.java`
- `docs/api/QUEUE_REJOIN_API_IMPLEMENTATION_REPORT.md`

## 4. Updated Files

- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/queue/domain/QueueTicket.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueTicketMapper.java`
- `src/main/java/com/rpb/reservation/queue/state/QueueTicketStateMachine.java`
- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- `src/test/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermissionTest.java`
- Existing boundary baseline tests that enumerate approved queue API files/controllers

## 5. Request DTO

`RejoinQueueTicketRequest` exposes only:

- `note`

The API does not accept client-controlled tenant, store, actor, reservation, table, seating, status, targetStatus, queuePosition, ticketNumber, rejoinedAt, reasonCode, skip, noShow, cancellation, cleaning, or turnover fields.

## 6. Response DTO

`RejoinQueueTicketResponse` returns:

- `success`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus`
- `reservationId`
- `reservationCode`
- `reservationStatus`
- `queuePosition`
- `rejoinedAt`
- `alreadyRejoined`
- `events`
- `idempotency.status`
- `idempotency.replayed`

## 7. State Behavior

- Source status: `skipped`
- Target status: `waiting`
- `queue_tickets.rejoined_at` is set by the backend clock.
- Queue position is moved to the tail of the same queue group and business date.
- Ticket number is preserved.
- Reservation remains `arrived`.
- `called_at` and `called_expires_at` are cleared when the ticket rejoins.
- `skipped_at` is preserved as historical source evidence.

## 8. App Gate Behavior

- Added stable permission key `queue.rejoin`.
- Included `queue.rejoin` in the existing `reservation_queue` permission metadata.
- Controller requires `@RequireAppGate(appKey = "reservation_queue", permission = "queue.rejoin")`.
- Denied App Gate requests do not reach the application service mutation path.
- Local/test runtime security allowlist includes the rejoin path for local profile validation.

## 9. Idempotency Behavior

- Required header: `Idempotency-Key`
- Idempotency action: `rejoin_queue_ticket`
- Completed replay returns success with `idempotency.replayed = true`.
- Same key with a different request hash returns `IDEMPOTENCY_CONFLICT`.
- In-progress keys return `IDEMPOTENCY_IN_PROGRESS`.
- Failed keys require a new key.
- Missing key returns `MISSING_IDEMPOTENCY_KEY` and writes no mutation.

## 10. AlreadyRejoined Behavior

- If ticket status is `waiting` and complete rejoin evidence exists, the API returns success with `alreadyRejoined = true`.
- Complete evidence requires `rejoined_at`, rejoin BusinessEvent, rejoin StateTransitionLog, and rejoin AuditLog.
- No duplicate BusinessEvent, StateTransitionLog, AuditLog, or idempotency side effects are written for completed replay or already-rejoined evidence success.
- If evidence is incomplete, the API returns `QUEUE_REJOIN_EVIDENCE_INCOMPLETE`.

## 11. Error Mapping

Stable API errors include:

- `MISSING_IDEMPOTENCY_KEY`
- `INVALID_COMMAND`
- `STORE_NOT_FOUND`
- `STORE_SCOPE_MISMATCH`
- `FORBIDDEN`
- `QUEUE_TICKET_NOT_FOUND`
- `QUEUE_TICKET_STATUS_NOT_SKIPPED`
- `QUEUE_SKIP_EVIDENCE_INCOMPLETE`
- `QUEUE_REJOIN_EVIDENCE_INCOMPLETE`
- `RESERVATION_NOT_FOUND`
- `RESERVATION_STATUS_NOT_ARRIVED`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- `ILLEGAL_STATE_TRANSITION`
- `EVENT_WRITE_FAILED`
- `STATE_TRANSITION_WRITE_FAILED`
- `AUDIT_WRITE_FAILED`
- `PERSISTENCE_ERROR`

Raw database exceptions are not exposed.

## 12. Persistence / Schema

- Reused existing `queue_tickets.rejoined_at`.
- No migration was added.
- No schema change was made.
- No production database was touched.

## 13. Runtime / DB Validation

Validated through Spring Boot integration tests with local temporary PostgreSQL 17.10:

- Successful rejoin writes QueueTicket `waiting`, tail `queue_position`, and `rejoined_at`.
- BusinessEvent is written once for first success.
- StateTransitionLog is written once for first success.
- AuditLog is written once for first success.
- Idempotency record is completed.
- Completed replay does not duplicate evidence.
- Already-rejoined success does not duplicate evidence.
- App Gate denied requests write only App Gate denial audit and do not mutate business state.

## 14. Boundary Validation

Confirmed absent:

- Queue Rejoin UI
- Queue Rejoin frontend API client
- Queue Display API
- Queue Workbench mutation
- Queue Call from list
- Queue Seat from list
- Seating API changes
- Table status changes
- Table map
- Reservation Calendar
- No-show API
- Cancellation API
- Cleaning behavior changes
- Turnover API
- Migration changes
- Seed data
- Production database access
- Existing API path changes
- Maven Wrapper
- GitHub remote/push

## 15. Commands Executed

- Red phase: `mvn -q "-Dtest=QueueRejoin*Test,AppGateRequiredPermissionTest,AppGateServiceTest" test` failed before implementation with missing Queue Rejoin classes/permission metadata.
- Focused Rejoin/App Gate: `mvn -q "-Dtest=QueueRejoin*Test,AppGateRequiredPermissionTest,AppGateServiceTest" test` passed.
- Required Rejoin suite: `mvn -q "-Dtest=QueueRejoin*Test" test` passed.
- Required runtime/security suite: `mvn -q "-Dtest=QueueRejoinApiIntegrationTest,QueueRejoinLocalRuntimeSecurityTest" test` passed.
- Legacy boundary resync group: `mvn -q "-Dtest=CleaningControllerTest,WalkInDirectSeatingControllerTest,ReservationControllerTest,CleaningCompleteApiIntegrationTest,ReservationCheckInApiIntegrationTest,ReservationCreateApiIntegrationTest" test` passed.
- Queue Skip regression: `mvn -q "-Dtest=QueueSkip*Test" test` passed.
- Full backend suite: `mvn -q test` passed.
- Frontend build: `cmd /c npm run build` passed.
- Frontend typecheck: `cmd /c npx vue-tsc --noEmit` passed.
- Vite build: `cmd /c npx vite build` passed.

## 16. Open Questions

- None for V1 API implementation.

## 17. Open Conflicts

- None.

## 18. Next Step Recommendation

- The next slice can decide whether to implement Queue Rejoin UI/client against this approved backend API contract. Keep it as a separate frontend slice.
