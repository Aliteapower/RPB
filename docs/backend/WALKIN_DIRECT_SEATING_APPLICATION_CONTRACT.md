# WalkIn Direct Seating Application Contract V1

## Purpose

This document defines the first backend vertical-slice application contract for the Reservation Platform.

Selected vertical slice:

```text
WalkIn Direct Seating Flow
```

Flow purpose:

```text
Store staff creates a WalkIn seating request
-> Store and actor scope are validated
-> Customer identity is resolved or created as a temporary/walk-in guest boundary
-> Party size is validated
-> One suitable DiningTable or TableGroup is selected
-> The selected resource is locked
-> WalkIn is created
-> Seating is created with source = WalkIn
-> SeatingResource is created
-> DiningTable or temporary TableGroup resource becomes occupied
-> BusinessEvent, StateTransitionLog, and AuditLog are written
-> Idempotency record is completed
-> Application result is returned
```

This is a contract document only. It does not create Java Application Service code, Repository Implementation, Spring Data Repository, Controller, API DTO, REST API, Vue UI, migration, SQL, seed data, mock data, configuration, or database connection.

## Inputs Read

Governance:

- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`

Architecture and skill:

- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/skills/reservation-system/SKILL.md`

Database and migration:

- `docs/database/DATABASE_DESIGN.md`
- `docs/database/ERD.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_REVIEW_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `docs/database/migrations/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`

Backend:

- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/BACKEND_DESIGN_CHECKLIST.md`
- `docs/backend/BACKEND_SKELETON_IMPLEMENTATION_REPORT.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/ENTITY_MAPPING_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/backend/PERSISTENCE_DESIGN_CHECKLIST.md`
- `docs/backend/PERSISTENCE_SKELETON_IMPLEMENTATION_REPORT.md`

## Scope

In scope:

- Design the `SeatWalkInDirectlyCommand` application command boundary.
- Design the `WalkInDirectSeatingApplicationService` orchestration boundary.
- Select the minimum Repository Port methods needed by this single vertical slice.
- Select the minimum Rule, Policy, Validator, StateMachine, Mapper, Event, Audit, and Idempotency boundaries.
- Define one-command transaction behavior.
- Define idempotency replay behavior.
- Define table/table-group lock and occupancy behavior.
- Define failure cases and application error codes.
- Define test scenarios for the later implementation round.

Business scope:

- A Store staff actor seats a WalkIn party directly when a suitable table or table group is available.
- WalkIn remains separate from QueueTicket.
- Seating uses exactly one source: WalkIn.
- The resource target is exactly one DiningTable or TableGroup.
- No Reservation and no QueueTicket is created by this flow.

## Non-Scope

Out of scope:

- Reservation create, confirm, check-in, no-show, cancellation, or seating.
- QueueTicket create, queue call, skip, rejoin, cancellation, or expiry.
- Cleaning start, cleaning completion, table release, or turnover calculation.
- Customer full search, customer merge, membership, marketing, POS, payment, or loyalty.
- API endpoint, API DTO, REST controller, OpenAPI, Vue UI, or mobile screen.
- Repository Implementation, Spring Data Repository, mapper implementation, complex SQL query, or database connection.
- New migration, SQL change, seed data, mock data, configuration, or test code.

## Command Design

Command name:

```text
SeatWalkInDirectlyCommand
```

This is an application command, not an API DTO. Do not name this boundary `SeatWalkInRequest` or `SeatWalkInResponse`.

Required fields:

| Field | Required | Meaning | Boundary |
| --- | --- | --- | --- |
| `tenantId` | Yes | Tenant isolation boundary. | Must form `StoreScope` with `storeId`. |
| `storeId` | Yes | Store operation boundary. | Direct seating is Store-scoped. |
| `partySize` | Yes | Declared guest count. | Must become `PartySize`; must be positive. |
| `customerId` | No | Existing Tenant-scoped Customer. | If present, must belong to the same Tenant. |
| `customerName` | No | No-phone or temporary customer display context. | May help create or resolve temporary customer. |
| `nickname` | No | Optional lookup aid. | Does not replace Customer identity rules. |
| `phoneE164` | No | Optional phone. | If present, must pass E.164 validation. |
| `tableId` | No | Staff-selected DiningTable. | Mutually exclusive with `tableGroupId`. |
| `tableGroupId` | No | Staff-selected TableGroup. | Mutually exclusive with `tableId`. |
| `idempotencyKey` | Yes | Critical command deduplication key. | Store-scoped action key. |
| `actorId` | Yes | Staff or system actor identity. | Captured in audit/event/transition records. |
| `actorType` | Yes | Stable actor type. | Must map to allowed source/triggered-by code such as `staff`. |

