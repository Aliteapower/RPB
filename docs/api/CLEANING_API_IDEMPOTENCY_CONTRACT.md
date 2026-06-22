# Cleaning API Idempotency Contract V1

## Purpose

This document defines the API-level idempotency contract for Cleaning commands:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

This is documentation only. It does not create Controller, API DTO Java class, endpoint implementation, Repository, Entity, migration, SQL, UI, test code, seed data, or mock runtime data.

## Header

Required header:

```text
Idempotency-Key: <key>
```

Rules:

- The header is required for Start Cleaning and Complete Cleaning.
- The value must be non-blank.
- The key is interpreted inside authenticated Tenant scope, Store scope, source, action, and request hash.
- The key must not be used as a table lock replacement.
- The key must not bypass authorization, Store scope checks, state validation, audit, or persistence errors.

## Scope

For Start Cleaning:

| Dimension | Source |
| --- | --- |
| Tenant scope | JWT / server security context |
| Store scope | Path `{storeId}` after membership validation |
| Source | Authenticated actor source, normally `staff` |
| Action | `start_cleaning` |
| Business target | Path `{seatingId}` |
| Idempotency key | `Idempotency-Key` header |

For Complete Cleaning:

| Dimension | Source |
| --- | --- |
| Tenant scope | JWT / server security context |
| Store scope | Path `{storeId}` after membership validation |
| Source | Authenticated actor source, normally `staff` |
| Action | `complete_cleaning` |
| Business target | Path `{cleaningId}` |
| Idempotency key | `Idempotency-Key` header |

Store-scoped idempotency identity:

```text
tenant + store + source + action + idempotencyKey
```

## Request Hash Rule

The API request hash should be based on normalized business intent, not raw JSON formatting.

### Start Cleaning Hash Inputs

Include:

- Authenticated `tenantId`.
- Path `storeId`.
- Action `start_cleaning`.
- Path `seatingId`.
- Authenticated or server-derived `actorType`.
- Normalized `reasonCode`.
- Normalized `note`.

Exclude:

- `tableId`.
- `tableGroupId`.
- Raw whitespace and JSON field ordering.
- `Idempotency-Key` itself.
- Access token string.
- Transient request id or trace id.
- Internal actor session metadata not part of business intent.

### Complete Cleaning Hash Inputs

Include:

- Authenticated `tenantId`.
- Path `storeId`.
- Action `complete_cleaning`.
- Path `cleaningId`.
- Authenticated or server-derived `actorType`.
- Normalized `reasonCode`.
- Normalized `note`.

Exclude:

- `tableId`.
- `tableGroupId`.
- Raw whitespace and JSON field ordering.
- `Idempotency-Key` itself.
- Access token string.
- Transient request id or trace id.
- Internal actor session metadata not part of business intent.

## Behavior Matrix

| Existing record | Same request hash | Different request hash |
| --- | --- | --- |
| None | Start command and execute once. | Not applicable. |
| `started` / `in_progress` | Return retry-later error. Do not mutate Cleaning or table status again. | Return `IDEMPOTENCY_CONFLICT`. |
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

Start Cleaning replay must not:

- Create a new Cleaning.
- Mark SeatingResource released again.
- Write duplicate `cleaning.started`.
- Write duplicate `table.cleaning`.
- Move the table to cleaning again.
- Append duplicate critical audit or state transition records.

Complete Cleaning replay must not:

- Write duplicate `cleaning.completed`.
- Write duplicate `table.available`.
- Move the table to available again.
- Append duplicate critical audit or state transition records.
- Create or update Turnover BI.

## In-Progress Behavior

When an existing started/in-progress record has the same request hash:

- Recommended status: `409 Conflict` or `425 Too Early`.
- Error code: `IDEMPOTENCY_IN_PROGRESS`.
- Message key: `cleaning.idempotency_in_progress`.
- The response should communicate retry-later semantics without creating another mutation.

Recommended response:

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_IN_PROGRESS",
    "messageKey": "cleaning.idempotency_in_progress",
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
    "messageKey": "cleaning.idempotency_conflict",
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
- Do not create Cleaning, table status changes, AuditLog, BusinessEvent, or StateTransitionLog records for a successful operation.

## Success Completion

On first successful Start Cleaning execution:

- Create or use the application-level started record.
- Execute the command once.
- Complete the idempotency record with target type `cleaning`.
- Store a response snapshot or result pointer sufficient for completed replay.
- Return `201 Created`.

On first successful Complete Cleaning execution:

- Create or use the application-level started record.
- Execute the command once.
- Complete the idempotency record with target type `cleaning`.
- Store a response snapshot or result pointer sufficient for completed replay.
- Return `200 OK`.

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

If failure happens before idempotency can start:

- Return the mapped API error.
- Do not require an IdempotencyRecord to exist.

## Security Notes

- Idempotency lookup must happen inside authenticated Tenant/Store/action/source scope.
- A key used by one Tenant must not affect another Tenant.
- A key used for one Store must not affect another Store.
- A key used for Start Cleaning must not affect Complete Cleaning.
- Idempotency must not leak whether a Seating, Cleaning, Table, or TableGroup exists outside the caller's scope.

## Non-Scope

This contract does not define:

- Database retention duration.
- Redis key structure.
- OpenAPI schema.
- Java idempotency filter.
- Controller implementation.
- Background expiry job.
- `retryable_failure`.
- Turnover projection idempotency.
