# Cleaning Complete Application Contract V1

## 1. Flow Purpose

This document defines the application contract for the second minimum backend vertical slice:

```text
WalkIn Direct Seating
-> Table occupied
-> Start Cleaning
-> Complete Cleaning
-> Table available
```

This round is contract-only. It does not create Java code, repository implementation, controller, API DTO, Vue UI, migration, SQL, seed data, production auth, or database data.

The purpose of this slice is to close the table release loop after the already validated WalkIn Direct Seating flow. WalkIn Direct Seating can move a DiningTable or TableGroup resource into occupancy. Cleaning Complete must safely move that occupied resource through cleaning and release it back to availability.

Confirmed Product Owner decision for this contract:

- Cleaning is the next slice before Reservation.
- V1 Cleaning only covers the minimum `occupied -> cleaning -> available` table release loop.
- Turnover boundary may be mentioned, but complex Turnover statistics, BI, reporting, or dashboards are out of scope.
- V1 allows anonymous WalkIn. Cleaning does not depend on Customer identity.

## 2. Scope

In scope:

- Start cleaning for an occupied DiningTable or existing TableGroup resource.
- Complete cleaning for a DiningTable or existing TableGroup resource.
- Validate Tenant and Store scope.
- Validate Seating ownership and resource relationship.
- Validate resource target XOR:
  - exactly one `tableId`
  - or exactly one `tableGroupId`
- Validate table or TableGroup can move through the cleaning state path.
- Create or update `Cleaning`.
- Update DiningTable status from `occupied` to `cleaning`.
- Update DiningTable status from `cleaning` to `available`.
- For TableGroup resources, validate group membership and release member table statuses according to TableGroup rules.
- Write BusinessEvent records.
- Write StateTransitionLog records.
- Write AuditLog records.
- Apply command-level idempotency.

Primary state loop:

```text
DiningTable: occupied -> cleaning -> available
Cleaning: none/pending -> cleaning -> completed -> released
```

The application contract maps the Product Owner's minimal business loop to the existing schema and state machine vocabulary:

- Start Cleaning may create a Cleaning record and move it through `pending -> cleaning` in one command.
- Complete Cleaning may move Cleaning through `cleaning -> completed -> released` in one command.
- The public business result is still simple: occupied table becomes cleaning, then available.

## 3. Non-Scope

Out of scope:

- Reservation implementation.
- Queue implementation.
- WalkIn modification.
- Seating source modification.
- Complex Turnover BI.
- Turnover dashboard.
- Turnover report aggregation.
- Payment.
- POS.
- Marketing.
- Membership.
- Production Auth.
- REST API.
- Controller.
- API DTO.
- Vue UI.
- New migration.
- SQL schema change.
- Seed data.
- Mock runtime data.
- Production database connection.

Cleaning must not be used as a place to implement Reservation, Queue, Customer search, Table management, or Turnover analytics.

## 4. Selected Vertical Slice

Selected vertical slice:

```text
Cleaning Complete
```

Minimum flow:

```text
Store staff selects occupied Seating / Table / TableGroup
-> Validate StoreScope
-> Validate occupied resource
-> Start Cleaning
-> Transition Table occupied -> cleaning
-> Complete Cleaning
-> Transition Table cleaning -> available
-> Persist Cleaning
-> Append BusinessEvent
-> Append StateTransitionLog
-> Append AuditLog
-> Complete IdempotencyRecord
-> Return application result
```

This contract keeps Start and Complete as two application commands because they are distinct operational actions:

- Start Cleaning records the moment the occupied resource leaves seating and becomes unavailable for cleaning.
- Complete Cleaning records the moment the cleaned resource is released and becomes available again.

Keeping two commands prevents a future UI/API from pretending cleaning happened instantly when staff needs to track an in-progress cleaning state.

## 5. Command Design

### 5.1 StartCleaningCommand

Purpose:

- Begin cleaning for the occupied resource associated with a Seating.
- Move the resource from occupied to cleaning.
- Create a Cleaning record if one does not already exist for the accepted idempotent command.

