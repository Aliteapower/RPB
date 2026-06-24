# Table Switch API V1 Contract

## Scope

Table Switch API V1 changes the active seating resource for an occupied seating.
It keeps the seating active, moves the previous table resource into cleaning, and
occupies the target table resource.

This contract does not implement frontend wiring, migrations, POS integration,
reservation creation, queue mutation, or payment behavior.

## Endpoint

```http
POST /api/v1/stores/{storeId}/seatings/{seatingId}/table-switch
Idempotency-Key: <required>
Content-Type: application/json
```

## Permission

```text
App Gate appKey: reservation_queue
Required permission: table.switch
Allowed roles: tenant_admin, store_manager, store_staff
```

The local runtime allowlist may permit only this exact POST path when
`rpb.local-auth.enabled=true`.

## Request

Exactly one target must be provided.

```json
{
  "tableId": "60000000-0000-0000-0000-000000000001",
  "tableGroupId": null,
  "reasonCode": "guest_requested",
  "note": "moved away from entrance"
}
```

## Response

Created on first success, OK on idempotent replay.

```json
{
  "success": true,
  "seatingId": "40000000-0000-0000-0000-000000000001",
  "fromResource": {
    "type": "TABLE",
    "id": "60000000-0000-0000-0000-000000000001",
    "status": "cleaning"
  },
  "toResource": {
    "type": "TABLE",
    "id": "60000000-0000-0000-0000-000000000002",
    "status": "occupied"
  },
  "cleaningId": "50000000-0000-0000-0000-000000000001",
  "seatingStatus": "occupied",
  "events": [
    "table.switch.completed",
    "table.cleaning",
    "table.occupied"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

## State Rules

- Source seating must exist in the requested store and be `occupied`.
- Source seating must have exactly one active `SeatingResource`.
- Source active resource may be `dining_table` or `table_group`.
- Target must be different from the source active resource.
- Target table must be `available`, capacity-compatible, unlocked, and not actively occupied.
- Target group must be `active`, valid, capacity-compatible, unlocked, not actively occupied, and all member tables must be available, unlocked, and not actively occupied.
- Source table member statuses move from `occupied` to `cleaning`.
- Source seating resource status moves from `active` to `released`.
- A new active seating resource is created for the target resource.
- Target table member statuses move from `available` to `occupied`.
- Seating status remains `occupied`.
- A cleaning record is opened for the released source resource.

## Idempotency

Action key:

```text
switch_table
```

- Missing `Idempotency-Key` returns `400`.
- Same key and same normalized request replays completed success without duplicate mutations.
- Same key and different request returns conflict.
- Started command returns retry-later conflict.
- Failed command requires a new idempotency key.

## Audit And Events

Successful switch writes:

- `BusinessEvent`: `table.switch.completed`, `table.cleaning`, `table.occupied`
- `StateTransitionLog`: source seating resource active to released, target seating resource created to active, source resource occupied to cleaning, target resource available to occupied
- `AuditLog`: `table.switch.completed`

Failure writes a best-effort failure audit:

```text
table.switch.failed
```

## Errors

Stable API error codes include:

| HTTP | Code |
|---|---|
| 400 | MISSING_IDEMPOTENCY_KEY |
| 400 | TABLE_SWITCH_TARGET_INVALID |
| 403 | FORBIDDEN |
| 403 | STORE_SCOPE_MISMATCH |
| 404 | STORE_NOT_FOUND |
| 404 | SEATING_NOT_FOUND |
| 404 | TABLE_NOT_FOUND |
| 404 | TABLE_GROUP_NOT_FOUND |
| 409 | SEATING_NOT_OCCUPIED |
| 409 | ACTIVE_SEATING_RESOURCE_NOT_FOUND |
| 409 | TABLE_SWITCH_TARGET_SAME_AS_CURRENT |
| 409 | TABLE_NOT_AVAILABLE |
| 409 | TABLE_GROUP_INVALID |
| 409 | TABLE_CAPACITY_INSUFFICIENT |
| 409 | TABLE_GROUP_CAPACITY_INSUFFICIENT |
| 409 | TABLE_LOCK_CONFLICT |
| 409 | TABLE_RESOURCE_UNAVAILABLE |
| 409 | CLEANING_ALREADY_ACTIVE |
| 409 | IDEMPOTENCY_CONFLICT |
| 409 | IDEMPOTENCY_IN_PROGRESS |
| 409 | IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY |
| 409 | ILLEGAL_STATE_TRANSITION |
| 500 | AUDIT_WRITE_FAILED |
| 500 | PERSISTENCE_ERROR |

## Database

No migration is added. The API uses existing seating, seating resource, dining
table, table group, cleaning, idempotency, audit, business event, and state
transition tables.