Structural command rules:

- `tenantId`, `storeId`, `partySize`, `idempotencyKey`, `actorId`, and `actorType` are mandatory.
- `tableId` and `tableGroupId` cannot both be present.
- If neither `tableId` nor `tableGroupId` is present, the service asks `TableAssignmentRule` to select a resource.
- `phoneE164` is nullable. No-phone WalkIn must be supported.
- Customer identity must not be treated as phone-only.
- User-facing error text is not carried by the command. Failures return stable application error codes for later i18n resolution.

Recommended application result boundary:

```text
WalkInDirectSeatingResult
```

The result is not an API response DTO. It may include:

- `walkInId`
- `seatingId`
- `seatingResourceTarget`
- `partySizeSnapshot`
- `walkInStatus`
- `seatingStatus`
- `resourceStatus`
- `idempotencyStatus`
- `businessEventIds`
- `stateTransitionLogIds`
- `auditLogId`

## Application Service Boundary

Application service name:

```text
WalkInDirectSeatingApplicationService
```

Primary operation:

```text
seatWalkInDirectly(SeatWalkInDirectlyCommand command) -> WalkInDirectSeatingResult
```

Responsibilities:

- Build `StoreScope` and `TenantScope` from the command.
- Validate Store existence and actor access through `StoreAccessPolicy`.
- Resolve Store current policy for assignment, lock expiry, timezone, and business-date derivation.
- Start or replay idempotency through `IdempotencyRule` and `IdempotencyRepositoryPort`.
- Resolve or create the Customer identity boundary for no-phone, temporary, anonymous, or walk-in guest context.
- Validate `PartySize`.
- Resolve explicit resource target or ask `TableAssignmentRule` for a candidate.
- Validate DiningTable or TableGroup availability, capacity, and scope.
- Acquire a resource lock through `TableLockRule` and `TableLockRepositoryPort`.
- Create and save `WalkIn`.
- Create and save `Seating` with source = WalkIn.
- Create or persist the `SeatingResource` through the Seating persistence boundary.
- Drive relevant state machines and state transition logs.
- Append required business events and audit logs.
- Complete or fail idempotency.
- Return an application result or an application error.

Not responsible for:

- Controller, API parameter parsing, REST status mapping, OpenAPI schema, or API DTO naming.
- UI text, UI message formatting, or mobile screen flow.
- SQL query implementation, JPA mapping, Spring Data repository details, or transaction manager wiring.
- The table assignment algorithm internals.
- The business rule internals of Store access, Customer identity, table capacity, table availability, lock conflict, state transition legality, audit, or idempotency.
- Full Reservation, Queue, Cleaning, or Turnover workflows.

## Required Repository Ports

Only the following port capabilities are needed by this vertical slice.