Suggested fields:

| Field | Required | Source | Notes |
|---|---:|---|---|
| `tenantId` | Yes | Server context | Never trusted from request body. |
| `storeId` | Yes | Path or server context | Must belong to actor Store scope. |
| `seatingId` | Yes | API or application caller | Seating must belong to StoreScope and be occupied or otherwise policy-approved for cleaning start. |
| `tableId` | Conditional | API or application caller | Required when target is DiningTable. |
| `tableGroupId` | Conditional | API or application caller | Required when target is TableGroup. |
| `actorId` | Yes | Server context | Staff/system actor. |
| `actorType` | Yes | Server context | Usually `staff`; future `system` may be allowed. |
| `reasonCode` | Optional | Caller | Cleaning/table release reason when required by policy. |
| `note` | Optional | Caller | Staff note; not display copy. |
| `idempotencyKey` | Yes | Header or command caller | Required. |

Rules:

- `tenantId` comes from server context.
- `storeId` comes from path or server context and must be checked against actor scope.
- `idempotencyKey` is required.
- Exactly one of `tableId` or `tableGroupId` must be present.
- `seatingId` must be present and must match the target resource through active or just-released SeatingResource.
- Command does not depend on Customer.
- Command does not depend on Reservation.
- Command does not depend on QueueTicket.
- Command does not create QueueTicket.
- Command does not create Turnover BI.

### 5.2 CompleteCleaningCommand

Purpose:

- Complete an in-progress Cleaning.
- Release the cleaned resource.
- Move DiningTable status from `cleaning` to `available` unless table policy requires `inactive`.

Suggested fields:

| Field | Required | Source | Notes |
|---|---:|---|---|
| `tenantId` | Yes | Server context | Never trusted from request body. |
| `storeId` | Yes | Path or server context | Must belong to actor Store scope. |
| `cleaningId` | Yes | API or application caller | Cleaning must belong to StoreScope. |
| `tableId` | Conditional | API or application caller | Required when cleaning target is DiningTable. |
| `tableGroupId` | Conditional | API or application caller | Required when cleaning target is TableGroup. |
| `actorId` | Yes | Server context | Staff/system actor. |
| `actorType` | Yes | Server context | Usually `staff`; future `system` may be allowed. |
| `reasonCode` | Optional | Caller | Completion or release reason where policy requires it. |
| `note` | Optional | Caller | Staff note; not display copy. |
| `idempotencyKey` | Yes | Header or command caller | Required. |

Rules:

- `cleaningId` identifies the Cleaning workflow.
- Exactly one of `tableId` or `tableGroupId` must be present.
- The target resource must match the persisted Cleaning target.
- Cleaning status must be `cleaning` unless idempotency replay applies.
- Command does not change Customer, WalkIn identity, Reservation, or QueueTicket.
- Command may update Seating to `cleaning_triggered` if Start did not already do so, but it must not change Seating source.

## 6. Application Service Boundary

Recommended service:

```text
CleaningApplicationService
```

Application methods:

```text
startCleaning(StartCleaningCommand command)
completeCleaning(CompleteCleaningCommand command)
```

Alternative split for implementation:

```text
StartCleaningApplicationService
CompleteCleaningApplicationService
```

Either shape is acceptable if the same transaction, rule, audit, and idempotency boundaries are preserved. A single `CleaningApplicationService` is recommended for V1 because both commands share the same Cleaning, resource, event, audit, transition, and idempotency dependencies.

Responsibilities:

- Validate command structure.
- Build `StoreScope`.
- Validate Store access.
- Check idempotency.
- Validate Store exists.
- Validate Seating belongs to Store scope.
- Validate Seating resource target belongs to Store scope.
- Validate DiningTable or TableGroup belongs to Store scope.
- Validate the resource target XOR.
- Validate TableGroup membership and active status when target is TableGroup.
- Validate DiningTable state transition:
  - `occupied -> cleaning`
  - `cleaning -> available`
