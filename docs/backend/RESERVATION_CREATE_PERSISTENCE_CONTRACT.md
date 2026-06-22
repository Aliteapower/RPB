# Reservation Create Persistence Contract V1

## 1. Purpose

This document defines the persistence boundary for the `Create Reservation` vertical slice.

This is a design-only round. It does not create Java repository implementations, Spring Data repositories, mapper implementations, application services, controllers, API DTOs, Vue UI, migrations, SQL files, seed data, mock runtime data, or database changes.

Previous Reservation Create application contract confirmation:

- Scope is Create Reservation only.
- CheckIn designed: No.
- Queue designed: No.
- Seating designed: No.
- No-show designed: No.
- Cancellation designed: No.
- Table assignment designed: No.
- API implemented: No.
- UI implemented: No.
- Migration changed: No.

## 2. Persistence Scope

In scope:

- Store-scoped Reservation creation persistence.
- Tenant-scoped Customer lookup or save needed before Reservation creation.
- Store policy lookup for reservation hold and expected dining duration policy values.
- Duplicate active Reservation lookup for the same Customer and overlapping time range.
- Capacity usage lookup for Store + business date + time range.
- Store-scoped Reservation code existence check or save-time uniqueness protection.
- BusinessEvent append for Reservation creation/confirmation.
- StateTransitionLog append for Reservation status transition.
- AuditLog append for Reservation create success/failure evidence.
- Idempotency record lookup/start/complete/fail for action `create_reservation`.
- UTC instant and Store-local business date persistence boundaries.

Out of scope:

- Reservation CheckIn.
- Reservation seating or resource assignment.
- QueueTicket creation.
- Queue fallback.
- No-show handling.
- Cancellation handling.
- Reservation update, list, calendar, or search.
- Table assignment.
- TableLock creation for Reservation.
- ReservationPreassignment workflow.
- API, Controller, DTO, UI, Migration, SQL, seed data, and runtime mock data.

## 3. Table / Entity Scope

Allowed table/entity scope for this slice:

- `stores`
- `store_policies`
- `customers`
- `reservations`
- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`
- `reason_codes`
- `i18n_message_catalog`

Forbidden table/entity scope for this slice:

- `queue_tickets`
- `walk_ins`
- `seatings`
- `seating_resources`
- `cleanings`
- `turnovers`
- `table_locks`
- `reservation_preassignments`

Notes:

- `reservation_preassignments` is a concrete table or table group preassignment boundary. Create Reservation V1 must not write it.
- `table_locks` protects concrete table/table-group resources. Create Reservation V1 does not lock concrete resources.
- `reason_codes` and `i18n_message_catalog` are allowed only as configuration/reference boundaries where later implementation needs stable codes or display keys. They are not command target tables for this slice.

## 4. Required Repository Ports

Repository Ports must be use-case shaped, not mechanical CRUD. Ports return Domain Objects, projections, existence results, or append results. They must not expose JPA entities, Spring Data repositories, SQL objects, or raw database exceptions.

### StoreRepositoryPort

Required capability:

```text
findByScope(StoreScope scope)
```

Equivalent existing method names such as `findById(StoreScope)` are acceptable if they preserve the same boundary.

Purpose:

- Validate Store exists.
- Validate `tenant_id + store_id` scope.
- Load Store timezone, locale, date format, time format, currency, and operational status when needed.
- Provide Store timezone needed to derive `businessDate` outside persistence mapping.

Must not:

- Return Stores across Tenant scope.
- Make Reservation capacity decisions.
- Expose Store persistence entities to the application service.

### StorePolicyRepositoryPort

Required capability:

```text
findByStoreScope(StoreScope scope)
```

Equivalent existing method names such as `findByStore(StoreScope)` or `findCurrentPolicy(StoreScope, Instant)` are acceptable if they return the current policy for the Store.

Purpose:

- Load `reservation_hold_minutes`.
- Load `expected_dining_minutes`.
- Load Reservation-related policy knobs already modeled for the Store.

Policy missing behavior:

- Repository returns an explicit empty result or domain error projection. It must not silently create a default row.
- Application policy decides whether to use V1 business defaults or fail with `STORE_POLICY_MISSING`.
- V1 confirmed defaults remain: reservation hold 15 minutes and expected dining duration 90 minutes where the application-level fallback is explicitly allowed.

Must not:

- Derive `reservedEndAt` inside the repository.
- Run Reservation availability logic.
- Introduce new schema fields in this round.

### CustomerRepositoryPort

Required capabilities:

```text
findById(TenantScope scope, CustomerId customerId)
findByPhone(TenantScope scope, E164Phone phone)
save(TenantScope scope, Customer customer)
```

Purpose:

- Resolve existing Tenant-scoped Customer by id.
- Resolve existing Tenant-scoped Customer by nullable E.164 phone when phone is present.
- Save a temporary/no-phone Customer when the application rules choose to create one.

Requirements:

- Customer is Tenant-level shared data and must not require `store_id`.
- `phone_e164` is nullable.
- Phone is not the primary Customer identity.
- Tenant-scoped Customer uniqueness must be preserved.
- Anonymous/no-phone Reservation input must remain supported.

Must not:

- Treat Customer as Member.
- Introduce loyalty, marketing, payment, or membership concepts.
- Search across Tenants.
- Decide Customer identity sufficiency by itself; that belongs to `CustomerIdentityRule`.

### ReservationRepositoryPort

Required capabilities:

```text
save(StoreScope scope, Reservation reservation)
existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange)
findActiveCapacityUsage(StoreScope scope, BusinessDate businessDate, TimeRange timeRange)
findByCode(StoreScope scope, ReservationCode code)
```

Equivalent minimal method names are acceptable, for example:

```text
existsByReservationCode(StoreScope scope, ReservationCode code)
findActiveCapacityUsage(StoreScope scope, TimeRange timeRange)
```

Purpose:

- Persist the Reservation aggregate.
- Check same Customer active duplicate reservation rule.
- Read active capacity usage for Store + business date + overlapping time range.
- Support Store-scoped `reservation_code` uniqueness.

Active statuses:

```text
confirmed
arrived
seated
```

Must exclude from active duplicate and active capacity usage:

- `deleted_at is not null`
- `cancelled`
- `no_show`
- `completed`
- `draft`, unless a later draft flow explicitly decides draft holds capacity

Must not:

- Create QueueTicket.
- Create Seating.
- Create TableLock.
- Write `reservation_preassignments`.
- Implement CheckIn, No-show, Cancellation, update, list, or calendar behavior.
- Return persistence entities.

### BusinessEventRepositoryPort

Required capability:

```text
append(StoreScope scope, BusinessEvent event)
```

Required event types:

- `reservation.created`
- `reservation.confirmed`

A later implementation may use one event only if it documents the atomic create-and-confirm behavior, but the preferred contract is to append both event codes.

Required persistence fields:

- `tenant_id`
- `store_id`
- `event_type`
- `target_type = reservation`
- `target_id = reservationId`
- `actor_type`
- `actor_id`
- `source`
- `idempotency_key`
- `occurred_at`
- `before_state`
- `after_state`
- `metadata`

### StateTransitionLogRepositoryPort

Required capability:

```text
append(StoreScope scope, StateTransitionLog transitionLog)
```

Required transition:

```text
target_type = reservation
from_status = null or draft
to_status = confirmed
transition_code = reservation.confirmed
```

Required persistence fields:

- `tenant_id`
- `store_id`
- `target_id = reservationId`
- `actor_type`
- `actor_id`
- `reason_code` when present
- `idempotency_key`
- `occurred_at`
- `audit_log_id` when linked
- `before_state`
- `after_state`
- `metadata`

### AuditLogRepositoryPort

Required capability:

```text
append(StoreScope scope, AuditLog auditLog)
```

Required operation:

```text
reservation.create
```

Audit metadata for success should include:

- `reservationId`
- `reservationCode`
- `customerId`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `businessDate`
- `holdUntilAt`
- `source`
- `reasonCode`
- `note`
- `idempotencyKey`
- `actorId`
- `actorType`

Failure audit:

- If Reservation was not created, `target_id` may be null.
- Failure metadata should include normalized request facts where available.
- If AuditLog append itself fails, the application command should fail and idempotency should be marked failed when possible.

### IdempotencyRepositoryPort

Required capabilities:

```text
findOrStart(scope, source, action, key, requestHash, expiresAt)
complete(record, targetRef, responseSnapshot)
fail(record, failureReason)
```

Equivalent existing split methods such as `findByScopeActionKey`, `start`, `complete`, and `fail` are acceptable.

Action:

```text
create_reservation
```

Store scope:

- Reservation Create is a Store operation, so idempotency scope should carry `tenant_id` and `store_id`.
- Nullable scope behavior remains supported by the shared idempotency table, but this action should use Store scope.

Behavior:

| Existing status | Same request hash | Different request hash |
| --- | --- | --- |
| `completed` | Replay previous result. | `IDEMPOTENCY_CONFLICT`. |
| `started` | Retry later / conflict. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |
| missing | Start and execute once. | Not applicable. |

Missing key:

```text
MISSING_IDEMPOTENCY_KEY
```

Must not:

- Decide the Reservation business result by itself.
- Create duplicate Reservation rows on replay.
- Swallow persistence failures.

## 5. Required Mapper Boundaries

Required mappers:

- `ReservationMapper`
- `CustomerMapper`
- `StorePolicyMapper`
- `BusinessEventMapper`
- `StateTransitionLogMapper`
- `AuditLogMapper`
- `IdempotencyMapper`

Mapper responsibilities:

- Convert Persistence Entity to Domain Object or domain projection.
- Convert Domain Object to Persistence Entity or update an existing Persistence Entity.
- Map raw UUIDs to value-object ids.
- Map `tenant_id` and `store_id` to `TenantScope` or `StoreScope`.
- Map database status text to domain status enums.
- Map `timestamptz` to Java `Instant`.
- Map `business_date date` to Java `LocalDate`.
- Map JSONB before/after/metadata/response snapshots to typed payload wrappers.
- Preserve nullable fields, especially `customers.phone_e164` and `reservations.customer_id`.

Mapper must not:

- Decide capacity availability.
- Decide duplicate reservation policy.
- Decide idempotency replay/conflict/failure.
- Decide whether audit/event/transition records are required.
- Derive `reservedEndAt` from Store policy.
- Derive Store-local `businessDate` from timezone.
- Generate `reservationCode`.
- Validate Reservation state transitions.
- Render i18n display text.
- Call repositories.
- Contain API DTO logic.

## 6. Capacity Availability Contract

Reservation Create V1 capacity check is based on:

```text
Store + BusinessDate + TimeRange + PartySize
```

It does not use:

- Specific table locks.
- Dining table assignment.
- Table group assignment.
- Area split.
- Table type split.
- Capacity bucket split.

Repository data requirement:

```text
ReservationCapacityUsage {
  storeScope
  businessDate
  requestedTimeRange
  activeReservationCount
  activePartySizeTotal
  activeStatuses = [confirmed, arrived, seated]
}
```

An equivalent projection or aggregate total is acceptable if it gives the rule layer enough data to determine capacity.

Overlap rule required at query boundary:

```text
existing.reserved_start_at < requested.reservedEndAt
and existing.reserved_end_at > requested.reservedStartAt
```

Filtering rules:

- Include only same `tenant_id` and `store_id`.
- Include only same Store-local `business_date` or a wider overlap query when a time range crosses a business-date boundary.
- Include only `confirmed`, `arrived`, and `seated`.
- Exclude `deleted_at is not null`.
- Exclude `cancelled`, `no_show`, and `completed`.
- Exclude `draft` in V1 Create Reservation.

Application/rule layer usage:

- `ReservationAvailabilityRule` compares `activePartySizeTotal + requested.partySize` with Store capacity or configured Reservation capacity policy.
- Store capacity/configured capacity is not computed by the repository.
- If policy data is missing, the application service decides whether to use a confirmed V1 default or fail with `STORE_POLICY_MISSING`.
- The repository must not create or update any Reservation while performing usage lookup.

Important schema note:

- V001 includes indexes for schedule lookup and an active duplicate partial unique index for identical slots.
- Arbitrary overlap capacity checking still needs an explicit repository query; it must not rely only on the identical-slot unique index.

## 7. Duplicate Reservation Contract

Business rule:

Same Tenant + Store + Customer + overlapping time range must not have more than one active Reservation.

Repository capability:

```text
existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange)
```

Duplicate overlap rule:

```text
existing.reserved_start_at < requested.reservedEndAt
and existing.reserved_end_at > requested.reservedStartAt
```

Active statuses:

```text
confirmed
arrived
seated
```

Filtering rules:

- Same `tenant_id`.
- Same `store_id`.
- Same resolved `customer_id`.
- `deleted_at is null`.
- Active statuses only.

Anonymous/no-phone behavior:

- The preferred application flow resolves or creates a Tenant-scoped Customer before duplicate checking.
- If a later application path permits a truly anonymous Reservation without `customer_id`, same-customer duplicate checking cannot be applied; capacity checking still applies.
- Phone may help resolve Customer, but phone is not Customer identity and is nullable.

DB support note:

- V001 has `ux_reservations_active_customer_slot` for same Customer and identical `reserved_start_at + reserved_end_at` active slots.
- The repository duplicate query must still support arbitrary overlapping ranges, not only identical start/end rows.

## 8. Reservation Code Contract

Business code:

```text
reservation_code
```

Generation owner:

```text
ReservationCodePolicy
```

Persistence support:

```text
findByCode(StoreScope scope, ReservationCode code)
```

or:

```text
existsByReservationCode(StoreScope scope, ReservationCode code)
```

Save-time protection:

- V001 enforces active Store-scoped uniqueness through `ux_reservations_code_active`.
- Repository implementation should translate unique constraint violations into a stable application/domain error such as `RESERVATION_CODE_CONFLICT`.
- Repository must not expose raw database constraint exceptions to the application service.

Rules:

- Do not use database auto-increment as the business Reservation code.
- Do not make Reservation code globally unique across Tenants.
- Do not generate a code in the mapper.
- Do not use Queue ticket number semantics for Reservation code.

## 9. Time / Timezone Contract

Persisted facts:

```text
reserved_start_at timestamptz
reserved_end_at timestamptz
hold_until_at timestamptz
business_date date
```

Rules:

- `reserved_start_at` and `reserved_end_at` are required for persistence.
- `reserved_end_at` must be explicit by the time the Reservation reaches persistence.
- If a future API omits `reservedEndAt`, the application layer derives it using `StorePolicy.expectedDiningDurationMinutes` before calling `ReservationRepositoryPort.save`.
- Persistence must not store Store-local wall time as fact time.
- `timestamptz` values represent UTC instants.
- `businessDate` is derived from `reservedStartAt` using Store timezone.
- Store timezone, locale, date format, time format, and currency come from `stores`.
- Formatting for display belongs to API/UI/i18n layers, not persistence.

Validation support:

- Repository may rely on V001 check constraint `reserved_end_at > reserved_start_at`.
- Application rules must validate time range before save and return stable errors such as `INVALID_TIME_RANGE` or `RESERVATION_START_IN_PAST`.
- Repository should translate persistence constraint failures into stable persistence/domain errors, not raw SQL errors.

## 10. Event / Transition / Audit Persistence

Success persistence should be atomic with Reservation creation where implementation technology allows:

1. Save Reservation with status `confirmed`.
2. Append `reservation.created`.
3. Append `reservation.confirmed`.
4. Append StateTransitionLog `null/draft -> confirmed`.
5. Append AuditLog `reservation.create`.
6. Complete idempotency with a response snapshot.

Target reference:

```text
target_type = reservation
target_id = reservationId
```

Required scope:

```text
tenant_id = StoreScope.tenantId
store_id = StoreScope.storeId
```

Required actor/source boundary:

- `actor_id` from server/application context.
- `actor_type` aligned with allowed source codes such as `staff`, `customer`, `integration`, or `system`.
- `source` must be a stable code, not display text.

Metadata:

- May include request facts and result snapshot.
- Must not become the source of core Reservation state.
- Must not store localized display text as business fact.

Failure handling:

- If event, transition, audit, or idempotency completion fails, the application should fail the command.
- Repository adapters should return stable errors or throw mapped persistence exceptions.
- Repositories must not swallow append failures.

## 11. Idempotency Persistence

Action:

```text
create_reservation
```

Request hash should be built from normalized command intent:

- `tenantId`
- `storeId`
- `partySize`
- `reservedStartAt` UTC
- `reservedEndAt` UTC
- `customerId`
- `customerName`
- `customerNickname`
- `phoneE164`
- `note`
- `reservationCode` if supplied
- `source`

Persistence states:

- `started`
- `completed`
- `failed`
- `expired`

Completion snapshot:

- Stores the application result snapshot needed for replay.
- Must include enough fields to return the same Reservation Create result later.
- Must not expose JPA entity internals.

Failure behavior:

- Application validation failures after an idempotency record is started should mark the record `failed` where possible.
- Same failed key and same hash requires a new key.
- Same key with different hash returns conflict.
- Repeated completed same key must not create a second Reservation.

## 12. Failure Cases Persistence Boundary

| Scenario | Persistence support | Stable error boundary | Audit | Idempotency |
| --- | --- | --- | --- | --- |
| Store not found | `StoreRepositoryPort.findByScope` returns empty. | `STORE_NOT_FOUND` | Yes, target null when possible. | Mark failed if started. |
| Store scope mismatch | Store lookup is scope-filtered and returns empty or mismatch projection. | `STORE_SCOPE_MISMATCH` | Yes, target null when possible. | Mark failed if started. |
| Policy not found | `StorePolicyRepositoryPort.findByStoreScope` returns empty. | `STORE_POLICY_MISSING` or documented fallback. | Yes. | Mark failed if command fails. |
| Customer not found | `CustomerRepositoryPort.findById` returns empty. | `CUSTOMER_NOT_FOUND` | Yes. | Mark failed if started. |
| Duplicate active Reservation | `existsActiveDuplicate` returns true. | `DUPLICATE_ACTIVE_RESERVATION` | Yes. | Mark failed if started. |
| Capacity insufficient | `findActiveCapacityUsage` supports rule decision. | `RESERVATION_CAPACITY_UNAVAILABLE` | Yes. | Mark failed if started. |
| Reservation code conflict | `findByCode` detects existing code or save hits unique constraint. | `RESERVATION_CODE_CONFLICT` | Yes when possible. | Mark failed if started. |
| Idempotency conflict | `IdempotencyRepositoryPort` detects same key different hash. | `IDEMPOTENCY_CONFLICT` | Recommended. | Existing record unchanged. |
| Idempotency in progress | Existing `started` same hash. | `IDEMPOTENCY_IN_PROGRESS` | Optional. | Existing record unchanged. |
| Failed key reused | Existing `failed` same hash. | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | Optional. | Existing record unchanged. |
| Persistence error | Repository adapter maps infrastructure failure. | `PERSISTENCE_ERROR` | Best effort. | Mark failed when possible. |

Repository error rules:

- Do not swallow exceptions.
- Do not leak raw SQL/JPA exceptions to the application service.
- Map known constraint failures to stable domain/application error categories.
- Unknown infrastructure errors should become `PERSISTENCE_ERROR`.

## 13. Non-Scope

This persistence contract does not introduce or change:

- Java Entity classes.
- Repository implementation classes.
- Spring Data repositories.
- Mapper implementation classes.
- Application Service code.
- Controller or REST endpoint.
- API request/response DTOs.
- Vue pages or components.
- Migration files.
- SQL schema files.
- Database seed data.
- Runtime mock data.
- Production configuration.

No data is inserted, and no production database is touched.

## 14. Next Implementation Notes

Recommended next round:

```text
Reservation Create Persistence Implementation
```

Implementation notes for that later round:

- Implement only the Reservation Create methods listed here.
- Reuse existing Customer, event, transition, audit, and idempotency adapter patterns where they already exist.
- Add focused tests before implementation.
- Keep Reservation Create separated from CheckIn, Queue, Seating, No-show, Cancellation, TableLock, and ReservationPreassignment.
- Preserve UTC persistence and Store timezone-derived `businessDate`.
- Validate that repository overlap queries cover arbitrary overlapping time ranges, not only identical slots.
- Do not change V001 migration unless a future schema-design round explicitly approves it.