| Port | Required methods for this slice | Purpose |
| --- | --- | --- |
| `StoreRepositoryPort` | `findById(StoreScope)`, `findCurrentPolicy(StoreScope, at)` | Confirm Store exists, is operational, and provides policy/timezone context. |
| `CustomerRepositoryPort` | `findById(TenantScope, CustomerId)`, `findByPhone(TenantScope, E164Phone)`, `searchNoPhoneCandidates(TenantScope, criteria)`, `save(TenantScope, Customer)` | Resolve existing Customer or create a temporary/walk-in guest identity. |
| `DiningTableRepositoryPort` | `findById(StoreScope, TableId)`, `findCandidates(StoreScope, PartySize, BusinessDate)`, `save(StoreScope, DiningTable)` | Load explicit table, discover automatic candidates, and persist table status changes. |
| `TableGroupRepositoryPort` | `findById(StoreScope, TableGroupId)`, `findActiveMembers(StoreScope, TableGroupId)`, `findActiveGroupsForTable(StoreScope, TableId)`, `save(StoreScope, TableGroup)` | Validate explicit TableGroup, inspect members, and persist temporary group status when applicable. |
| `TableLockRepositoryPort` | `findActiveByResource(StoreScope, resourceType, resourceId)`, `existsActiveConflict(StoreScope, resourceType, resourceId, at)`, `save(StoreScope, TableLock)`, `release(StoreScope, tableLockId, releasedAt)` | Protect selected resource from concurrent seating. |
| `WalkInRepositoryPort` | `findByCode(StoreScope, walkInCode)` when code pre-generation is used, `save(StoreScope, WalkIn)` | Persist WalkIn arrival scenario and final seated status. |
| `SeatingRepositoryPort` | `findActiveBySource(StoreScope, sourceType, sourceId)`, `existsActiveResourceOccupancy(StoreScope, resourceType, resourceId)`, `findActiveOccupancy(StoreScope, resourceType, resourceId)`, `save(StoreScope, Seating)` | Prevent duplicate seating for the same WalkIn and prevent duplicate active occupancy. |
| `BusinessEventRepositoryPort` | `append(StoreScope, BusinessEvent)` | Append `walk_in.created`, `seating.created`, `table.locked`, and `table.occupied`. |
| `StateTransitionLogRepositoryPort` | `append(StoreScope, StateTransitionLog)`, `findLatest(StoreScope, targetType, targetId)` when needed | Preserve transition evidence for WalkIn, Seating, DiningTable/TableGroup, and TableLock. |
| `AuditLogRepositoryPort` | `append(StoreScope, AuditLog)` | Record success and required failure audit. |
| `IdempotencyRepositoryPort` | `findByScopeActionKey(StoreScope, source, action, key)`, `start(StoreScope, source, action, key, requestHash, expiresAt)`, `complete(StoreScope, record, targetType)`, `fail(StoreScope, record, failureReason)` | Start, replay, complete, or fail the command guard. |

Repository methods not needed in this slice must not be added merely for completeness.

## Required Mapper Boundaries

The later persistence implementation for this slice should use only the mapper subset needed by the selected objects:

- `StoreMapper`
- `StorePolicyMapper`
- `CustomerMapper`
- `DiningTableMapper`
- `TableGroupMapper`
- `TableLockMapper`
- `WalkInMapper`
- `SeatingMapper`
- `SeatingResourceMapper`
- `BusinessEventMapper`
- `StateTransitionLogMapper`
- `AuditLogMapper`
- `IdempotencyMapper`

Mapper responsibilities remain conversion only:

- Convert persistence rows to Domain Objects.
- Convert Domain Objects to persistence entities or update rows.
- Convert `reservation_id / queue_ticket_id / walk_in_id` to a single `SeatingSource` shape.
- Convert `resource_type + table_id / table_group_id` to a single resource target shape.
- Convert status codes to status enums.
- Convert timestamps as UTC instants.
- Convert generic targets to `TargetRef`.

Mapper must not decide table availability, lock conflict, customer identity sufficiency, state transition legality, audit requirement, or idempotency replay behavior.

## Required Rules, Policies, and Validators

| Component | Input in this flow | Output | Failure result |
| --- | --- | --- | --- |
| `StoreAccessPolicy` | Actor, actor type, `StoreScope`, source `staff`. | Access accepted. | `store_access_denied`. |
| `CustomerIdentityRule` | `TenantScope`, optional customer id, optional name/nickname/phone, WalkIn scenario. | Existing or creatable Customer identity decision. | `customer_identity_unresolved`. |
| `TableAvailabilityRule` | `StoreScope`, candidate resource, active locks, active seating, active cleaning, table/group status. | Resource available decision. | `table_resource_unavailable`. |
| `TableCapacityRule` | `PartySize`, `capacity_min`, `capacity_max`. | Capacity accepted. | `party_size_outside_capacity`. |
| `TableLockRule` | `StoreScope`, resource target, source type `walk_in` then `seating`, lock owner, expiry, idempotency key. | Lock acquired/released decision. | `table_lock_conflict`. |
| `TableAssignmentRule` | `PartySize`, Store policy, active table candidates, active group candidates, optional staff-selected resource. | Selected DiningTable or TableGroup. | `no_assignable_table`. |
| `TableGroupValidationRule` | TableGroup, members, member table statuses, active locks/occupancy/cleaning. | Valid TableGroup for seating. | `invalid_table_group`. |
| `SeatingSourceValidator` | Source set: reservation absent, queue ticket absent, WalkIn present, WalkIn status. | Source XOR accepted. | `invalid_seating_source`. |
| `SeatingResourceValidator` | Seating, resource type, table id or group id, resource status and StoreScope. | Resource XOR accepted. | `invalid_seating_resource`. |
| `DiningTableStateMachine` | From `available`, `reserved`, or `locked` to `occupied`. | Transition accepted. | `illegal_state_transition`. |
| `SeatingStateMachine` | `none -> planned -> locked -> occupied` or accepted collapsed implementation path. | Transition accepted. | `illegal_state_transition`. |
| `AuditRule` | Operation code, actor, scope, target, before/after state, reason, failure. | Audit required and audit snapshot shape. | `audit_required_missing` or `audit_write_failed`. |
| `BusinessEventRule` | Event type, target, actor/source, before/after state, metadata. | Event accepted. | `business_event_invalid`. |
| `StateTransitionRule` | Target type/id, from/to status, trigger, preconditions. | Transition log required and accepted. | `illegal_state_transition`. |
| `IdempotencyRule` | Store scope, source, action, key, request hash, existing record. | Start/replay/reject decision. | `idempotency_conflict` or `command_in_progress`. |