- Validate Cleaning state transition:
  - `none -> pending`
  - `pending -> cleaning`
  - `cleaning -> completed`
  - `completed -> released`
- Create or update Cleaning.
- Update DiningTable status.
- Release active SeatingResource when cleaning starts or when policy requires it.
- Write BusinessEvent.
- Write StateTransitionLog.
- Write AuditLog.
- Complete or fail IdempotencyRecord.
- Return application result.

Not responsible for:

- API parsing.
- HTTP status mapping.
- UI display text.
- i18n message resolution.
- Reservation lifecycle.
- Queue lifecycle.
- Customer search.
- Payment.
- POS.
- Marketing.
- Full Turnover reporting.
- Table management configuration.
- Production Auth.

## 7. Application Result Design

Suggested success result:

```text
CleaningApplicationResult
  success: true
  cleaningId
  seatingId
  resourceType: dining_table | table_group
  tableId
  tableGroupId
  previousTableStatus
  currentTableStatus
  cleaningStatus
  events[]
  idempotencyStatus
  replayed
```

Suggested failure result:

```text
CleaningApplicationResult
  success: false
  errorCode
  details
  idempotencyStatus
```

The application result is not an API response DTO and must not expose Persistence Entity internals.

## 8. Required Repository Ports

Minimum required ports:

| Port | Purpose In This Slice |
|---|---|
| `StoreRepositoryPort` | Validate Store exists and resolve Store scope/profile when needed. |
| `DiningTableRepositoryPort` | Load and save DiningTable status for `occupied -> cleaning -> available`. |
| `TableGroupRepositoryPort` | Load TableGroup and members when cleaning target is TableGroup. |
| `SeatingRepositoryPort` | Load Seating and SeatingResource; validate source remains unchanged; release resource occupancy where policy requires. |
| `CleaningRepositoryPort` | Load active Cleaning by resource or id; save Cleaning lifecycle state. |
| `BusinessEventRepositoryPort` | Append cleaning and table events. |
| `StateTransitionLogRepositoryPort` | Append table, seating, and cleaning transition evidence. |
| `AuditLogRepositoryPort` | Append success and failure audit records. |
| `IdempotencyRepositoryPort` | Start, replay, complete, fail, and detect conflict for idempotent commands. |

Optional only if a later implementation already has the boundary:

| Port | Use |
|---|---|
| `ReasonCodeRepositoryPort` | Validate cleaning/table release reason code when provided or required. |

Do not introduce:

- `ReservationRepositoryPort`
- `QueueTicketRepositoryPort`
- Broad `TurnoverRepositoryPort` for BI/reporting
- Mechanical all-table CRUD ports

Turnover can remain a documented downstream boundary. This slice does not require Turnover persistence unless a later Product Owner round explicitly asks for a minimal turnover record.

## 9. Required Rules / Policies / Validators

Required reusable components:

| Component | Type | Purpose |
|---|---|---|
| `StoreAccessPolicy` | Policy | Validate actor can operate within Store scope. |
| `DiningTableStateMachine` | State machine | Validate `occupied -> cleaning -> available`. |
| `CleaningStateMachine` | State machine | Validate `pending -> cleaning -> completed -> released`. |
| `TableAvailabilityRule` | Rule | Ensure resource is not assignable while occupied/cleaning and can become available after release. |
| `SeatingResourceValidator` | Validator | Validate Seating has exactly one resource target and it matches command target. |
| `CleaningResourceValidator` | Validator | Validate Cleaning target XOR and Store scope. |
| `AuditRule` | Rule | Decide required audit snapshot and operation code. |
| `BusinessEventRule` | Rule | Validate event codes and target shape. |
| `StateTransitionRule` | Rule | Validate transition evidence requirements. |
| `IdempotencyRule` | Rule | Deduplicate command execution and replay completed result. |

Optional:

| Component | Type | Purpose |
|---|---|---|
| `TableGroupValidationRule` | Rule | Required when target is TableGroup; optional only if implementation starts with table-only, but this contract designs both targets. |
| `ReasonCodeRule` | Rule | Validate reason code when future cleaning reason configuration is implemented. |

