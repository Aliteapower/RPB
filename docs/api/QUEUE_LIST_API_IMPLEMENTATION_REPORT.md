# Queue List Read API Implementation Report

## 1. Read Documents

- `docs/api/QUEUE_LIST_API_CONTRACT.md`
- Reservation Arrived To Queue application/API/UI reports
- Queue Call application/API/UI validation reports
- Seating From Called Queue application/API/UI validation reports
- App Gate operational handoff, integration checklist, and permission metadata alignment docs
- Governance, glossary, data standard, architecture, reservation skill, schema design, and bootstrap migration references
- QueueTicket, QueueGroup, Reservation, Store, App Gate, current actor, and existing controller/application/persistence patterns

## 2. API Contract

- Implemented the read-only Queue List endpoint from the V1 contract.
- The endpoint is limited to listing queue tickets for a tenant-scoped store.
- The API does not call Queue Call, Queue Skip, Queue Rejoin, Queue Display, Seating, Cleaning, Turnover, Cancellation, or No-show flows.

## 3. Created / Updated Files

- Created Queue List application models, query, service, list rows, API controller, DTOs, mappers, error mapping, projection, and tests.
- Updated `QueueTicketRepositoryPort`, `QueueTicketPersistenceAdapter`, and `QueueTicketJpaRepository` with the minimal read-only list method.
- Updated `AppGateRequiredPermission` with `QUEUE_VIEW = "queue.view"`.
- Updated local runtime security to allow the local/test Queue List GET endpoint.
- Updated App Gate documentation for `queue.view`.
- Updated existing approved boundary baselines to recognize the new Queue List API files and previously approved seating UI artifact.

## 4. Endpoint

```http
GET /api/v1/stores/{storeId}/queue-tickets
```

- Controller: `QueueTicketListController`
- Guard: `@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")`
- Local/test runtime security: `GET /api/v1/stores/*/queue-tickets`

## 5. Query Params

- `status`: optional, must match an existing `QueueTicketStatus` code.
- `limit`: optional, default `50`, max `100`, must be positive.
- `offset`: optional, default `0`, must be non-negative.

## 6. App Gate Metadata

- `app_key = reservation_queue`
- `permission = queue.view`
- Java metadata includes `AppGateRequiredPermission.QUEUE_VIEW`.
- `queue.view` is included in `RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- App Gate guard integration tests cover the Queue List endpoint annotation.

## 7. Response DTO

Success response includes:

- `success`
- `items`
- `page.limit`
- `page.offset`
- `page.total`

Each item includes:

- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus`
- `partySize`
- `partySizeGroup`
- `reservationId`
- `reservationCode`
- `reservationStatus`
- `customerName`
- `customerPhoneMasked`
- `createdAt`
- `calledAt`
- `holdUntilAt`
- `expiresAt`

`expiresAt` is mapped to both `holdUntilAt` and `expiresAt` for Queue Call compatibility.

## 8. Error Mapping

- Invalid query: `400 INVALID_QUERY`
- Invalid status: `400 INVALID_STATUS`
- Invalid limit: `400 INVALID_LIMIT`
- Invalid offset: `400 INVALID_OFFSET`
- Store not found: `404 STORE_NOT_FOUND`
- Store scope mismatch: `403 STORE_SCOPE_MISMATCH`
- Store access denied: `403 FORBIDDEN`
- Persistence failure: `500 PERSISTENCE_ERROR`

Raw persistence exceptions are not exposed to the API response.

## 9. Pagination / Sorting

- Default pagination: `limit = 50`, `offset = 0`.
- Max limit: `100`.
- Sort order: `created_at asc`, then `ticket_number asc`.
- The count query uses the same tenant/store/status/deleted filters as the row query.

## 10. App Gate Tests

- `AppGateRequiredPermissionTest` covers `QUEUE_VIEW` and `RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `AppGateServiceTest` covers app visibility recognition for `queue.view`.
- `AppGateGuardIntegrationTest` covers the Queue List `@RequireAppGate` metadata.
- Queue List integration tests cover entitlement missing, store app disabled, and missing `queue.view` permission denial with App Gate audit rows.

## 11. API / Integration Tests

- `QueueTicketListApplicationServiceTest`
- `QueueTicketListControllerTest`
- `QueueTicketListLocalRuntimeSecurityTest`
- `QueueTicketListApiIntegrationTest`

Covered success cases:

- Waiting/called/seated tickets can be read.
- `status` filter works.
- Pagination works.
- Sorting is stable by created time and ticket number.
- Reservation and customer summary fields are returned.
- Customer phone is masked.
- `expiresAt` is returned as both `expiresAt` and `holdUntilAt`.

Covered failure cases:

- Invalid status.
- Invalid limit.
- Invalid offset.
- Store not found.
- Store scope mismatch.
- Store access denied.
- Persistence failure.
- App Gate denial cases.

## 12. Local Runtime Security

- Added local/test permit rule for `GET /api/v1/stores/*/queue-tickets`.
- `QueueTicketListLocalRuntimeSecurityTest` verifies the local endpoint can pass through security and still uses the controller/application path.

## 13. Commands Executed

```bash
mvn -q "-Dtest=QueueTicketList*Test,QueueList*Test,AppGateRequiredPermissionTest,AppGateServiceTest,AppGateGuardIntegrationTest" test
mvn -q "-Dtest=QueueTicketList*Test,QueueList*Test" test
mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,WalkInDirectSeatingControllerTest" test
mvn test
npm run build
```

## 14. Test Result

- Target Queue List tests: passed.
- Target Queue List plus App Gate tests: passed.
- Boundary baseline tests: passed.
- Full backend test suite: passed, `488` tests, `0` failures, `0` errors, `0` skipped.
- Frontend build: passed.

## 15. Boundary Check

Seating implemented: No

SeatingResource implemented: No

Table status changed: No

Queue Call changed: No

Queue Skip implemented: No

Queue Rejoin implemented: No

Queue Display implemented: No

Table assignment implemented: No

Reservation status changed: No

No-show implemented: No

Cancellation implemented: No

Cleaning implemented: No

Turnover implemented: No

BusinessEvent written: No

StateTransitionLog written: No

AuditLog written: No

IdempotencyRecord written: No

UI implemented: No

Migration changed: No

Production database touched: No

Seed data inserted: No

## 16. Open Questions

- None for this V1 read API slice.

## 17. Open Conflicts

- None.

## 18. Next Step Recommendation

- Use this Queue List Read API as the source for the next approved Queue List UI slice.
- Keep Queue Skip, Queue Rejoin, Queue Display, and automatic expiration behavior in separate contracts.