Supporting policies:

- `StoreTimeZoneRule` or `DateTimePolicy` should derive `businessDate` from the Store timezone while all instants remain UTC.
- `CustomerPhoneRule` should validate `phoneE164` when the optional phone is provided.
- `AnonymousCustomerPolicy` should permit temporary or walk-in guest identity when no durable customer identity is available.

## Transaction Boundary

Recommended boundary:

```text
One command = one transaction
```

The successful transaction must include:

1. Read Store and Store policy.
2. Start idempotency for action `seat_walk_in_directly`.
3. Resolve Customer identity.
4. Resolve table or TableGroup candidate.
5. Validate availability, capacity, TableGroup validity, source, and resource target.
6. Acquire durable resource lock.
7. Create WalkIn.
8. Create Seating with source = WalkIn.
9. Create SeatingResource for exactly one DiningTable or TableGroup.
10. Transition DiningTable or temporary TableGroup toward occupied.
11. Append required BusinessEvent records.
12. Append required StateTransitionLog records.
13. Append required AuditLog record.
14. Complete idempotency with a result snapshot or result pointer.

Failure behavior:

- If validation fails before a durable lock is committed, no table lock should remain active.
- If a durable lock was created and a later step fails in the same transaction, rollback removes the lock with the failed transaction.
- If an implementation later uses Redis or another external live lock, the service must release it in a failure path or rely on a short expiry.
- If a lock has been committed before a later non-transactional failure is discovered, it must be released, cancelled, or allowed to expire according to `TableLockRule`.
- Idempotency must be marked `failed` for command failures after `started`.
- Successful domain events, state transition logs, and audit logs are written in the same transaction as the business mutation.
- Failure audit should be written for critical rejected commands. If failure happens before the main transaction can commit, failure audit may be written through a separate append-only audit boundary.
- Audit write failure for an accepted mutation is blocking. The command must fail and roll back rather than complete an unaudited critical seating flow.

## Idempotency Boundary

Action code:

```text
seat_walk_in_directly
```

Storage scope follows the V1 migration:

```text
tenant_id + store_id + source + action + idempotency_key
```

For this flow:

- `tenant_id` = command tenant id.
- `store_id` = command store id.
- `source` = `staff` for store-staff operation.
- `action` = `seat_walk_in_directly`.
- `idempotency_key` = command idempotency key.

Request hash should include normalized business intent:

- `tenantId`
- `storeId`
- `partySize`
- `customerId` or normalized temporary customer identity fields
- normalized `phoneE164` when present
- optional `tableId`
- optional `tableGroupId`
- `actorType`

Replay behavior:

| Existing idempotency state | Same request hash | Different request hash |
| --- | --- | --- |
| none | Start command and create `started` record. | Not applicable. |
| `started` | Return `command_in_progress`; do not create another WalkIn, Seating, lock, or occupancy. | Return `idempotency_conflict`. |
| `completed` | Return the stored `WalkInDirectSeatingResult` or result pointer. | Return `idempotency_conflict`. |
| `failed` | Return the recorded failure by default; retry requires a new key or an explicit retry policy later. | Return `idempotency_conflict`. |
| `expired` | Treat as a new command only if retention policy allows key reuse; otherwise require a new key. | Return `idempotency_conflict`. |

