# API Error Contract V1

## Purpose

This document defines the common API error contract for Reservation Platform API design rounds. It is documentation only and does not create Controller, API DTO Java class, REST endpoint, code, tests, migration, SQL, UI, seed data, or mock runtime data.

The first concrete consumer of this contract is:

```text
POST /api/v1/stores/{storeId}/walk-ins/direct-seating
```

## Common Error Envelope

Recommended error body:

```json
{
  "success": false,
  "error": {
    "code": "TABLE_NOT_AVAILABLE",
    "messageKey": "walkin.direct_seating.table_not_available",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Required fields:

| Field | Required | Meaning |
| --- | --- | --- |
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
- Do not expose database constraint names, table names, stack traces, SQL, Entity names, or Repository details.
- Do not put user-facing display text in `error.code`.

## Message Key Convention

For this endpoint:

```text
walkin.direct_seating.<error_key>
```

Examples:

- `walkin.direct_seating.invalid_party_size`
- `walkin.direct_seating.table_lock_conflict`
- `walkin.direct_seating.idempotency_conflict`

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
  "resourceType": "TABLE",
  "resourceId": "00000000-0000-0000-0000-000000000003"
}
```

Forbidden details:

- Stack traces.
- SQL strings.
- Persistence Entity class names.
- Repository method names.
- Full AuditLog metadata.
- Full JWT contents.
- Internal security policy implementation details.
- Sensitive customer notes beyond the field-level reason needed by the client.

## WalkIn Direct Seating Error Codes

| API Error Code | Message Key | HTTP Status | Meaning |
| --- | --- | --- | --- |
| `STORE_NOT_FOUND` | `walkin.direct_seating.store_not_found` | 404 | Path Store does not exist in authenticated Tenant scope. |
| `STORE_SCOPE_MISMATCH` | `walkin.direct_seating.store_scope_mismatch` | 403 | Store or resource does not belong to authenticated Tenant/Store scope. |
| `FORBIDDEN` | `walkin.direct_seating.forbidden` | 403 | Authenticated actor lacks role or permission. |
| `MISSING_IDEMPOTENCY_KEY` | `walkin.direct_seating.missing_idempotency_key` | 400 | Required `Idempotency-Key` header is absent or blank. |
| `INVALID_PARTY_SIZE` | `walkin.direct_seating.invalid_party_size` | 400 | `partySize` is missing or not greater than 0. |
| `INVALID_PHONE_E164` | `walkin.direct_seating.invalid_phone_e164` | 400 | `phoneE164` is present but not E.164. |
| `INVALID_CUSTOMER_IDENTITY` | `walkin.direct_seating.invalid_customer_identity` | 400 | Customer identity cannot be resolved or created for this Tenant. |
| `RESOURCE_CONFLICT` | `walkin.direct_seating.resource_conflict` | 400 or 409 | Request resource fields conflict, such as both `tableId` and `tableGroupId`. |
| `TABLE_NOT_AVAILABLE` | `walkin.direct_seating.table_not_available` | 409 | Selected or candidate resource cannot be seated now. |
| `TABLE_CAPACITY_INSUFFICIENT` | `walkin.direct_seating.table_capacity_insufficient` | 409 | Party size is outside selected resource capacity range. |
| `TABLE_LOCK_CONFLICT` | `walkin.direct_seating.table_lock_conflict` | 409 | Active lock conflicts with selected resource. |
| `TABLE_INACTIVE` | `walkin.direct_seating.table_inactive` | 409 | Selected table is inactive and cannot be seated. |
| `TABLE_GROUP_INVALID` | `walkin.direct_seating.table_group_invalid` | 409 | Selected existing TableGroup is inactive, invalid, empty, cross-scope, or otherwise unusable. |
| `SEATING_SOURCE_INVALID` | `walkin.direct_seating.seating_source_invalid` | 409 | Seating source XOR rule failed. |
| `SEATING_RESOURCE_INVALID` | `walkin.direct_seating.seating_resource_invalid` | 409 | SeatingResource target XOR or resource scope rule failed. |
| `OVERRIDE_REASON_REQUIRED` | `walkin.direct_seating.override_reason_required` | 400 | Staff selected a valid non-recommended resource without override reason or note. |
| `IDEMPOTENCY_CONFLICT` | `walkin.direct_seating.idempotency_conflict` | 409 | Same idempotency key was reused with different request hash. |
| `IDEMPOTENCY_IN_PROGRESS` | `walkin.direct_seating.idempotency_in_progress` | 409 or 425 | Same command is already started and not completed. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `walkin.direct_seating.idempotency_failed_requires_new_key` | 409 | Same failed idempotency key cannot be reused in V1. |
| `ILLEGAL_STATE_TRANSITION` | `walkin.direct_seating.illegal_state_transition` | 409 | A required state transition is not legal. |
| `AUDIT_WRITE_FAILED` | `walkin.direct_seating.audit_write_failed` | 500 | Critical audit write failed; command must not be treated as completed. |
| `PERSISTENCE_ERROR` | `walkin.direct_seating.persistence_error` | 500 | Persistence failed without a more specific safe API error. |

