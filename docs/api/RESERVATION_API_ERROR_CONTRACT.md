# Reservation API Error Contract V1

## Purpose

This document defines the Reservation Create API error contract. It is documentation only and does not create Controller, API DTO Java class, endpoint implementation, Repository, Entity, migration, SQL, UI, test code, seed data, or mock runtime data.

The concrete consumer is:

```text
POST /api/v1/stores/{storeId}/reservations
```

## Common Error Envelope

Recommended error body:

```json
{
  "success": false,
  "error": {
    "code": "RESERVATION_CAPACITY_INSUFFICIENT",
    "messageKey": "reservation.capacity_insufficient",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Required fields:

| Field | Required | Meaning |
| --- | ---: | --- |
| `success` | Yes | Always `false` for error responses. |
| `error.code` | Yes | Stable public API error code. |
| `error.messageKey` | Yes | i18n key for client display resolution. |
| `error.details` | Yes | Safe structured details object. Empty object is allowed. |
| `idempotency.status` | Conditional | Present when the request has an idempotency outcome. |

## Error Code Convention

Rules:

- Error codes are stable, uppercase, snake-case identifiers.
- Error codes are API contract values, not Java enum names by default.
- API may map internal application errors to public error codes.
- Do not expose database constraint names, table names, stack traces, SQL, Entity names, Repository method names, or Mapper details.
- Do not put user-facing display text in `error.code`.

## Message Key Convention

Reservation Create message keys use:

```text
reservation.<error_key>
```

Examples:

- `reservation.invalid_party_size`
- `reservation.capacity_insufficient`
- `reservation.idempotency_conflict`

Rules:

- `messageKey` is required for every error.
- API responses must not hardcode display messages.
- Store locale, Tenant fallback, and platform fallback are future i18n resolution responsibilities.
- API may optionally include `details` for interpolation, but clients must still resolve display text by key.

## Details Object Convention

`details` must be an object.

Allowed examples:

```json
{
  "field": "partySize",
  "minimum": 1
}
```

```json
{
  "field": "reservedEndAt",
  "rule": "must_be_after_reservedStartAt"
}
```

```json
{
  "capacityScope": "STORE_TIME_WINDOW",
  "requestedPartySize": 4
}
```

Forbidden details:

- Stack traces.
- SQL strings.
- Constraint names.
- Persistence Entity class names.
- Repository method names.
- Full AuditLog metadata.
- Full JWT contents.
- Internal security policy implementation details.
- Sensitive customer notes beyond the field-level reason needed by the client.
- Internal capacity fallback value unless Product Owner approves exposing it for operational diagnostics.

## Reservation Create Error Codes

| API Error Code | Message Key | HTTP Status | Meaning |
| --- | --- | --- | --- |
| `STORE_NOT_FOUND` | `reservation.store_not_found` | 404 | Path Store does not exist in authenticated Tenant scope. |
| `STORE_SCOPE_MISMATCH` | `reservation.store_scope_mismatch` | 403 | Store does not belong to authenticated Tenant or actor Store scope. |
| `FORBIDDEN` | `reservation.forbidden` | 403 | Authenticated actor lacks role or permission `reservation.create`. |
| `MISSING_IDEMPOTENCY_KEY` | `reservation.missing_idempotency_key` | 400 | Required `Idempotency-Key` header is absent or blank. |
| `IDEMPOTENCY_CONFLICT` | `reservation.idempotency_conflict` | 409 | Same idempotency key was reused with a different request hash. |
| `IDEMPOTENCY_IN_PROGRESS` | `reservation.idempotency_in_progress` | 409 | Same command is already started and not completed. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `reservation.idempotency_failed_requires_new_key` | 409 | Same failed idempotency key cannot be reused in V1. |
| `INVALID_PARTY_SIZE` | `reservation.invalid_party_size` | 400 | `partySize` is missing or not greater than 0. |
| `INVALID_TIME_RANGE` | `reservation.invalid_time_range` | 400 | Reservation time range is missing or `reservedEndAt <= reservedStartAt`. |
| `RESERVATION_START_IN_PAST` | `reservation.start_in_past` | 400 | `reservedStartAt` is in the past. |
| `INVALID_PHONE_E164` | `reservation.invalid_phone_e164` | 400 | `phoneE164` is present but not valid E.164. |
| `CUSTOMER_NOT_FOUND` | `reservation.customer_not_found` | 404 | Supplied `customerId` does not exist in authenticated Tenant scope. |
| `INVALID_CUSTOMER_IDENTITY` | `reservation.invalid_customer_identity` | 400 | Customer identity hints cannot be resolved or created for this Tenant. |
| `RESERVATION_DUPLICATE_ACTIVE` | `reservation.duplicate_active` | 409 | Same Tenant + Store + Customer + overlapping active Reservation already exists. |
| `RESERVATION_CAPACITY_INSUFFICIENT` | `reservation.capacity_insufficient` | 409 | Store time-window capacity is insufficient for requested party size. |
| `RESERVATION_CODE_CONFLICT` | `reservation.code_conflict` | 409 | Reservation code conflicts with an existing active Store-scoped code. |
| `RESERVATION_POLICY_NOT_FOUND` | `reservation.policy_not_found` | 409 | Required Store reservation policy is unavailable when fallback is not allowed. |
| `AUDIT_WRITE_FAILED` | `reservation.audit_write_failed` | 500 | Critical audit write failed; command must not be treated as completed. |
| `EVENT_WRITE_FAILED` | `reservation.event_write_failed` | 500 | Required business event write failed. |
| `STATE_TRANSITION_WRITE_FAILED` | `reservation.state_transition_write_failed` | 500 | Required state transition log write failed. |
| `PERSISTENCE_ERROR` | `reservation.persistence_error` | 500 | Persistence failed without a more specific safe API error. |

## Application Error Mapping

Suggested mapping from `ReservationCreateError` to API error code:

| Application Error | API Error Code |
| --- | --- |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` |
| `MISSING_IDEMPOTENCY_KEY` | `MISSING_IDEMPOTENCY_KEY` |
| `IDEMPOTENCY_CONFLICT` | `IDEMPOTENCY_CONFLICT` |
| `COMMAND_IN_PROGRESS` | `IDEMPOTENCY_IN_PROGRESS` |
| `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` |
| `INVALID_PARTY_SIZE` | `INVALID_PARTY_SIZE` |
| `INVALID_TIME_RANGE` | `INVALID_TIME_RANGE` |
| `RESERVATION_START_IN_PAST` | `RESERVATION_START_IN_PAST` |
| `INVALID_PHONE_E164` | `INVALID_PHONE_E164` |
| `CUSTOMER_NOT_FOUND` | `CUSTOMER_NOT_FOUND` |
| `RESERVATION_DUPLICATE_ACTIVE` | `RESERVATION_DUPLICATE_ACTIVE` |
| `RESERVATION_CAPACITY_INSUFFICIENT` | `RESERVATION_CAPACITY_INSUFFICIENT` |
| `RESERVATION_CODE_CONFLICT` | `RESERVATION_CODE_CONFLICT` |
| `AUDIT_WRITE_FAILED` | `AUDIT_WRITE_FAILED` |
| `BUSINESS_EVENT_WRITE_FAILED` | `EVENT_WRITE_FAILED` |
| `STATE_TRANSITION_WRITE_FAILED` | `STATE_TRANSITION_WRITE_FAILED` |
| `REPOSITORY_SAVE_FAILED` | `PERSISTENCE_ERROR` |
| `PERSISTENCE_ERROR` | `PERSISTENCE_ERROR` |

## HTTP Status Mapping

| Scenario | HTTP Status |
| --- | --- |
| Validation error | `400 Bad Request` |
| Missing or invalid JWT | `401 Unauthorized` |
| Role, permission, or scope denied | `403 Forbidden` |
| Store or Customer not found | `404 Not Found` |
| Duplicate, capacity, code conflict, illegal state, or idempotency conflict | `409 Conflict` |
| Idempotency in progress | `409 Conflict` |
| Audit, event, transition, or persistence failure | `500 Internal Server Error` |

## I18n Rule

- API error responses carry `messageKey`.
- API error responses do not carry hardcoded display text.
- Clients may resolve text through Store locale or a later i18n catalog endpoint.
- `details` may provide structured interpolation values.
- Message catalog ownership remains in the i18n boundary, not in Controller code.

## Non-Scope

This document does not implement:

- Java exception handler.
- Controller advice.
- API DTO Java classes.
- OpenAPI generation.
- i18n catalog rows.
- Frontend message rendering.
- Tests.
- Migration or SQL.