The same WalkIn seating request must never duplicate table occupancy.

## State Transition Boundary

WalkIn:

```text
none -> arrived -> seated
```

Notes:

- `arrived` is the initial WalkIn state after creation.
- This direct seating flow transitions the newly created WalkIn to `seated`.
- It must not create QueueTicket.

Seating:

```text
none -> planned -> locked -> occupied
```

Accepted implementation shortcut:

```text
none -> occupied
```

The shortcut is allowed only if the application still records the lock decision, source validation, resource validation, and transition evidence. The preferred contract remains `planned -> locked -> occupied` because it matches the existing Seating state machine.

DiningTable:

```text
available -> locked -> occupied
reserved -> locked -> occupied
locked -> occupied
```

Notes:

- `available` is the normal direct seating path.
- `reserved` can be used only if Store policy allows staff to occupy a reserved/preassigned resource and the override is audited where required.
- `locked` can move to `occupied` only when the lock belongs to the same workflow and has not expired.
- `inactive`, `cleaning`, and already `occupied` cannot be seated.

TableGroup:

- Fixed TableGroup remains a configuration resource. Occupancy is represented through `SeatingResource`, active locks, and member table status or availability facts.
- Temporary TableGroup may use:

```text
created -> locked -> occupied
```

TableLock:

```text
none -> active -> released
```

or on failure:

```text
active -> cancelled
active -> expired
```

IdempotencyRecord:

```text
none -> started -> completed
```

or on failure:

```text
started -> failed
```

All accepted state changes must produce `StateTransitionLog` where the target is stateful and the transition is critical.

## Audit and Event Boundary

Minimum required BusinessEvent records:

| Event type | Target type | Target id | Required metadata |
| --- | --- | --- | --- |
| `walk_in.created` | `walk_in` | WalkIn id | party size, customer id if known, business date, idempotency key. |
| `seating.created` | `seating` | Seating id | source type `walk_in`, walk-in id, party size snapshot. |
| `table.locked` | `dining_table` or `table_group` | Resource id | lock id, lock owner, locked until, source type. |
| `table.occupied` | `dining_table` or `table_group` | Resource id | seating id, seating resource target, party size snapshot. |

Each event must include:

- `tenant_id`
- `store_id`
- `event_type`
- `target_type`
- `target_id`
- `actor_type`
- `actor_id`
- `source`
- `occurred_at`
- `reason_code` when applicable
- `idempotency_key`
- `metadata`

Minimum required AuditLog operations:

| Operation code | Target type | When |
| --- | --- | --- |
| `walk_in_direct_seating.started` | `walk_in` or command target placeholder | After idempotency start when command is accepted for execution. |
| `walk_in.created` | `walk_in` | WalkIn persisted. |
| `seating.created` | `seating` | Seating persisted. |
| `table.locked` | `dining_table` or `table_group` | Resource lock acquired. |
| `table.occupied` | `dining_table` or `table_group` | Resource becomes occupied. |
| `walk_in_direct_seating.failed` | command target placeholder or known target | Critical failure after command intake. |
| `walk_in_direct_seating.completed` | `seating` | Command completed. |

AuditLog must include:

- `target_type`
- `target_id` when known
- `actor_type`
- `actor_id`
- `actor_role` when available from security context
- `tenant_id`
- `store_id`
- `occurred_at`
- `source`
- `before_state`
- `after_state`
- `reason_code`
- `failure_reason` when failed
- `idempotency_key`
- `metadata`

## Failure Cases

