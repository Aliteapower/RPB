# WalkIn Direct Seating API Contract V1

## Purpose

This document defines the API contract for the first closed business loop exposed to store staff:

```text
WalkIn arrival
-> direct seating
-> occupy one DiningTable or existing TableGroup
-> persist WalkIn / Seating / SeatingResource
-> write BusinessEvent / StateTransitionLog / AuditLog
-> apply Idempotency
```

This is a contract document only. It does not implement a Controller, REST endpoint, API DTO Java class, Vue UI, Reservation API, Queue API, Cleaning API, Turnover API, migration, SQL, seed data, mock runtime data, Docker, CI/CD, or production configuration.

## Read Inputs

- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Previous implementation confirmation:

- `mvn test` passed.
- Tests: 56.
- Failures: 0.
- Errors: 0.
- Controller created: No.
- API DTO created: No.
- API implemented: No.
- UI implemented: No.
- Reservation implemented: No.
- Queue implemented: No.
- Cleaning implemented: No.
- Turnover implemented: No.
- Migration changed: No.

## Endpoint

Recommended endpoint:

```text
POST /api/v1/stores/{storeId}/walk-ins/direct-seating
```

Purpose:

- Store staff directly seats an on-site WalkIn party.
- The API maps external request data to `SeatWalkInDirectlyCommand` in a later implementation round.
- The API must not create Reservation, QueueTicket, Cleaning, or Turnover records.

## Auth / Role / Permission Contract

Authentication:

- JWT authentication is required.
- The JWT must provide tenant scope.
- The JWT must provide actor identity and role context.
- The JWT or server session must prove access to the path `storeId`.

Allowed roles:

- `tenant_admin`
- `store_manager`
- `store_staff`

Forbidden roles for this endpoint:

- `customer`
- `integration_app`

Permission key:

```text
walkin.direct_seating.create
```

Scope rules:

- `tenantId` must come from JWT or server-side security context.
- `tenantId` must not be accepted from request body as a trusted source.
- `storeId` comes from the path.
- The server must verify that `storeId` belongs to the authenticated tenant scope.
- Cross-Tenant and cross-Store resource references must be rejected.

## Path Params

| Param | Required | Type | Meaning |
| --- | --- | --- | --- |
| `storeId` | Yes | UUID | Store operation boundary for the direct seating command. |

## Headers

| Header | Required | Meaning |
| --- | --- | --- |
| `Authorization: Bearer <jwt>` | Yes | Authenticates actor, tenant scope, roles, and permissions. |
| `Idempotency-Key: <key>` | Yes | Deduplicates the critical direct seating command. |
| `Accept-Language` | Recommended | Locale preference. Final display still follows Store locale fallback rules. |
| `Content-Type: application/json` | Yes | JSON request body. |

## Request Body

Example:

```json
{
  "partySize": 2,
  "customerId": null,
  "customerName": "Guest",
  "customerNickname": "Boss friend",
  "phoneE164": "+6591234567",
  "tableId": null,
  "tableGroupId": null,
  "overrideReasonCode": null,
  "overrideNote": null
}
```

Field contract:

| Field | Required | Type | Rule |
| --- | --- | --- | --- |
| `partySize` | Yes | integer | Must be greater than 0. |
| `customerId` | No | UUID or null | Must belong to authenticated Tenant if present. |
| `customerName` | No | string or null | Temporary/no-phone customer context; not a unique identity by itself. |
| `customerNickname` | No | string or null | Optional lookup or staff context. |
| `phoneE164` | No | string or null | Must be valid E.164 if present. Phone is not required. |
| `tableId` | No | UUID or null | Staff-selected DiningTable. Mutually exclusive with `tableGroupId`. |
| `tableGroupId` | No | UUID or null | Staff-selected existing TableGroup. Mutually exclusive with `tableId`. |
| `overrideReasonCode` | No | string or null | Required with `overrideNote` alternative when selected resource is valid but not system-recommended. |
| `overrideNote` | No | string or null | Required with `overrideReasonCode` alternative when selected resource is valid but not system-recommended. |

Structural rules:

