# Cleaning API Error Contract V1

## Purpose

This document defines the Cleaning-specific API error contract for:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

This is documentation only. It does not create Controller, API DTO Java class, REST endpoint, code, tests, migration, SQL, UI, seed data, mock runtime data, or production configuration.

## Common Error Envelope

Recommended error body:

```json
{
  "success": false,
  "error": {
    "code": "CLEANING_NOT_FOUND",
    "messageKey": "cleaning.not_found",
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
- Do not expose database constraint names, table names, stack traces, SQL, Entity names, Repository details, or mapper details.
- Do not put user-facing display text in `error.code`.

## Message Key Convention

Cleaning message key namespace:

```text
cleaning.<error_key>
```

Examples:

- `cleaning.table_not_occupied`
- `cleaning.table_not_cleaning`
- `cleaning.idempotency_conflict`

Rules:

- `messageKey` is required for every error.
- API responses must not hardcode display messages.
- Store locale, Tenant fallback, and platform fallback are future i18n resolution responsibilities.
- API may include `details` for interpolation, but clients must still resolve display text by key.

## Details Object Convention

`details` must be an object.

Allowed examples:

```json
{
  "field": "Idempotency-Key"
}
```

```json
{
  "resourceType": "TABLE",
  "resourceId": "00000000-0000-0000-0000-000000000003",
  "expectedStatus": "cleaning",
  "actualStatus": "occupied"
}
```

Forbidden details:

- Stack traces.
- SQL strings.
- Persistence Entity class names.
- Repository method names.
- Mapper names.
- Full AuditLog metadata.
- Full JWT contents.
- Internal security policy implementation details.
- Sensitive staff notes beyond the field-level reason needed by the client.

## Cleaning Error Codes

| API Error Code | Message Key | HTTP Status | Meaning |
| --- | --- | --- | --- |
| `STORE_NOT_FOUND` | `cleaning.store_not_found` | 404 | Path Store does not exist in authenticated Tenant scope. |
| `STORE_SCOPE_MISMATCH` | `cleaning.store_scope_mismatch` | 403 | Store or resource does not belong to authenticated Tenant/Store scope. |
| `FORBIDDEN` | `cleaning.forbidden` | 403 | Authenticated actor lacks role or permission. |
| `MISSING_IDEMPOTENCY_KEY` | `cleaning.missing_idempotency_key` | 400 | Required `Idempotency-Key` header is absent or blank. |
| `IDEMPOTENCY_CONFLICT` | `cleaning.idempotency_conflict` | 409 | Same idempotency key was reused with different request hash. |
| `IDEMPOTENCY_IN_PROGRESS` | `cleaning.idempotency_in_progress` | 409 or 425 | Same command is already started and not completed. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `cleaning.idempotency_failed_requires_new_key` | 409 | Same failed idempotency key cannot be reused in V1. |
| `SEATING_NOT_FOUND` | `cleaning.seating_not_found` | 404 | Start Cleaning references a missing or out-of-scope Seating. |
| `SEATING_RESOURCE_NOT_FOUND` | `cleaning.seating_resource_not_found` | 409 | Seating has no active resource from which Cleaning can derive target. |
| `CLEANING_NOT_FOUND` | `cleaning.not_found` | 404 | Complete Cleaning references a missing or out-of-scope Cleaning. |
| `CLEANING_ALREADY_ACTIVE` | `cleaning.already_active` | 409 | Resource already has an active Cleaning. |
| `CLEANING_ALREADY_COMPLETED` | `cleaning.already_completed` | 409 | Cleaning is already completed or released outside completed replay. |
| `CLEANING_TARGET_INVALID` | `cleaning.target_invalid` | 409 | Cleaning resource target is missing, ambiguous, cross-scope, or unsupported. |
| `TABLE_NOT_FOUND` | `cleaning.table_not_found` | 404 | Derived DiningTable resource is missing or out of scope. |
| `TABLE_GROUP_INVALID` | `cleaning.table_group_invalid` | 409 | Derived TableGroup is inactive, invalid, empty, cross-scope, or otherwise unusable. |
| `TABLE_NOT_OCCUPIED` | `cleaning.table_not_occupied` | 409 | Start Cleaning requires an occupied table resource. |
| `TABLE_NOT_CLEANING` | `cleaning.table_not_cleaning` | 409 | Complete Cleaning requires a table resource in cleaning status. |
| `ILLEGAL_STATE_TRANSITION` | `cleaning.illegal_state_transition` | 409 | Required table or Cleaning state transition is not legal. |
| `AUDIT_WRITE_FAILED` | `cleaning.audit_write_failed` | 500 | Critical audit write failed; command must not be treated as completed. |
| `PERSISTENCE_ERROR` | `cleaning.persistence_error` | 500 | Persistence failed without a more specific safe API error. |

Optional implementation-specific public mappings may later include:

| API Error Code | Message Key | HTTP Status | Meaning |
| --- | --- | --- | --- |
| `BUSINESS_EVENT_WRITE_FAILED` | `cleaning.business_event_write_failed` | 500 | Critical event write failed. |
| `STATE_TRANSITION_WRITE_FAILED` | `cleaning.state_transition_write_failed` | 500 | Critical state transition log write failed. |

These optional codes may also be folded into `PERSISTENCE_ERROR` if the implementation keeps the public API smaller.

## Application Error Mapping

Suggested mapping from `CleaningApplicationError` to API error code:

| Application Error | API Error Code |
| --- | --- |
| `INVALID_COMMAND` | `MISSING_IDEMPOTENCY_KEY` or validation-specific `400` error when caused by missing path/header input |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` |
| `SEATING_NOT_FOUND` | `SEATING_NOT_FOUND` |
| `SEATING_RESOURCE_NOT_FOUND` | `SEATING_RESOURCE_NOT_FOUND` |
| `TABLE_NOT_FOUND` | `TABLE_NOT_FOUND` |
| `INVALID_TABLE_GROUP` | `TABLE_GROUP_INVALID` |
| `TABLE_NOT_OCCUPIED` | `TABLE_NOT_OCCUPIED` |
| `CLEANING_ALREADY_ACTIVE` | `CLEANING_ALREADY_ACTIVE` |
| `CLEANING_NOT_FOUND` | `CLEANING_NOT_FOUND` |
| `CLEANING_ALREADY_COMPLETED` | `CLEANING_ALREADY_COMPLETED` |
| `TABLE_NOT_CLEANING` | `TABLE_NOT_CLEANING` |
| `RESOURCE_TARGET_INVALID` | `CLEANING_TARGET_INVALID` |
| `IDEMPOTENCY_CONFLICT` | `IDEMPOTENCY_CONFLICT` |
| `COMMAND_IN_PROGRESS` | `IDEMPOTENCY_IN_PROGRESS` |
| `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` |
| `ILLEGAL_STATE_TRANSITION` | `ILLEGAL_STATE_TRANSITION` |
| `AUDIT_WRITE_FAILED` | `AUDIT_WRITE_FAILED` |
| `BUSINESS_EVENT_WRITE_FAILED` | `PERSISTENCE_ERROR` or `BUSINESS_EVENT_WRITE_FAILED` |
| `STATE_TRANSITION_WRITE_FAILED` | `PERSISTENCE_ERROR` or `STATE_TRANSITION_WRITE_FAILED` |
| `REPOSITORY_SAVE_FAILED` | `PERSISTENCE_ERROR` |

## HTTP Status Mapping

| Scenario | HTTP Status |
| --- | --- |
| Validation error | `400 Bad Request` |
| Missing or invalid JWT | `401 Unauthorized` |
| Role, permission, or scope denied | `403 Forbidden` |
| Store, Seating, Cleaning, or Table not found | `404 Not Found` |
| Resource state conflict, illegal transition, idempotency conflict | `409 Conflict` |
| Idempotency in progress | `409 Conflict` or `425 Too Early` |
| Audit, event, transition, or persistence failure | `500 Internal Server Error` |

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
- Reservation, Queue, Turnover, Payment, POS, Marketing, or Member API errors.