Do not implement or require:

- `ReservationAvailabilityRule`
- `ReservationDuplicateRule`
- `QueueCallingRule`
- `QueueOrderingPolicy`
- Complex Turnover calculation rule

## 10. Transaction Boundary

Each accepted command should execute in one application transaction.

### 10.1 StartCleaning Transaction

Suggested order:

1. Validate command shape before mutation.
2. Resolve StoreScope.
3. Resolve idempotency decision for action `start_cleaning`.
4. Reject completed replay, in-progress, failed-key, or hash-conflict cases according to IdempotencyRule.
5. Validate Store access.
6. Load Store.
7. Load Seating.
8. Load SeatingResource target.
9. Validate command resource target matches SeatingResource target.
10. Load DiningTable or TableGroup members.
11. Validate resource currently occupied and belongs to Store.
12. Create Cleaning as `pending` if needed.
13. Transition Cleaning `pending -> cleaning`.
14. Transition DiningTable or member tables `occupied -> cleaning`.
15. Transition Seating `occupied -> completed` and/or `completed -> cleaning_triggered` where not already done by policy.
16. Mark SeatingResource released when cleaning owns the resource release path.
17. Append BusinessEvent.
18. Append StateTransitionLog.
19. Append AuditLog.
20. Complete IdempotencyRecord with result snapshot.

### 10.2 CompleteCleaning Transaction

Suggested order:

1. Validate command shape before mutation.
2. Resolve StoreScope.
3. Resolve idempotency decision for action `complete_cleaning`.
4. Reject completed replay, in-progress, failed-key, or hash-conflict cases according to IdempotencyRule.
5. Validate Store access.
6. Load Cleaning.
7. Validate Cleaning target matches command target.
8. Validate DiningTable or TableGroup members.
9. Validate Cleaning is in `cleaning`.
10. Transition Cleaning `cleaning -> completed`.
11. Transition Cleaning `completed -> released`.
12. Transition DiningTable or member tables `cleaning -> available`, unless table policy requires `inactive`.
13. Append BusinessEvent.
14. Append StateTransitionLog.
15. Append AuditLog.
16. Complete IdempotencyRecord with result snapshot.

Failure handling:

- No partial table status change should be committed if validation fails.
- Validation failures after idempotency start should mark the idempotency record as `failed`.
- Infrastructure failures should not be hidden behind a business success result.
- Audit write failure fails the command because audit is mandatory for Cleaning.

## 11. State Transition Boundary

### 11.1 DiningTable

Legal transitions used by this slice:

| From | To | Command | Required Evidence |
|---|---|---|---|
| `occupied` | `cleaning` | `StartCleaningCommand` | Cleaning created/started, resource target, actor, reason/note, idempotency key. |
| `cleaning` | `available` | `CompleteCleaningCommand` | Cleaning completed/released, actor, reason/note, idempotency key. |

Illegal in this slice:

- `available -> cleaning`
- `inactive -> cleaning`
- `reserved -> available` directly
- `occupied -> available` directly without Cleaning
- `cleaning -> occupied` without a new Seating command after release

### 11.2 Cleaning

Existing schema statuses:

```text
pending
cleaning
completed
released
cancelled
```

Legal transitions used by this slice:

| From | To | Command | Notes |
|---|---|---|---|
| none | `pending` | Start Cleaning | New Cleaning record. |
| `pending` | `cleaning` | Start Cleaning | Cleaner starts work. |
| `cleaning` | `completed` | Complete Cleaning | Cleaning task completed; `completed_at` required. |
| `completed` | `released` | Complete Cleaning | Resource release finished; `released_at` required by contract. |

Cancelled is designed but not implemented by this slice.

Illegal in this slice:

- `pending -> released` without completion.
- `cleaning -> released` without `completed`.
- `released -> cleaning`.
- `cancelled -> completed`.
- Completing a Cleaning that belongs to another Store.