- `partySize` is required and must be positive.
- `phoneE164` is optional; if present it must pass E.164 validation.
- `tableId` and `tableGroupId` cannot both be present.
- If neither `tableId` nor `tableGroupId` is present, the application service may auto-select a valid resource.
- If staff manually selects a valid but non-recommended resource, `overrideReasonCode` or `overrideNote` is required.
- Request body must not contain trusted `tenantId`.
- Request body should not contain trusted `actorId`, `actorType`, role, permission, or Store scope.

## Application Command Mapping

Later Controller/API implementation should map:

| API Source | Application Command Field |
| --- | --- |
| JWT tenant scope | `tenantId` |
| Path `{storeId}` | `storeId` |
| Body `partySize` | `partySize` |
| Body `customerId` | `customerId` |
| Body `customerName` | `customerName` |
| Body `customerNickname` | `customerNickname` |
| Body `phoneE164` | `phoneE164` |
| Body `tableId` | `tableId` |
| Body `tableGroupId` | `tableGroupId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| JWT actor id | `actorId` |
| JWT/server actor type | `actorType` |
| Body `overrideReasonCode` | `overrideReasonCode` |
| Body `overrideNote` | `overrideNote` |

The API request and response are API DTOs in a future round. They must not become Domain Objects, persistence Entities, Repository objects, or Mapper objects.

## Success Response

Recommended `201 Created` response:

```json
{
  "success": true,
  "walkInId": "00000000-0000-0000-0000-000000000001",
  "seatingId": "00000000-0000-0000-0000-000000000002",
  "resource": {
    "type": "TABLE",
    "id": "00000000-0000-0000-0000-000000000003",
    "label": "A1"
  },
  "partySize": 2,
  "status": "occupied",
  "events": [
    "walk_in.created",
    "seating.created",
    "table.occupied"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Resource type values:

- `TABLE`
- `TABLE_GROUP`

Response rules:

- Response is an API DTO contract, not a Domain Object.
- Do not expose JPA Entity fields.
- Do not expose Repository, Mapper, persistence, or transaction details.
- Do not expose full AuditLog metadata.
- Do not expose full StateTransitionLog metadata.
- Event exposure is limited to short event codes useful to the caller.
- Store-local display labels such as table label are optional API projection fields resolved later by the implementation.

Completed replay response:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.

## Error Response

Recommended envelope:

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

Rules:

- `error.code` is stable and machine-readable.
- `error.messageKey` is required for i18n.
- `error.details` is an object and should remain safe for clients.
- Do not hardcode display text in the API response.
- The API layer may map internal application errors to public API error codes.

## Error Code Mapping

| API Error Code | Suggested Message Key | Typical HTTP Status |
| --- | --- | --- |
| `STORE_NOT_FOUND` | `walkin.direct_seating.store_not_found` | 404 |
| `STORE_SCOPE_MISMATCH` | `walkin.direct_seating.store_scope_mismatch` | 403 |
| `FORBIDDEN` | `walkin.direct_seating.forbidden` | 403 |
| `MISSING_IDEMPOTENCY_KEY` | `walkin.direct_seating.missing_idempotency_key` | 400 |
| `INVALID_PARTY_SIZE` | `walkin.direct_seating.invalid_party_size` | 400 |
| `INVALID_PHONE_E164` | `walkin.direct_seating.invalid_phone_e164` | 400 |
| `INVALID_CUSTOMER_IDENTITY` | `walkin.direct_seating.invalid_customer_identity` | 400 |
| `RESOURCE_CONFLICT` | `walkin.direct_seating.resource_conflict` | 400 or 409 |
| `TABLE_NOT_AVAILABLE` | `walkin.direct_seating.table_not_available` | 409 |
| `TABLE_CAPACITY_INSUFFICIENT` | `walkin.direct_seating.table_capacity_insufficient` | 409 |
| `TABLE_LOCK_CONFLICT` | `walkin.direct_seating.table_lock_conflict` | 409 |
| `TABLE_INACTIVE` | `walkin.direct_seating.table_inactive` | 409 |
| `TABLE_GROUP_INVALID` | `walkin.direct_seating.table_group_invalid` | 409 |
| `SEATING_SOURCE_INVALID` | `walkin.direct_seating.seating_source_invalid` | 409 |
| `SEATING_RESOURCE_INVALID` | `walkin.direct_seating.seating_resource_invalid` | 409 |
| `OVERRIDE_REASON_REQUIRED` | `walkin.direct_seating.override_reason_required` | 400 |
| `IDEMPOTENCY_CONFLICT` | `walkin.direct_seating.idempotency_conflict` | 409 |
| `IDEMPOTENCY_IN_PROGRESS` | `walkin.direct_seating.idempotency_in_progress` | 409 or 425 |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `walkin.direct_seating.idempotency_failed_requires_new_key` | 409 |
| `ILLEGAL_STATE_TRANSITION` | `walkin.direct_seating.illegal_state_transition` | 409 |
| `AUDIT_WRITE_FAILED` | `walkin.direct_seating.audit_write_failed` | 500 |
| `PERSISTENCE_ERROR` | `walkin.direct_seating.persistence_error` | 500 |

## Status Code Recommendation

| Scenario | HTTP Status |
| --- | --- |
| Success | `201 Created` |
| Completed replay | `200 OK` |
| Validation error | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden or scope mismatch | `403 Forbidden` |
| Store not found | `404 Not Found` |
| Table conflict or lock conflict | `409 Conflict` |
| Idempotency in progress | `409 Conflict` or `425 Too Early` |
| Persistence error | `500 Internal Server Error` |

## Idempotency Behavior

Header:

```text
Idempotency-Key: <key>
```

Rules:

- Header is required.
- Request hash includes normalized tenant scope from JWT, store id from path, request body business intent, and actor type.
- Same key + same hash + completed returns previous success response with `replayed = true`.
- Same key + same hash + in-progress returns `IDEMPOTENCY_IN_PROGRESS`.
- Same key + same hash + failed returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key + different hash returns `IDEMPOTENCY_CONFLICT`.
- Missing key returns `MISSING_IDEMPOTENCY_KEY`.

V1 confirmed decision:

- Failed idempotency requires a new key.
- Future `retryable_failure` is deferred and not part of this contract.

## Audit / Event Exposure

API response may expose these short event codes:

- `walk_in.created`
- `seating.created`
- `table.occupied`

API response must not expose:

- Full AuditLog records.
- Full StateTransitionLog records.
- Internal metadata payloads.
- Raw persistence snapshots.

Full audit, event, and transition timelines belong to later backend/admin query APIs.

## Test Contract

Future API contract tests should cover:

- Success with no-phone customer.
- Success with specified table.
- Success with existing table group.
- Success with auto-selected table.
- Validation: invalid party size.
- Validation: invalid phone.
- Validation: `tableId` and `tableGroupId` both present.
- Conflict: table locked.
- Conflict: table inactive.
- Conflict: capacity insufficient.
- Conflict: invalid table group.
- Override missing.
- Idempotency completed replay.
- Idempotency in progress.
- Idempotency failed requires new key.
- Idempotency hash conflict.
- Forbidden role.
- Store scope mismatch.

No test code is created in this round.

## Non-Scope

This round does not design or implement:

- Controller implementation.
- REST endpoint implementation.
- API DTO Java class.
- OpenAPI file generation.
- Vue UI.
- Reservation API.
- Queue API.
- Cleaning API.
- Turnover API.
- Customer search API.
- Table management API.
- Repository, Mapper, Entity, Application Service, Migration, SQL, seed data, or mock runtime data.

## Next Implementation Notes

Later API implementation should:

- Create a Controller only in an approved API implementation round.
- Create API DTO Java classes only in an approved API implementation round.
- Map API DTOs to `SeatWalkInDirectlyCommand` without leaking DTOs into Domain.
- Map `WalkInDirectSeatingResult` to the API response envelope.
- Map application errors to API error codes and i18n message keys.
- Enforce JWT, TenantScope, StoreScope, RBAC, and `walkin.direct_seating.create`.
- Preserve idempotency exactly as documented here.