## Application Error Mapping

Suggested mapping from `WalkInDirectSeatingError` to API error code:

| Application Error | API Error Code |
| --- | --- |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` |
| `INVALID_COMMAND` | `RESOURCE_CONFLICT` or validation-specific code |
| `INVALID_PARTY_SIZE` | `INVALID_PARTY_SIZE` |
| `INVALID_CUSTOMER_IDENTITY` | `INVALID_CUSTOMER_IDENTITY` or `INVALID_PHONE_E164` when caused by phone format |
| `INVALID_RESOURCE_SELECTION` | `RESOURCE_CONFLICT` |
| `NO_ASSIGNABLE_TABLE` | `TABLE_NOT_AVAILABLE` |
| `TABLE_RESOURCE_UNAVAILABLE` | `TABLE_NOT_AVAILABLE` or `TABLE_INACTIVE` when table status is inactive |
| `PARTY_SIZE_OUTSIDE_CAPACITY` | `TABLE_CAPACITY_INSUFFICIENT` |
| `TABLE_LOCK_CONFLICT` | `TABLE_LOCK_CONFLICT` |
| `INVALID_TABLE_GROUP` | `TABLE_GROUP_INVALID` |
| `MANUAL_OVERRIDE_REQUIRED` | `OVERRIDE_REASON_REQUIRED` |
| `INVALID_SEATING_SOURCE` | `SEATING_SOURCE_INVALID` |
| `INVALID_SEATING_RESOURCE` | `SEATING_RESOURCE_INVALID` |
| `IDEMPOTENCY_CONFLICT` | `IDEMPOTENCY_CONFLICT` |
| `COMMAND_IN_PROGRESS` | `IDEMPOTENCY_IN_PROGRESS` |
| `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` |
| `ILLEGAL_STATE_TRANSITION` | `ILLEGAL_STATE_TRANSITION` |
| `AUDIT_WRITE_FAILED` | `AUDIT_WRITE_FAILED` |
| `REPOSITORY_SAVE_FAILED` | `PERSISTENCE_ERROR` |

## HTTP Status Mapping

| Scenario | HTTP Status |
| --- | --- |
| Validation error | `400 Bad Request` |
| Missing or invalid JWT | `401 Unauthorized` |
| Role, permission, or scope denied | `403 Forbidden` |
| Store not found | `404 Not Found` |
| Resource conflict, lock conflict, illegal state, idempotency conflict | `409 Conflict` |
| Idempotency in progress | `409 Conflict` or `425 Too Early` |
| Audit or persistence failure | `500 Internal Server Error` |

## I18n Rule

- API error responses carry `messageKey`.
- API error responses do not carry hardcoded display text.
- Clients may resolve text through Store locale or call a later i18n catalog endpoint.
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
