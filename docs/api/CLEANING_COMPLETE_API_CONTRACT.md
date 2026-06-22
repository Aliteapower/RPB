# Cleaning Complete API Contract V1

## Purpose

This document defines the API contract for the Cleaning Complete vertical slice:

```text
Seating occupied
-> Start Cleaning
-> Table occupied -> cleaning
-> Complete Cleaning
-> Table cleaning -> available
```

This is a contract document only. It does not implement a Controller, REST endpoint, API DTO Java class, Vue UI, Reservation API, Queue API, Turnover API, migration, SQL, seed data, production configuration, or business data.

## Read Inputs

- `docs/backend/CLEANING_COMPLETE_APPLICATION_CONTRACT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Previous implementation confirmation:

- `CleaningApplicationService` implemented.
- `StartCleaningCommand` implemented.
- `CompleteCleaningCommand` implemented.
- `mvn test` passed: 120 tests, 0 failures, 0 errors.
- Controller created: No.
- API DTO created: No.
- API implemented: No.
- UI implemented: No.
- Reservation implemented: No.
- Queue implemented: No.
- Turnover BI implemented: No.
- Migration changed: No.

## Endpoints

### Start Cleaning

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
```

Purpose:

- Store staff confirms that the occupied resource related to a Seating has entered cleaning.
- The resource is derived from `seatingId`.
- The request body must not carry `tableId` or `tableGroupId`.

### Complete Cleaning

```text
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

Purpose:

- Store staff confirms that the Cleaning workflow is finished.
- The resource is derived from `cleaningId`.
- V1 ends with Cleaning status `released` and Table status `available`.
- The request body must not carry `tableId` or `tableGroupId`.

## Auth / Role / Permission Contract

Authentication:

- JWT authentication or the current server-side actor context is required.
- The authenticated context must provide Tenant scope.
- The authenticated context must provide actor identity, actor type, role, and permission context.
- The server must verify the path `storeId` belongs to the authenticated Tenant scope and actor Store scope.

Allowed roles:

- `tenant_admin`
- `store_manager`
- `store_staff`

Forbidden roles:

- `customer`
- `integration_app`

Permission keys:

| Endpoint | Permission |
| --- | --- |
| Start Cleaning | `cleaning.start` |
| Complete Cleaning | `cleaning.complete` |

Scope rules:

- `tenantId` must come from JWT or server-side security context.
- `tenantId` must not be accepted from request body as a trusted source.
- `storeId` comes from the path.
- `seatingId` and `cleaningId` come from the path.
- Cross-Tenant and cross-Store references must be rejected.

## Path Params

| Param | Endpoint | Required | Type | Meaning |
| --- | --- | ---: | --- | --- |
| `storeId` | Both | Yes | UUID | Store operation boundary. |
| `seatingId` | Start Cleaning | Yes | UUID | Seating used to derive the occupied resource. |
| `cleaningId` | Complete Cleaning | Yes | UUID | Cleaning workflow used to derive the resource to release. |

## Headers

| Header | Required | Meaning |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Authenticates actor, Tenant scope, roles, and permissions. |
| `Idempotency-Key: <key>` | Yes | Deduplicates the critical Cleaning command. |
| `Accept-Language` | Recommended | Locale preference. Final display still follows Store locale fallback rules. |
| `Content-Type: application/json` | Yes | JSON request body. |

## Start Cleaning Request Body

Example:

```json
{
  "reasonCode": null,
  "note": null
}
```

Field contract:

| Field | Required | Type | Rule |
| --- | ---: | --- | --- |
| `reasonCode` | No | string or null | Optional reason code for staff action or table release policy. |
| `note` | No | string or null | Optional staff note; not display copy. |

Rules:

- `storeId` comes from path.
- `seatingId` comes from path.
- `tenantId`, `actorId`, and `actorType` come from server-side context.
- `Idempotency-Key` is required.
- Caller must not pass `tableId`.
- Caller must not pass `tableGroupId`.
- Caller must not pass trusted `tenantId`, `actorId`, `actorType`, role, permission, or Store scope in the request body.

## Complete Cleaning Request Body

Example:

```json
{
  "reasonCode": null,
  "note": null
}
```

Field contract:

| Field | Required | Type | Rule |
| --- | ---: | --- | --- |
| `reasonCode` | No | string or null | Optional reason code for cleaning completion or table release. |
| `note` | No | string or null | Optional staff note; not display copy. |

Rules:

- `storeId` comes from path.
- `cleaningId` comes from path.
- `tenantId`, `actorId`, and `actorType` come from server-side context.
- `Idempotency-Key` is required.
- Caller must not pass `tableId`.
- Caller must not pass `tableGroupId`.
- Caller must not pass trusted `tenantId`, `actorId`, `actorType`, role, permission, or Store scope in the request body.

## Application Command Mapping

### Start Cleaning

| API Source | Application Command Field |
| --- | --- |
| JWT/server Tenant scope | `tenantId` |
| Path `{storeId}` | `storeId` |
| Path `{seatingId}` | `seatingId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| JWT/server actor id | `actorId` |
| JWT/server actor type | `actorType` |
| Body `reasonCode` | `reasonCode` |
| Body `note` | `note` |

