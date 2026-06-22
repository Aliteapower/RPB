# Queue Skip Vertical Slice Checklist V1

## 1. Contract Scope

| Check | Result |
| --- | --- |
| This round only creates Queue Skip contract documents. | Yes |
| Java implementation created. | No |
| API implementation created. | No |
| UI implementation created. | No |
| Router changed. | No |
| Staff Home changed. | No |
| App Gate metadata changed. | No |
| Migration changed. | No |
| SQL file changed. | No |
| Production database touched. | No |
| Seed data inserted. | No |

## 2. Selected Vertical Slice

| Check | Result |
| --- | --- |
| Only covers QueueTicket `called -> skipped`. | Yes |
| Allows `waiting -> skipped`. | No |
| Allows `seated -> skipped`. | No |
| Allows `cancelled -> skipped`. | No |
| Allows `expired -> skipped`. | No |
| Allows `rejoined -> skipped`. | No |
| Uses existing `skipped` QueueTicket status. | Yes |
| Requires durable `skippedAt` evidence. | Yes |

## 3. Reservation Boundary

| Check | Result |
| --- | --- |
| Reservation remains `arrived`. | Yes |
| Reservation becomes `skipped`. | No |
| Reservation becomes `cancelled`. | No |
| Reservation becomes `no_show`. | No |
| Reservation becomes `seated`. | No |
| Reservation state transition is written. | No |
| Related Reservation existence and `arrived` status are required. | Yes |

## 4. Non-Scope Boundary

| Check | Result |
| --- | --- |
| Queue Rejoin designed. | No |
| Queue Display designed. | No |
| Queue Workbench mutation designed. | No |
| Seating designed. | No |
| SeatingResource designed. | No |
| Table status change designed. | No |
| Table map designed. | No |
| Auto assignment designed. | No |
| No-show designed. | No |
| Cancellation designed. | No |
| Cleaning designed. | No |
| Turnover designed. | No |

## 5. Command Boundary

| Check | Result |
| --- | --- |
| `tenantId` comes from actor context. | Yes |
| `storeId` is required. | Yes |
| `queueTicketId` is required. | Yes |
| `skippedAt` is optional. | Yes |
| `reasonCode` is optional. | Yes |
| `note` is optional. | Yes |
| `idempotencyKey` is required. | Yes |
| `actorId` is required. | Yes |
| `actorType` is required. | Yes |
| Client-provided `reservationId` is accepted. | No |
| `tableId` is accepted. | No |
| `tableGroupId` is accepted. | No |
| `seatingId` is accepted. | No |
| `cleaningId` is accepted. | No |
| `turnoverId` is accepted. | No |
| `rejoinReason` is accepted. | No |
| `noShowAt` is accepted. | No |
| `cancelledAt` is accepted. | No |
| Client-provided `status` update is accepted. | No |

## 6. State / Event / Audit

| Check | Result |
| --- | --- |
| QueueTicket state transition is `called -> skipped`. | Yes |
| BusinessEvent is `queue_ticket.skipped`. | Yes |
| StateTransitionLog target is `queue_ticket`. | Yes |
| Audit operation is `queue.skip`. | Yes |
| Failure audit may use `queue.skip.failed`. | Yes |
| Reservation transition log is written. | No |
| Table transition log is written. | No |
| Seating transition log is written. | No |

## 7. Idempotency

| Check | Result |
| --- | --- |
| Idempotency action is `skip_queue_ticket`. | Yes |
| `COMPLETED + same hash` behavior is defined. | Yes |
| `IN_PROGRESS / STARTED + same hash` behavior is defined. | Yes |
| `FAILED + same hash` behavior is defined. | Yes |
| Same key with different hash conflict is defined. | Yes |
| Completed replay avoids duplicate event / transition / audit. | Yes |
| Missing idempotency key is an error. | Yes |

## 8. AlreadySkipped

| Check | Result |
| --- | --- |
| `alreadySkipped` behavior is defined. | Yes |
| Complete evidence requirement is defined. | Yes |
| Already skipped with evidence returns success-like result. | Yes |
| Already skipped with evidence duplicates BusinessEvent. | No |
| Already skipped with evidence duplicates StateTransitionLog. | No |
| Already skipped with evidence duplicates AuditLog. | No |
| Already skipped without evidence silently succeeds. | No |
| Already skipped without evidence returns application-level error. | Yes |

## 9. Future App Gate

| Check | Result |
| --- | --- |
| Future app key is `reservation_queue`. | Yes |
| Future permission is `queue.skip`. | Yes |
| Reuses `queue.call`. | No |
| Reuses `queue.seat`. | No |
| Reuses `queue.view`. | No |
| Reuses `reservation.queue`. | No |
| App Gate metadata changed in this round. | No |

## 10. Future API / UI

| Check | Result |
| --- | --- |
| Future API path is declared. | Yes |
| Future API is implemented in this round. | No |
| Future API requires `Idempotency-Key`. | Yes |
| Future body allowlist is `skippedAt`, `reasonCode`, `note`. | Yes |
| Future UI is implemented in this round. | No |
| Queue Skip button in Queue List is designed in this round. | No |
| Queue Workbench is designed in this round. | No |
| Queue Display is designed in this round. | No |

## 11. Final Boundary Confirmation

```text
Only called -> skipped: Yes
Reservation remains arrived: Yes
No Rejoin: Yes
No Display: Yes
No Seating: Yes
No Table change: Yes
No No-show: Yes
No Cancellation: Yes
No Cleaning / Turnover: Yes
No Migration: Yes
Future app_key = reservation_queue: Yes
Future permission = queue.skip: Yes
Idempotency clear: Yes
alreadySkipped clear: Yes
```
