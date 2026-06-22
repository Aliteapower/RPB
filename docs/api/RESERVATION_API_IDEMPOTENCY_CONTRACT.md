# Reservation API Idempotency Contract V1

## Purpose

This document defines the API-level idempotency contract for Reservation Create. It is documentation only and does not create Controller, API DTO Java class, endpoint implementation, Repository, Entity, migration, SQL, UI, test code, seed data, or mock runtime data.

The endpoint using this contract is:

```text
POST /api/v1/stores/{storeId}/reservations
```

## Header

Required header:

```text
Idempotency-Key: <key>
```

Rules:

- The header is required for Create Reservation.
- The value must be non-blank.
- The key is interpreted inside authenticated Tenant scope, Store scope, source, action, and request hash.
- The key must not be used as a capacity lock, table lock, or authorization bypass.
- The key must not bypass validation, duplicate checks, capacity checks, audit, event, or transition requirements.

## Scope

For Reservation Create:

| Dimension | Source |
| --- | --- |
| Tenant scope | JWT / server security context |
| Store scope | Path `{storeId}` after membership validation |
| Source | Authenticated actor source, normally `staff` |
| Action | `create_reservation` |
| Idempotency key | `Idempotency-Key` header |

Store-scoped idempotency identity:

```text
tenant + store + source + action + idempotencyKey
```

## Request Hash Rule

The API request hash should be based on normalized business intent, not raw JSON formatting.

Include:

- Authenticated `tenantId`.
- Path `storeId`.
- `partySize`.
- `reservedStartAt` normalized to UTC / ISO8601 instant.
- `reservedEndAt` normalized to UTC / ISO8601 instant when present.
- `customerId`.
- Normalized `customerName`.
- Normalized `customerNickname`.
- Normalized `phoneE164`.
- Normalized `note`.
- Server-derived source, normally `staff`.
- Authenticated or server-derived `actorType`.

Exclude:

- Raw whitespace and JSON field ordering.
- `Idempotency-Key` itself.
- Access token string.
- Transient request id or trace id.
- Internal actor session metadata not part of business intent.
- Derived `reservationId`.
- Derived `reservationCode` when not supplied by the client.
- Derived `reservedEndAt` when the request omitted it and the application computed it.
- Derived `holdUntilAt`.

If a later API version accepts client-provided `reservationCode`, that field must be included in the request hash.

## Behavior Matrix

| Existing record | Same request hash | Different request hash |
| --- | --- | --- |
| None | Start command and execute. | Not applicable. |
| `started` / `in_progress` | Return retry-later error. Do not create another Reservation. | Return `IDEMPOTENCY_CONFLICT`. |
| `completed` | Return previous success response with `idempotency.replayed = true`. | Return `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | Return `IDEMPOTENCY_CONFLICT`. |
| `expired` | V1 should require a new key unless retention policy later explicitly allows reuse. | Return `IDEMPOTENCY_CONFLICT`. |

## Completed Replay

When an existing completed record has the same request hash:

- Recommended status: `200 OK`.
- Return the previous success response shape.
- Set:

```json
{
  "idempotency": {
    "status": "completed",
    "replayed": true
  }
}
```

Replay must not:

- Create a new Reservation.
- Create a QueueTicket.
- Create Seating.
- Create TableLock.
- Create ReservationPreassignment.
- Append duplicate `reservation.created` or `reservation.confirmed` as if it were a new command.
- Append duplicate state transition or audit as if it were a new command.

## In-Progress Behavior

When an existing started/in-progress record has the same request hash:

- Recommended status: `409 Conflict`.
- Error code: `IDEMPOTENCY_IN_PROGRESS`.
- Message key: `reservation.idempotency_in_progress`.
- The response should communicate retry-later semantics without creating another mutation.

Recommended response:

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_IN_PROGRESS",
    "messageKey": "reservation.idempotency_in_progress",
    "details": {}
  },
  "idempotency": {
    "status": "started"
  }
}
```

## Failed Behavior

V1 Product Owner decision:

```text
FAILED idempotency record requires a new idempotency key.
```

Behavior:

- Same key + same hash + failed returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Recommended status: `409 Conflict`.
- Do not retry the original mutation under the same key.
- Future `retryable_failure` may be introduced later for transient infrastructure failures, but it is not part of V1.

Recommended response:

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY",
    "messageKey": "reservation.idempotency_failed_requires_new_key",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

## Conflict Behavior

Same key + different request hash:

- Return `IDEMPOTENCY_CONFLICT`.
- Recommended status: `409 Conflict`.
- Do not execute the new request.
- Do not mutate business data.
- Audit suspicious replay where future audit policy requires it.

Recommended response:

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "messageKey": "reservation.idempotency_conflict",
    "details": {}
  },
  "idempotency": {
    "status": "conflict"
  }
}
```

## Missing Key Behavior

If `Idempotency-Key` is missing or blank:

- Return `MISSING_IDEMPOTENCY_KEY`.
- Recommended status: `400 Bad Request`.
- Do not execute the command.
- Do not create Reservation, Customer, AuditLog, BusinessEvent, StateTransitionLog, QueueTicket, Seating, TableLock, or ReservationPreassignment records for a successful operation.

Recommended response:

```json
{
  "success": false,
  "error": {
    "code": "MISSING_IDEMPOTENCY_KEY",
    "messageKey": "reservation.missing_idempotency_key",
    "details": {
      "header": "Idempotency-Key"
    }
  }
}
```

## Success Completion

On first successful execution:

- Create or use the application-level started record.
- Execute the command once.
- Complete the idempotency record with target type `reservation`.
- Store a response snapshot or result pointer sufficient for completed replay.
- Return `201 Created`.

Response idempotency block:

```json
{
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

## Failure Completion

On accepted command failure after idempotency started:

- Mark idempotency as `failed`.
- Return the mapped API error.
- Include `idempotency.status = failed` where safe and applicable.
- V1 requires a new idempotency key for retry.

Failures before an idempotency record exists, such as missing header, do not need to mark idempotency failed.

## Snapshot Requirements

Completed replay must be able to return:

- `reservationId`
- `reservationCode`
- `status`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `businessDate`
- customer projection fields available at the time of success
- event codes
- idempotency status and replay flag

If current persistence snapshot is not enough to reconstruct the full API response, the later API implementation round may add the minimum mapping or snapshot support required. This round does not change persistence or API code.

## Security Notes

- Idempotency lookup must happen inside authenticated Tenant/Store/action/source scope.
- A key used by one Tenant must not affect another Tenant.
- A key used for one Store must not affect another Store.
- A key used for one action must not affect another action.
- Idempotency must not leak whether a Store, Customer, or Reservation exists outside the caller's scope.

## Non-Scope

This contract does not define:

- Database retention duration.
- Redis key structure.
- OpenAPI schema.
- Java idempotency filter.
- Controller implementation.
- Background expiry job.
- `retryable_failure`.
- Reservation CheckIn, Queue, Seating, No-show, Cancellation, or Table assignment idempotency.