### Complete Cleaning

| API Source | Application Command Field |
| --- | --- |
| JWT/server Tenant scope | `tenantId` |
| Path `{storeId}` | `storeId` |
| Path `{cleaningId}` | `cleaningId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| JWT/server actor id | `actorId` |
| JWT/server actor type | `actorType` |
| Body `reasonCode` | `reasonCode` |
| Body `note` | `note` |

The API request and response are future API DTOs. They must not become Domain Objects, persistence Entities, Repository objects, or Mapper objects.

## Start Cleaning Success Response

Recommended `201 Created` response:

```json
{
  "success": true,
  "cleaningId": "00000000-0000-0000-0000-000000000001",
  "seatingId": "00000000-0000-0000-0000-000000000002",
  "resource": {
    "type": "TABLE",
    "id": "00000000-0000-0000-0000-000000000003",
    "label": "A1"
  },
  "cleaningStatus": "cleaning",
  "tableStatus": "cleaning",
  "events": [
    "cleaning.started",
    "table.cleaning"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Completed replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.

## Complete Cleaning Success Response

Recommended `200 OK` response:

```json
{
  "success": true,
  "cleaningId": "00000000-0000-0000-0000-000000000001",
  "resource": {
    "type": "TABLE",
    "id": "00000000-0000-0000-0000-000000000003",
    "label": "A1"
  },
  "cleaningStatus": "released",
  "tableStatus": "available",
  "events": [
    "cleaning.completed",
    "table.available"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Completed replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.

Response rules:

- Response is an API DTO contract, not a Domain Object.
- Do not expose JPA Entity fields.
- Do not expose Repository, Mapper, persistence, or transaction details.
- Do not expose full AuditLog metadata.
- Do not expose full StateTransitionLog metadata.
- Event exposure is limited to short event codes useful to the caller.
- Store-local display labels such as table label are optional API projection fields resolved later by implementation.

Resource type values:

- `TABLE`
- `TABLE_GROUP`

## Error Response

Recommended envelope:

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

Rules:

- `error.code` is stable and machine-readable.
- `error.messageKey` is required for i18n.
- `error.details` is an object and should remain safe for clients.
- Do not hardcode display text in the API response.
- The API layer may map internal application errors to public API error codes.

## Error Code Mapping

| API Error Code | Suggested Message Key | Typical HTTP Status |
| --- | --- | --- |
| `STORE_NOT_FOUND` | `cleaning.store_not_found` | 404 |
| `STORE_SCOPE_MISMATCH` | `cleaning.store_scope_mismatch` | 403 |
| `FORBIDDEN` | `cleaning.forbidden` | 403 |
| `MISSING_IDEMPOTENCY_KEY` | `cleaning.missing_idempotency_key` | 400 |
| `IDEMPOTENCY_CONFLICT` | `cleaning.idempotency_conflict` | 409 |
| `IDEMPOTENCY_IN_PROGRESS` | `cleaning.idempotency_in_progress` | 409 or 425 |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `cleaning.idempotency_failed_requires_new_key` | 409 |
| `SEATING_NOT_FOUND` | `cleaning.seating_not_found` | 404 |
| `SEATING_RESOURCE_NOT_FOUND` | `cleaning.seating_resource_not_found` | 409 |
| `CLEANING_NOT_FOUND` | `cleaning.not_found` | 404 |
| `CLEANING_ALREADY_ACTIVE` | `cleaning.already_active` | 409 |
| `CLEANING_ALREADY_COMPLETED` | `cleaning.already_completed` | 409 |
| `CLEANING_TARGET_INVALID` | `cleaning.target_invalid` | 409 |
| `TABLE_NOT_FOUND` | `cleaning.table_not_found` | 404 |
| `TABLE_GROUP_INVALID` | `cleaning.table_group_invalid` | 409 |
| `TABLE_NOT_OCCUPIED` | `cleaning.table_not_occupied` | 409 |
| `TABLE_NOT_CLEANING` | `cleaning.table_not_cleaning` | 409 |
| `ILLEGAL_STATE_TRANSITION` | `cleaning.illegal_state_transition` | 409 |
| `AUDIT_WRITE_FAILED` | `cleaning.audit_write_failed` | 500 |
| `PERSISTENCE_ERROR` | `cleaning.persistence_error` | 500 |

## Status Code Recommendation

| Scenario | HTTP Status |
| --- | --- |
| Start cleaning success | `201 Created` |
| Complete cleaning success | `200 OK` |
| Completed replay | `200 OK` |
| Validation error | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden or scope mismatch | `403 Forbidden` |
| Seating / Cleaning / Table not found | `404 Not Found` |
| State conflict | `409 Conflict` |
| Idempotency in progress | `409 Conflict` or `425 Too Early` |
| Audit or persistence error | `500 Internal Server Error` |

## Idempotency Behavior

Header:

```text
Idempotency-Key: <key>
```

Actions:

- `start_cleaning`
- `complete_cleaning`

Rules:

- Header is required.
- Request hash includes normalized Tenant scope from JWT/server context, Store id from path, action, path id, actor type, reason code, and note.
- Same key + same hash + completed returns previous success response with `replayed = true`.
- Same key + same hash + in-progress returns `IDEMPOTENCY_IN_PROGRESS`.
- Same key + same hash + failed returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key + different hash returns `IDEMPOTENCY_CONFLICT`.
- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Completed replay must not write duplicate `cleaning.completed`, `table.available`, state transition, audit, or table status changes.

V1 confirmed decision:

- Failed idempotency requires a new key.
- `retryable_failure` is deferred and not part of this contract.

## Audit / Event Exposure

Start Cleaning response may expose:

- `cleaning.started`
- `table.cleaning`

Complete Cleaning response may expose:

- `cleaning.completed`
- `table.available`

API response must not expose:

- Full AuditLog records.
- Full StateTransitionLog records.
- Internal metadata payloads.
- Raw persistence snapshots.
- Turnover BI data.

Full audit, event, and transition timelines belong to later backend/admin query APIs.

## Test Contract

Future API contract tests should cover:

### Start Cleaning

- Success from `seatingId` with table resource.
- Success from `seatingId` with TableGroup resource.
- Missing `Idempotency-Key`.
- Seating not found.
- Seating resource not found.
- Table not occupied.
- TableGroup invalid.
- Cleaning already active.
- Forbidden role.
- Store scope mismatch.
- Idempotency completed replay.
- Idempotency in progress.
- Idempotency failed requires new key.
- Idempotency hash conflict.

### Complete Cleaning

- Success by `cleaningId` with table resource.
- Success by `cleaningId` with TableGroup resource.
- Missing `Idempotency-Key`.
- Cleaning not found.
- Cleaning already completed.
- Table not cleaning.
- TableGroup invalid.
- Forbidden role.
- Store scope mismatch.
- Idempotency completed replay.
- Idempotency in progress.
- Idempotency failed requires new key.
- Idempotency hash conflict.

### Boundary

- No Reservation API.
- No Queue API.
- No Turnover API.
- No Vue UI.
- No migration change.

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
- Turnover API.
- Table management API.
- Payment, POS, Marketing, Member.
- Repository, Mapper, Entity, Application Service, Migration, SQL, seed data, or mock runtime data.

## Next Implementation Notes

Later API implementation should:

- Create Controllers only in an approved API implementation round.
- Create API DTO Java classes only in an approved API implementation round.
- Map API requests to `StartCleaningCommand` and `CompleteCleaningCommand`.
- Map `CleaningApplicationResult` to the API response envelope.
- Map application errors to API error codes and i18n message keys.
- Enforce JWT/server actor context, TenantScope, StoreScope, RBAC, and permission keys.
- Preserve idempotency exactly as documented here.
- Keep Turnover BI as a later projection/recorded slice.