| Case | Application error | Audit | Lock handling | Transaction | Retry |
| --- | --- | --- | --- | --- | --- |
| Store does not exist | `store_not_found` | Failure audit if actor and scope can be resolved. | No lock acquired. | End without mutation. | Retry after correcting Store. |
| StoreScope mismatch | `store_scope_mismatch` | Failure audit with attempted scope. | No lock acquired. | End without mutation. | Retry after correcting scope. |
| Party size invalid | `invalid_party_size` | Failure audit. | No lock acquired. | End without mutation. | Retry with valid party size. |
| Customer identity cannot be resolved or created | `customer_identity_unresolved` | Failure audit. | No lock acquired. | End without mutation. | Retry with sufficient identity context. |
| No available table or group | `no_assignable_table` | Failure audit. | No lock acquired. | End without mutation. | Retry when resources change. |
| Table or group capacity insufficient | `party_size_outside_capacity` | Failure audit. | Release/cancel lock if acquired before final capacity validation. | Rollback or end without Seating. | Retry with different resource or party size. |
| Resource already locked | `table_lock_conflict` | Failure audit. | Do not override existing lock. | End without mutation. | Retry after lock expiry or release. |
| TableGroup invalid | `invalid_table_group` | Failure audit. | Release/cancel lock if acquired. | Rollback or end without Seating. | Retry after group correction. |
| Seating source invalid | `invalid_seating_source` | Failure audit. | Release/cancel lock if acquired. | Rollback. | Retry only after command/source correction. |
| SeatingResource target invalid | `invalid_seating_resource` | Failure audit. | Release/cancel lock if acquired. | Rollback. | Retry only after resource correction. |
| Idempotency key reused with different request hash | `idempotency_conflict` | Suspicious replay audit recommended. | No new lock acquired. | End without mutation. | Retry with a new key or original request. |
| Illegal state transition | `illegal_state_transition` | Failure audit. | Release/cancel lock if acquired. | Rollback. | Retry only after resource/status changes. |
| Audit write failed | `audit_write_failed` | The failed audit write itself may be logged by infrastructure later. | Release/cancel external lock if acquired. | Rollback accepted mutation. | Retry after audit storage recovery. |
| Repository save failed | `repository_save_failed` | Failure audit if audit storage remains available. | Release/cancel external lock if acquired; database rollback removes uncommitted durable lock. | Rollback. | Retry after persistence issue is resolved. |

## Test Contract

This section defines future tests only. No test code is created in this round.

Required successful scenarios:

- WalkIn direct seating succeeds with a generated temporary/walk-in Customer.
- WalkIn direct seating succeeds without phone number.
- WalkIn direct seating succeeds with an existing `customerId`.
- Staff-selected `tableId` succeeds.
- Staff-selected `tableGroupId` succeeds when group is valid.
- Automatic table assignment succeeds when a candidate DiningTable is available.
- Automatic group assignment succeeds when a valid TableGroup is available.
- Successful command creates WalkIn, Seating, SeatingResource, TableLock, BusinessEvent, StateTransitionLog, AuditLog, and completed IdempotencyRecord.
- Successful command records `party_size_snapshot` on Seating.

Required failure scenarios:

- Store does not exist.
- StoreScope mismatch rejects cross-store or cross-tenant resource reference.
- Party size <= 0 is rejected.
- Phone is optional, but invalid provided phone is rejected.
- Customer identity insufficient is rejected.
- No assignable table fails without creating QueueTicket.
- Party size outside selected resource capacity fails.
- Existing active TableLock fails.
- Existing active SeatingResource occupancy fails.
- Invalid TableGroup fails.
- Seating source XOR failure is rejected.
- SeatingResource target XOR failure is rejected.
- Idempotency repeated same request returns same completed result.
- Idempotency same key with different hash fails.
- In-progress idempotency request does not duplicate occupancy.
- Illegal DiningTable transition fails.
- Audit/Event/StateTransition expected records are written on success.
- Audit write failure rolls back accepted mutation.

## Boundary Assertions

- Reservation implemented: No.
- Queue implemented: No.
- Repository Implementation created: No.
- Java Application Service created: No.
- Controller created: No.
- API DTO created: No.
- API implemented: No.
- UI implemented: No.
- Migration changed: No.
- SQL changed: No.
- Database touched: No.
- Seed or mock data inserted: No.

## Open Questions

- Should the later implementation allow the same failed idempotency key to retry after a transient persistence failure, or require a new key by default?
- Should staff-selected resources outside the automatic recommendation path require a manual override reason in this first vertical slice, or should that be deferred to a later manual-override slice?
- Should automatic temporary TableGroup creation be included in the first persistence implementation, or should the first implementation limit auto-assignment to existing DiningTable and existing valid TableGroup resources?

## Open Conflicts

- None found for this application contract round.

## Next Implementation Step

Next allowed round:

```text
WalkIn Direct Seating Persistence Implementation
```

That round should implement only the Repository Implementation and Mapper Implementation subset needed by this vertical slice. It must not implement Controller, REST API, API DTO, Vue UI, full Reservation flow, full Queue flow, Cleaning completion, Turnover calculation, or broad repository coverage.
