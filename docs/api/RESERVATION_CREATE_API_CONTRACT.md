# Reservation Create API Contract V1

## Purpose

This document defines the API contract for the Create Reservation vertical slice:

```text
Store staff creates reservation
-> hold Store + business date + time range + party-size capacity
-> create confirmed Reservation
-> write reservation.created / reservation.confirmed
-> write StateTransitionLog / AuditLog
-> apply Idempotency
```

This is a contract document only. It does not implement Controller, REST endpoint, API DTO Java class, Vue UI, CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, migration, SQL, seed data, production configuration, or business data.

## Read Inputs

- `docs/backend/RESERVATION_CREATE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_CREATE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_CONTRACT.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_VERTICAL_SLICE_CHECKLIST.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/CLEANING_COMPLETE_API_CONTRACT.md`

Previous implementation confirmation:

- `ReservationCreateApplicationService` implemented.
- `CreateReservationCommand` implemented.
- Capacity fallback implemented: 50 guests / overlapping time window / Store.
- `reservedEndAt` derivation implemented.
- `holdUntilAt` derivation implemented.
- `reservationCode` generation implemented.
- Idempotency implemented.
- Events / transition / audit implemented.
- `mvn test` passed: 194 tests, 0 failures, 0 errors.
- CheckIn implemented: No.
- Queue implemented: No.
- Seating implemented: No.
- No-show implemented: No.
- Cancellation implemented: No.
- Table assignment implemented: No.
- Controller created: No.
- API DTO created: No.
- API implemented: No.
- UI implemented: No.
- Migration changed: No.

## Endpoint

Recommended endpoint:

```text
POST /api/v1/stores/{storeId}/reservations
```

Purpose:

- Store staff creates a future confirmed Reservation.
- The API maps external request data to `CreateReservationCommand` in a later implementation round.
- The API must not create QueueTicket, Seating, TableLock, ReservationPreassignment, CheckIn, No-show, Cancellation, or UI state.

Permission key:

```text
reservation.create
```

## Auth / Role / Permission Contract

Authentication:

- JWT authentication or the current server-side actor context is required.
- The authenticated context must provide Tenant scope.
- The authenticated context must provide actor identity, actor type, role, and permission context.
- The server must verify that path `storeId` belongs to the authenticated Tenant scope and actor Store scope.

Allowed roles:

- `tenant_admin`
- `store_manager`
- `store_staff`

Forbidden roles:

- `customer`
- `integration_app`

Scope rules:

- `tenantId` must come from JWT or server-side security context.
- `tenantId` must not be accepted from request body as a trusted source.
- `storeId` comes from the path.
- `actorId`, `actorType`, role, and permission come from server context.
- Cross-Tenant and cross-Store references must be rejected.

## Path Params

| Param | Required | Type | Meaning |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | Store operation boundary for the Reservation create command. |

## Headers

| Header | Required | Meaning |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Authenticates actor, Tenant scope, roles, and permissions. |
| `Idempotency-Key: <key>` | Yes | Deduplicates the critical create Reservation command. |
| `Accept-Language` | Recommended | Locale preference. Final display still follows Store locale fallback rules. |
| `Content-Type: application/json` | Yes | JSON request body. |

## Request Body

Example:

```json
{
  "partySize": 4,
  "reservedStartAt": "2026-06-20T11:00:00Z",
  "reservedEndAt": null,
  "customerId": null,
  "customerName": "Guest",
  "customerNickname": "Boss friend",
  "phoneE164": "+6591234567",
  "note": "Window seat if possible"
}
```

Field contract:

| Field | Required | Type | Rule |
| --- | ---: | --- | --- |
| `partySize` | Yes | integer | Must be greater than 0. |
| `reservedStartAt` | Yes | string | ISO8601 instant. Must not be in the past according to server/Store policy. |
| `reservedEndAt` | No | string or null | ISO8601 instant if present. If present, must be after `reservedStartAt`. |
| `customerId` | No | UUID or null | Existing Tenant-scoped Customer. Must belong to authenticated Tenant if present. |
| `customerName` | No | string or null | Customer identity hint for temporary/no-phone flows. |
| `customerNickname` | No | string or null | Optional lookup or staff context. |
| `phoneE164` | No | string or null | Must be valid E.164 if present. Phone is not required. |
| `note` | No | string or null | Staff/customer note. Not display copy. |

Structural rules:

- `partySize` is required and must be positive.
- `reservedStartAt` is required and exchanged as ISO8601.
- `reservedEndAt` is optional in API V1.
- If `reservedEndAt` is missing, the application derives it from `StorePolicy.expectedDiningMinutes`.
- If `reservedEndAt` is present, it must be after `reservedStartAt`.
- `phoneE164` is optional; if present it must pass E.164 validation.
- `customerName` and `customerNickname` are accepted as customer identity hints. Persistence behavior follows the current `CustomerIdentityRule` and `ReservationCreateApplicationService`.
- Request body must not contain trusted `tenantId`, `storeId`, `actorId`, `actorType`, role, permission, or Store scope.