### 11.3 Seating

Cleaning must not change Seating source.

Allowed related transitions:

- `occupied -> completed`
- `completed -> cleaning_triggered`

Only apply these transitions if current Seating state and previous flow require them. If Seating was already completed by a prior guest-departure command in a future round, Start Cleaning should only link/validate that state and must not create a contradictory source change.

### 11.4 TableGroup

For `table_group` cleaning targets:

- `TableGroupValidationRule` must confirm all member tables belong to the same Tenant and Store.
- Fixed TableGroup configuration status should not be mutated just because it was used for Seating or Cleaning.
- Temporary TableGroup release/end behavior is a policy boundary. If a temporary group is used, release/end must be audited and state-transition logged.
- Member DiningTables must not become available until Cleaning completion.

## 12. Audit / Event Boundary

Required BusinessEvent codes:

| Code | Target | Command |
|---|---|---|
| `cleaning.started` | `cleaning` | Start Cleaning |
| `table.cleaning` | `dining_table` or `table_group` | Start Cleaning |
| `cleaning.completed` | `cleaning` | Complete Cleaning |
| `table.available` | `dining_table` or `table_group` | Complete Cleaning |

Required AuditLog operation codes:

| Code | Command | Target |
|---|---|---|
| `cleaning.start.completed` | Start Cleaning success | `cleaning` |
| `cleaning.start.failed` | Start Cleaning failure | `cleaning` or broad operation target |
| `cleaning.complete.completed` | Complete Cleaning success | `cleaning` |
| `cleaning.complete.failed` | Complete Cleaning failure | `cleaning` or broad operation target |

Required StateTransitionLog targets:

- `cleaning`
- `dining_table`
- `table_group` when TableGroup status is changed
- `seating` when Seating status is completed or cleaning-triggered

Audit metadata must include at least:

- `seatingId`
- `cleaningId` when available
- `tableId` or `tableGroupId`
- `previousTableStatus`
- `newTableStatus`
- `previousCleaningStatus`
- `newCleaningStatus`
- `reasonCode`
- `note`
- `actorId`
- `actorType`
- `idempotencyKey`

Audit metadata must not include UI display copy. API/UI layers later should resolve display text through i18n keys.

## 13. Idempotency Boundary

Idempotency source:

```text
staff
```

Action codes:

```text
start_cleaning
complete_cleaning
```

Request hash:

- Must include Tenant scope.
- Must include Store scope.
- Must include action code.
- Must include `seatingId` for Start Cleaning.
- Must include `cleaningId` for Complete Cleaning.
- Must include resource target type and id.
- Must include reason/note fields if they affect the command result.

Behavior:

| Existing Record | Same Request Hash | Behavior |
|---|---:|---|
| none | n/a | Start idempotency record and execute command. |
| `completed` | Yes | Replay previous result; do not write duplicate events, transitions, audit, or table updates. |
| `started` | Yes | Return retry-later / in-progress application error. |
| `failed` | Yes | Return failed-requires-new-key application error. |
| any | No | Return idempotency conflict. |

Repeated CompleteCleaning must not:

- Write duplicate `cleaning.completed`.
- Write duplicate `table.available`.
- Write duplicate StateTransitionLog.
- Write duplicate AuditLog.
- Reapply table status changes.

## 14. Failure Cases

