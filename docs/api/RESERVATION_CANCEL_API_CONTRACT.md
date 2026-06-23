# Reservation Cancellation API Contract

## Endpoint

```text
POST /api/v1/stores/{storeId}/reservations/{reservationId}/cancel
```

Required header:

```text
Idempotency-Key: <stable retry key>
```

App Gate:

```text
appKey = reservation_queue
permission = reservation.cancel
```

## Request

```json
{
  "cancelledAt": "2030-06-20T03:20:00Z",
  "reasonCode": "guest_requested",
  "note": "Customer called to cancel"
}
```

Fields:

| Field | Required | Notes |
|---|---:|---|
| `cancelledAt` | No | UTC instant. If omitted, the application clock is used. |
| `reasonCode` | No | Trimmed before use. Persisted to `reservations.cancellation_reason_code`. |
| `note` | No | Trimmed before use. Captured in audit/business/state metadata only. |

## Response

```json
{
  "success": true,
  "reservationId": "50000000-0000-0000-0000-000000000981",
  "reservationCode": "R-CANCEL-0981",
  "status": "cancelled",
  "cancelledAt": "2030-06-20T03:20:00Z",
  "cancellationReasonCode": "guest_requested",
  "alreadyCancelled": false,
  "events": ["reservation.cancelled"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

## State Rules

Allowed fresh cancellations:

```text
draft -> cancelled
confirmed -> cancelled
```

Already cancelled reservations return a success-like response with `alreadyCancelled = true` and no duplicate business event, audit log, state transition log, or reservation save.

Safety V1 rejects these states and intentionally avoids cross-module side effects:

| Current status | HTTP | Error code | Reason |
|---|---:|---|---|
| `arrived` | 409 | `RESERVATION_CANNOT_CANCEL_ARRIVED` | Arrived reservations may already have queue or seating workflow evidence. |
| `seated` | 409 | `RESERVATION_CANNOT_CANCEL_SEATED` | Seating must be handled by seating/table workflow. |
| `no_show` | 409 | `RESERVATION_CANNOT_CANCEL_NO_SHOW` | No-show is a terminal reservation outcome for this command. |
| `completed` | 409 | `RESERVATION_CANNOT_CANCEL_COMPLETED` | Completed reservations are terminal. |

## Persistence

This API does not add a migration.

Updated columns:

```text
reservations.status = cancelled
reservations.cancellation_reason_code = request.reasonCode
reservations.updated_at = effective cancelledAt
```

No changes are made to:

```text
queue_tickets
seatings
table_locks
reservation_preassignments
migrations
```

## Evidence

Successful fresh cancellation writes:

```text
BusinessEvent.event_type = reservation.cancelled
StateTransitionLog.transition_code = reservation.cancel
AuditLog.operation_code = reservation.cancel
IdempotencyRecord.action = cancel_reservation
IdempotencyRecord.target_type = reservation
```

Failure after idempotency start writes:

```text
AuditLog.operation_code = reservation.cancel.failed
IdempotencyRecord.status = failed
```

## Idempotency

The command is idempotent by store scope, actor source, action, and key.

| Existing record | Same request hash | Result |
|---|---:|---|
| none | n/a | Start and execute command. |
| completed | yes | Replay stored success response. |
| started | yes | `IDEMPOTENCY_IN_PROGRESS`. |
| failed | yes | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. |
| any | no | `IDEMPOTENCY_CONFLICT`. |

The request hash includes tenant id, store id, reservation id, actor id, actor type, effective cancellation time input marker, reason code, and note. It does not include the idempotency key.

## Boundary

Safety V1 only cancels the reservation record and writes reservation-owned evidence. It does not cancel arrived queue tickets, seated records, table locks, table resources, no-show state, or external POS resources.