Forbidden request fields:

- `tenantId`
- `queueTicketId`
- `seatingId`
- `tableId`
- `tableGroupId`
- `checkInAt`
- `noShowAt`
- `cancelledAt`

Reason:

Create Reservation V1 does not create Queue, does not seat the guest, does not check in the guest, does not mark no-show/cancelled, and does not preassign or lock a concrete table resource.

## Application Command Mapping

Later Controller/API implementation should map:

| API Source | Application Command Field |
| --- | --- |
| JWT/server Tenant scope | `tenantId` |
| Path `{storeId}` | `storeId` |
| Body `partySize` | `partySize` |
| Body `reservedStartAt` | `reservedStartAt` |
| Body `reservedEndAt` | `reservedEndAt` |
| Body `customerId` | `customerId` |
| Body `customerName` | `customerName` |
| Body `customerNickname` | `customerNickname` |
| Body `phoneE164` | `phoneE164` |
| Body `note` | `note` |
| Header `Idempotency-Key` | `idempotencyKey` |
| JWT/server actor id | `actorId` |
| JWT/server actor type | `actorType` |
| Server source | `source`, normally `staff` |
| Body/API future reason code | `reasonCode`, omitted in V1 public body unless later approved |

The API request and response are future API DTOs. They must not become Domain Objects, persistence Entities, Repository objects, or Mapper objects.

## Success Response

Recommended `201 Created` response:

```json
{
  "success": true,
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260620-0007",
  "status": "confirmed",
  "partySize": 4,
  "reservedStartAt": "2026-06-20T11:00:00Z",
  "reservedEndAt": "2026-06-20T12:30:00Z",
  "holdUntilAt": "2026-06-20T11:15:00Z",
  "businessDate": "2026-06-20",
  "customer": {
    "id": "00000000-0000-0000-0000-000000000002",
    "displayName": "Guest",
    "phoneE164": "+6591234567"
  },
  "events": [
    "reservation.created",
    "reservation.confirmed"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Response rules:

- Response is an API DTO contract, not a Domain Object.
- Response must return final `reservedStartAt` and `reservedEndAt`.
- If request omitted `reservedEndAt`, response returns the derived end time.
- Response must return `holdUntilAt`.
- Response must return `reservationCode`.
- `customer.displayName` is an API projection/hint, not a Customer domain field guarantee in V1.
- Do not expose JPA Entity fields.
- Do not expose Repository, Mapper, persistence, or transaction details.
- Do not expose full AuditLog metadata.
- Do not expose full StateTransitionLog metadata.
- Do not expose internal capacity calculation details.
- Do not expose fallback capacity value unless safe `details` on an error need it.

Completed replay response:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- Must not create a second Reservation or append duplicate critical events.

## Error Response

Recommended envelope:

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

Rules:

- `error.code` is stable and machine-readable.
- `error.messageKey` is required for i18n.
- `error.details` is an object and should remain safe for clients.
- Do not hardcode display text in the API response.
- The API layer may map internal application errors to public API error codes.

## Error Code Mapping

| API Error Code | Suggested Message Key | Typical HTTP Status |
| --- | --- | --- |
| `STORE_NOT_FOUND` | `reservation.store_not_found` | 404 |
| `STORE_SCOPE_MISMATCH` | `reservation.store_scope_mismatch` | 403 |
| `FORBIDDEN` | `reservation.forbidden` | 403 |
| `MISSING_IDEMPOTENCY_KEY` | `reservation.missing_idempotency_key` | 400 |
| `IDEMPOTENCY_CONFLICT` | `reservation.idempotency_conflict` | 409 |
| `IDEMPOTENCY_IN_PROGRESS` | `reservation.idempotency_in_progress` | 409 |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `reservation.idempotency_failed_requires_new_key` | 409 |
| `INVALID_PARTY_SIZE` | `reservation.invalid_party_size` | 400 |
| `INVALID_TIME_RANGE` | `reservation.invalid_time_range` | 400 |
| `RESERVATION_START_IN_PAST` | `reservation.start_in_past` | 400 |
| `INVALID_PHONE_E164` | `reservation.invalid_phone_e164` | 400 |
| `CUSTOMER_NOT_FOUND` | `reservation.customer_not_found` | 404 |
| `INVALID_CUSTOMER_IDENTITY` | `reservation.invalid_customer_identity` | 400 |
| `RESERVATION_DUPLICATE_ACTIVE` | `reservation.duplicate_active` | 409 |
| `RESERVATION_CAPACITY_INSUFFICIENT` | `reservation.capacity_insufficient` | 409 |
| `RESERVATION_CODE_CONFLICT` | `reservation.code_conflict` | 409 |
| `RESERVATION_POLICY_NOT_FOUND` | `reservation.policy_not_found` | 409 |
| `AUDIT_WRITE_FAILED` | `reservation.audit_write_failed` | 500 |
| `EVENT_WRITE_FAILED` | `reservation.event_write_failed` | 500 |
| `STATE_TRANSITION_WRITE_FAILED` | `reservation.state_transition_write_failed` | 500 |
| `PERSISTENCE_ERROR` | `reservation.persistence_error` | 500 |

## Status Code Recommendation

| Scenario | HTTP Status |
| --- | --- |
| Success | `201 Created` |
| Completed replay | `200 OK` |
| Validation error | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden or scope mismatch | `403 Forbidden` |
| Store or Customer not found | `404 Not Found` |
| Duplicate, capacity, reservation code, or idempotency conflict | `409 Conflict` |
| Idempotency in progress | `409 Conflict` |
| Audit, event, transition, or persistence error | `500 Internal Server Error` |

## Idempotency Behavior

Header:

```text
Idempotency-Key: <key>
```

Action:

```text
create_reservation
```

Rules:

- Header is required.
- Request hash includes normalized Tenant scope from JWT/server context, Store id from path, Reservation request body business intent, source, and actor type.
- Same key + same hash + completed returns previous success response with `replayed = true`.
- Same key + same hash + in-progress returns `IDEMPOTENCY_IN_PROGRESS`.
- Same key + same hash + failed returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key + different hash returns `IDEMPOTENCY_CONFLICT`.
- Missing key returns `MISSING_IDEMPOTENCY_KEY`.

V1 confirmed decision:

- Failed idempotency requires a new key.
- `retryable_failure` is deferred and not part of this contract.
- If current persistence snapshot is not enough for full API replay, the API implementation round may add the minimum mapping/snapshot support required without changing this contract.

## Capacity Behavior

API V1 does not expose capacity policy or capacity calculation internals.

Application behavior remains:

```text
capacityLimit = 50 guests / overlapping time window / store
```

until a later schema and policy round introduces explicit StorePolicy capacity fields.

If capacity is insufficient:

- Error code: `RESERVATION_CAPACITY_INSUFFICIENT`
- Message key: `reservation.capacity_insufficient`
- HTTP status: `409 Conflict`

The endpoint must not lock or assign a concrete table as part of capacity checking.

## Audit / Event Exposure

API response may expose these short event codes:

- `reservation.created`
- `reservation.confirmed`

API response must not expose:

- Full AuditLog records.
- Full StateTransitionLog records.
- Internal metadata payloads.
- Raw persistence snapshots.
- Internal capacity usage or fallback calculations.

Full audit, event, and transition timelines belong to later backend/admin query APIs.

## Test Contract

Future API contract tests should cover:

### Success

- Create reservation with existing Customer.
- Create reservation with phone Customer.
- Create reservation with no-phone temporary Customer.
- `reservedEndAt` omitted and derived.
- `holdUntilAt` returned.
- `reservationCode` returned.
- Completed idempotency replay returns `200 OK` and `idempotency.replayed = true`.

### Validation / Error

- Missing `Idempotency-Key`.
- Invalid `partySize`.
- Invalid time range.
- Start in the past.
- Invalid phone.
- Customer not found.
- Duplicate active Reservation.
- Capacity insufficient.
- Reservation code conflict if applicable.
- Idempotency in progress.
- Failed key requires new key.
- Hash conflict.
- Forbidden role.
- Store scope mismatch.

### Boundary

- No CheckIn created.
- No QueueTicket created.
- No Seating created.
- No TableLock created.
- No ReservationPreassignment created.
- No Reservation UI.
- No Queue UI.
- No Migration change.

No test code is created in this round.

## Non-Scope

This API contract does not design or implement:

- Reservation CheckIn API.
- Reservation Cancel API.
- Reservation No-show API.
- Reservation Seating API.
- Queue API.
- Table assignment API.
- Reservation list/search API.
- Reservation calendar API.
- Controller implementation.
- REST endpoint implementation.
- API DTO Java class.
- OpenAPI file generation.
- Vue UI.
- Repository, Mapper, Entity, Application Service, Migration, SQL, seed data, or mock runtime data.

## Next Implementation Notes

Later API implementation should:

- Create Controller only in an approved API implementation round.
- Create API DTO Java classes only in an approved API implementation round.
- Map API request to `CreateReservationCommand` without leaking DTOs into Domain.
- Map `ReservationCreateResult` to this API response envelope.
- Map `ReservationCreateError` to public API error codes and i18n message keys.
- Enforce JWT/server actor context, TenantScope, StoreScope, RBAC, and permission `reservation.create`.
- Preserve idempotency exactly as documented here.
- Keep CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, TableLock, ReservationPreassignment, list/search, calendar, and UI out of scope.