| Case | Application Error Code | Audit | Retry | Table Status Impact | Idempotency |
|---|---|---|---|---|---|
| Store not found | `STORE_NOT_FOUND` | Failure audit attempted | Retry after corrected Store | None | Failed if idempotency started |
| Store scope mismatch | `STORE_SCOPE_MISMATCH` | Failure audit attempted | Retry only with valid scope | None | Failed if idempotency started |
| Seating not found | `SEATING_NOT_FOUND` | Failure audit attempted | Retry after corrected seating | None | Failed if idempotency started |
| Table not found | `TABLE_NOT_FOUND` | Failure audit attempted | Retry after corrected target | None | Failed if idempotency started |
| TableGroup invalid | `TABLE_GROUP_INVALID` | Failure audit attempted | Retry after group fixed or corrected | None | Failed if idempotency started |
| Table not occupied | `TABLE_NOT_OCCUPIED` | Failure audit attempted | Retry only after valid occupancy exists | None | Failed if idempotency started |
| Table already cleaning | `TABLE_ALREADY_CLEANING` | Failure audit attempted unless same-key replay | Retry with complete command or replay same key | None | Replay only if same completed Start key; otherwise failed/conflict |
| Table already available | `TABLE_ALREADY_AVAILABLE` | Failure audit attempted unless same-key replay | Retry only if correcting command | None | Replay only if same completed Complete key; otherwise failed/conflict |
| Illegal table transition | `ILLEGAL_TABLE_TRANSITION` | Failure audit attempted | Retry after state corrected | None | Failed if idempotency started |
| Cleaning not found | `CLEANING_NOT_FOUND` | Failure audit attempted | Retry after corrected cleaning id | None | Failed if idempotency started |
| Cleaning already completed | `CLEANING_ALREADY_COMPLETED` | Failure audit attempted unless same-key replay | Replay only with same completed key | None | Same key replays; new/different key fails |
| Resource target invalid | `RESOURCE_TARGET_INVALID` | Failure audit attempted | Retry after corrected target | None | Failed if idempotency started |
| Idempotency conflict | `IDEMPOTENCY_CONFLICT` | Failure audit recommended | Retry with a new key or original body | None | Existing record unchanged |
| Audit write failure | `AUDIT_WRITE_FAILED` | Audit failed | Retry with new key after infrastructure fix | No table status commit | Failed when failure record can be persisted; no completed result |
| Repository save failure | `PERSISTENCE_ERROR` | Failure audit attempted if possible | Retry with new key after infrastructure fix | No partial commit | Failed when failure record can be persisted; no completed result |

Notes:

- Validation should happen before mutation whenever possible.
- If a failure occurs before idempotency can start, no IdempotencyRecord is required.
- If a failure occurs after idempotency starts, the record should become `failed` and V1 requires a new key for retry.
- Same completed key + same hash is the only valid replay path.

## 15. Test Contract

This round does not write tests. Future implementation should cover at least:

### Start Cleaning

- Occupied table can start cleaning.
- Occupied table group can start cleaning.
- Available table cannot start cleaning.
- Inactive table cannot start cleaning.
- Invalid Store scope is rejected.
- Resource target XOR is enforced.
- SeatingResource target must match command target.
- Start Cleaning writes:
  - Cleaning record.
  - `cleaning.started` event.
  - `table.cleaning` event.
  - Cleaning StateTransitionLog.
  - Table StateTransitionLog.
  - AuditLog.
- Same key + same request hash replays result.
- Same key + different hash returns idempotency conflict.

### Complete Cleaning

- Cleaning table can become available.
- Cleaning table group can become available.
- Already available table is rejected unless same-key completed replay applies.
- Cleaning completion writes:
  - `cleaning.completed` event.
  - `table.available` event.
  - Cleaning StateTransitionLog.
  - Table StateTransitionLog.
  - AuditLog.
- Repeated completed same key replays result.
- Hash conflict is rejected.
- Failed key requires new key.

### Boundary

- No Reservation is created.
- No QueueTicket is created.
- WalkIn is not changed.
- Seating source is not changed.
- No Turnover complex stats are implemented.
- No UI is created.
- No API is implemented.
- No migration is changed.
- No production database is touched.

## 16. Next Implementation Step

Next recommended round:

```text
Cleaning Complete Persistence Contract / Skeleton Review
```

The next implementation step should still be narrow:

- Implement only the persistence mapper/adapter support needed for Cleaning if missing.
- Reuse existing Store, DiningTable, TableGroup, Seating, Audit, Event, Transition, and Idempotency ports where possible.
- Do not implement Controller, REST API, Vue UI, Reservation, Queue, complex Turnover BI, migration, or production auth.
