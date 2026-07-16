# Large-File Incremental Capability Extraction Design

## Context

The RPB workspace has no 5,072-line tracked source file, but it has a broad concentration of large production files. The largest frontend production file is `TableResourceListPage.vue` at 2,301 lines. The largest backend production files are `ReservationArrivedDirectSeatingApplicationService.java` at 1,450 lines and `SeatingFromCalledQueueApplicationService.java` at 1,396 lines. Forty backend tests and thirty-six frontend production files exceed 500 lines.

The size is a symptom rather than the primary defect. The highest-risk files combine multiple responsibilities that change for different reasons:

- application services combine use-case orchestration, scope validation, domain rules, persistence, idempotency, response replay, audit, business events, state-transition evidence, hashing, and JSON parsing;
- seating workflows construct another application service directly instead of depending on an injected capability;
- Vue pages combine data loading, filters, workflow commands, action concurrency, error mapping, navigation, formatting, templates, and large style blocks;
- persistence adapters combine multiple ports or duplicate large native-query predicates;
- current frontend validation relies heavily on source-string assertions rather than runtime component behaviour.

The existing code compiles and focused tests pass. The objective is therefore controlled structural change with behavioural equivalence, not a rewrite.

## Goal

Incrementally extract stable capabilities from the largest backend, frontend, persistence, API, and test files so that each unit has a clear responsibility and can be tested independently, while preserving all existing HTTP APIs, database schema, Flyway history, business state machines, authorization behaviour, idempotency semantics, audit evidence, routes, and user-visible workflow outcomes.

## Fixed Constraints

- Do not change any existing API path, request shape, response shape, HTTP status, or stable error code.
- Do not add or modify database schema, migrations, indexes, constraints, or stored data.
- Do not change Reservation, QueueTicket, WalkIn, Seating, Cleaning, DiningTable, or TableGroup state-machine transitions.
- Preserve App Gate checks, tenant isolation, store isolation, idempotency behaviour, audit records, business events, transition logs, and transaction boundaries.
- Preserve existing frontend routes, query parameters, visible workflow order, stable selectors relied on by current validation, and i18n keys.
- Use the PostgreSQL runtime referenced by `target/local-postgres-current.txt` for any later local runtime or database validation.
- Perform one capability extraction or one workflow migration at a time. Each change must be independently reviewable and reversible.
- Do not create a generic framework merely to reduce line counts. Share only behaviour that is already duplicated and stable.

## Non-Goals

- No microservice split or new deployable module.
- No migration from JPA/JDBC to another persistence technology.
- No event-sourcing redesign.
- No new public feature or user-facing workflow.
- No dynamic Store-timezone API expansion in this refactor.
- No global CSS redesign or visual refresh.
- No bulk rewrite of every file above an arbitrary line threshold.
- No deletion of the active local PostgreSQL runtime or deployment worktree.

## Options Considered

### Incremental capability extraction

Introduce tested seams around time, snapshot serialization, idempotency, operation evidence, temporary table-group creation, resource planning, and frontend workflow state. Migrate one workflow at a time while the old and new shapes remain behaviourally equivalent.

This is the selected approach. It limits the blast radius, permits focused rollback, and produces reusable OOD boundaries without requiring API or schema changes.

### Vertical-slice rewrite

Rewrite Reservation seating, Queue seating, and Walk-in seating as new implementations and switch each endpoint after feature parity. This could produce cleaner code faster in isolation, but it would duplicate active workflows during transition and create a high risk of missing audit, idempotency, transaction, or App Gate details. This approach is rejected.

### Mechanical file splitting

Move methods and template fragments into additional files without first defining responsibilities. This reduces line counts but preserves coupling and can make navigation worse. This approach is rejected.

## Target Architecture

### Backend dependency direction

The target dependency direction remains:

```text
API adapter
  -> application use case
      -> domain rules/state machines
      -> application ports and application capabilities
          -> persistence/audit/idempotency adapters
```

Application code must not import API request/response classes or concrete persistence repositories. Domain objects and rules must not depend on Spring, repositories, JSON, or API DTOs.

### Typed snapshot codecs

Each idempotent workflow receives a workflow-specific codec implementing a small shared contract:

```java
public interface IdempotencySnapshotCodec<T> {
    String encode(T snapshot);
    T decode(String payload);
}
```

The implementation uses the existing Jackson dependency and an immutable workflow-specific snapshot record. It must write the same JSON field names and compatible value shapes as the current hand-built payload. It must read snapshots written by the old implementation so that deployment does not invalidate completed idempotency records.

The codec owns JSON representation only. It does not decide whether a request is new, in progress, replayable, failed, or conflicting.

Characterization fixtures must cover every existing snapshot shape before a workflow switches codecs. Malformed or incompatible snapshots continue to map to the workflow's current stable idempotency-conflict error.

### Time boundary

All backend use cases that read the current time receive `java.time.Clock`. Store-local business dates are derived by a focused `StoreBusinessTime` capability from the injected clock and `Store.timezone()`. Direct calls to `OffsetDateTime.now()`, `Instant.now()`, `LocalDate.now()`, and `Clock.systemUTC()` are removed from the migrated workflows.

Frontend date/time formatting moves behind a `StoreTemporalContext` interface and focused formatting utilities. During this refactor, Table and Queue pages continue to use the current Singapore defaults because their existing API responses do not carry Store timezone. Centralizing the default removes scattered constants without changing output. Supplying a dynamic Store timezone is a later API design and is explicitly outside this work.

Tests use fixed clocks and explicit timezone fixtures, especially around midnight and business-date boundaries.

### Idempotency coordinator

The `idempotency` module owns an application-level `IdempotencyCoordinator`. It wraps `IdempotencyRepositoryPort` and `DefaultIdempotencyRule` and exposes typed lifecycle decisions:

```text
new execution started
completed replay available
same request still in progress
same key with different request hash
previous execution failed
repository failure
```

Workflow services continue to own:

- their action and source names;
- the canonical request fields included in their hash;
- mapping lifecycle decisions to their existing result and error types;
- the concrete success snapshot passed to the codec.

The coordinator owns find/start/evaluate/complete/fail mechanics and expiry calculation. It must not open an independent business transaction. Existing outer transaction boundaries and the deliberate failure-recording behaviour outside rolled-back business work are preserved.

### Operation evidence writer

The `audit` application module owns `OperationEvidenceWriter`. It receives already-decided `BusinessEvent`, `StateTransitionLog`, and `AuditLog` records and writes them through their existing ports in an explicit order.

The writer does not invent event types, transition codes, metadata, actor identity, or before/after state. Workflow-specific evidence factories remain close to each use case. A typed `EvidenceWriteException` identifies whether event, transition, or audit persistence failed, and each workflow maps it to its existing stable error.

This boundary removes repeated repository try/catch logic while preserving the exact evidence content and failure semantics.

### Temporary table-group capability

The Table application module introduces an injected `TemporaryTableGroupCreator` capability. The existing `TemporaryTableGroupApplicationService` implements it. Reservation seating, Queue seating, and Walk-in seating depend on the interface and never instantiate the implementation directly.

This keeps temporary table-group rules and persistence owned by the Table module, allows Spring transaction and future observability proxies to apply, and ensures all workflows use the caller's injected clock.

The existing management operations for saving and dissolving temporary groups remain on their current service/API surface.

### Seating resource planning and occupancy

The three seating workflows share stable resource concepts but retain separate use-case orchestration.

`SeatingResourcePlanner` resolves a command's table, existing group, or temporary group into an immutable `SeatingResourcePlan` containing:

- resource type and ID;
- selected table or group;
- member tables;
- temporary-group indicator;
- capacity and availability evidence needed by the caller.

`TableOccupancyCoordinator` validates locks and transitions planned tables through the existing `DiningTableStateMachine`, then persists the existing table states through current ports.

Workflow-specific rules remain separate:

- Reservation seating retains reservation status, business-date, preassignment, and check-in rules.
- Queue seating retains QueueTicket status, Reservation/WalkIn source, and queue evidence rules.
- Walk-in seating retains customer resolution, WalkIn creation, and hold-lock rules.

The design explicitly forbids replacing these with one generic "seat anything" service.

## Workflow Migration Order

### Reservation arrived direct seating

Migrate first because it is the largest workflow and has the strongest focused unit and API coverage. Extract snapshot codec, idempotency lifecycle, evidence writes, injected temporary-group creation, resource planning, and occupancy behind characterization tests. Keep the public service methods and result/error types unchanged.

### Seating from called queue

Migrate second and reuse only the capabilities proven by Reservation seating. Preserve QueueTicket, related Reservation/WalkIn, already-seated, and App Gate behaviour.

### Walk-in direct seating

Migrate third. Replace direct system-time access with the injected clock before moving resource planning. Preserve customer creation/lookup, table lock duration, WalkIn creation, and evidence semantics.

Each migration must reduce the workflow service to orchestration and workflow-specific decisions. There is no mandatory line-count gate, but a remaining large block must have one coherent reason to change.

## Frontend Design

### Behaviour-test foundation

Add Vitest, Vue Test Utils, and jsdom using versions compatible with the existing Vue 3 and Vite 6 stack. Add focused test scripts without replacing the existing `test:login-routing` command.

Before moving production logic, add behavioural tests for:

- initial load, loading, success, empty, and API error states;
- filter changes and query construction;
- action confirmation, disabled state, duplicate-click suppression, success refresh, and failure recovery;
- App Gate blocking errors;
- route construction for seating and table switching;
- dialog open/reset/close and create success;
- fixed-clock date formatting and business-date boundaries.

Existing Java source-validation tests remain during migration. They may be reduced only after equivalent runtime behaviour tests exist.

### Table resource workbench

`TableResourceListPage.vue` becomes the route-level composition shell. Extract:

- `useTableResourceQuery` for resource and calendar loading;
- `useTableResourceFilters` for status, area, party size, and displayed collections;
- `useTableCleaningCommands` for single and bulk cleaning;
- `useTemporaryTableGroupCommands` for selection, save, dissolve, and capacity calculation;
- `useResourceSeatingCommands` for Reservation and Queue seating actions;
- focused toolbar, summary, area-section, resource-card, and action-feedback components.

The route page retains Store/route coordination and composes the capabilities. CSS moves with the component it styles. Stable selectors used by current validation remain until replacement behavioural assertions are active.

### Queue workbench

`QueueTicketListPage.vue` becomes a route-level shell. Extract:

- `useQueueTicketQuery` for list and table-resource loading;
- `useQueueTicketFilters` for status, area, party-size group, and phone filters;
- `useQueueTicketCommands` for call, skip, rejoin, cancel, and post-action refresh;
- a discriminated `QueueActionState` instead of parallel per-action loading/error/success collections;
- focused filter, summary, ticket-card, action-bar, and feedback components.

All existing action availability rules, idempotency-key creation, confirmation text, and App Gate error mapping remain behaviourally identical.

### Create Reservation dialog

`CreateReservationDialog.vue` remains the public parent component and keeps its props and emitted events. Extract:

- `useReservationDraft` for form state and normalization;
- `useReservationAvailability` for date, meal-period, slot, and table-resource loading;
- `useReservationSubmission` for validation, idempotency, and create execution;
- `useReservationShare` for share-info loading, intent recording, copy, and channel launch;
- focused guest, schedule, resource, confirmation, and share sections.

The parent owns the step flow and dialog lifecycle. No extracted child calls APIs directly unless it is an explicitly named composable capability.

## Persistence And API Adapter Design

### Reservation preassignment queries

Keep the existing database schema and SQL semantics. Split entity mutation from read-model queries:

- the Spring Data repository retains entity lookup, save, and release operations;
- a `ReservationResourceAssignmentQueryRepository` owns the native projection queries;
- the repeated active-assignment predicate is centralized in one JDBC query implementation or shared SQL constant with parameterized filters.

No view, function, migration, or dependency is introduced. Query characterization tests compare current and extracted results for confirmed, arrived, seated-and-occupied, seated-and-cleaning, released, deleted, cross-tenant, and cross-store cases.

### Authentication persistence

Introduce application-facing ports for challenge, account, session, and authorization-store access. Move persistence records out of the concrete `AuthRepository` nested types. `AuthApplicationService` stops importing concrete persistence and API DTO/error types.

Customer authentication adapters are separated by their existing ports: customer auth/session, email settings, OAuth settings, and integration management. They may share package-private row mappers and SQL helpers, but no public adapter implements four unrelated ports.

### Platform Store and Billing boundary

Store structure persistence stops updating product subscription tables directly. Platform application orchestration calls an explicit Platform Billing port inside the existing transaction. The billing adapter owns subscription-item cancellation and aggregate refresh SQL. Store deletion behaviour and transaction atomicity remain unchanged.

### Tenant Admin controllers

Split the current controller into profile, staff, customer, table, settings, and meal-period controllers under the same base path. Move common exception mapping to scoped controller advice and keep `TenantAdminScopeResolver` as the shared scope boundary.

The split must not alter endpoint paths, methods, request/response DTOs, annotations, App Gate enforcement, or status mapping.

## Test Organization

Large tests are split only after production seams exist. Test classes are organized by behaviour rather than by arbitrary line count:

- happy path and domain transitions;
- idempotency replay/conflict/in-progress/failed;
- tenant/store/App Gate authorization;
- resource availability, capacity, lock, and preassignment conflicts;
- audit/event/transition evidence;
- repository and transaction failures.

Reusable test fixtures move into module-scoped builders and in-memory port fakes. Assertions remain in scenario tests; helpers must not hide the business outcome being verified.

Repository tests include tenant and store isolation for every extracted query boundary. Frontend tests exercise runtime DOM and action state rather than implementation strings.

## Transaction, Error, And Compatibility Rules

- Preserve the current outer transaction on each business command.
- Preserve the current ordering of aggregate persistence, evidence writes, and idempotency completion.
- Preserve the deliberate separation between rolled-back business work and failure idempotency/audit recording where it currently exists.
- Preserve all stable error enums and API mappings.
- Preserve existing completed idempotency records by decoding legacy snapshots.
- Treat snapshot decode failure exactly as the current idempotency-conflict path.
- Do not swallow audit, event, transition, or repository failures that currently fail a command.
- Do not allow a shared capability to perform an unscoped Store query.

## Phased Delivery

This document is an umbrella design, not a single implementation batch. Detailed implementation planning is divided into independently testable plans so that backend foundations, each workflow, each frontend surface, and later adapter cleanup can be reviewed or stopped separately:

1. snapshot, time, and frontend-test foundations;
2. idempotency, evidence, and temporary table-group capabilities;
3. Reservation arrived direct seating migration;
4. seating from called QueueTicket migration;
5. Walk-in direct seating migration;
6. Table resource workbench decomposition;
7. Queue workbench decomposition;
8. Create Reservation dialog decomposition;
9. Reservation preassignment query decomposition;
10. Auth and Customer Auth port/adapter decomposition;
11. Platform Store/Billing boundary correction;
12. Tenant Admin controller and large-test decomposition.

Later plans consume only interfaces and behaviour established by earlier completed plans. A plan must not begin merely because the umbrella phase exists; its declared prerequisites must already be merged and passing.

### Phase 1: Safety net and leaf capabilities

- Add legacy snapshot fixtures and typed codec tests.
- Add fixed-clock Store business-time tests.
- Add Vue component/composable test infrastructure and first behaviour tests.
- Introduce codecs and time boundaries without moving workflow orchestration.

### Phase 2: Shared application capabilities

- Introduce and test the idempotency coordinator.
- Introduce and test the operation evidence writer.
- Introduce the temporary table-group capability and replace direct construction.

### Phase 3: Seating workflow migration

- Migrate Reservation arrived direct seating.
- Migrate seating from called QueueTicket.
- Migrate Walk-in direct seating.
- Run focused unit, API, transaction, App Gate, tenant/store, and local-runtime tests after each workflow.

### Phase 4: Frontend decomposition

- Decompose Table resource workbench.
- Decompose Queue workbench.
- Decompose Create Reservation dialog.
- Run runtime behaviour tests, existing validation tests, login-routing tests, and production build after each page.

### Phase 5: Persistence, controller, and test decomposition

- Split Reservation preassignment read queries.
- Split Auth and Customer Auth persistence adapters behind ports.
- Move Billing writes behind a Billing port.
- Split Tenant Admin controllers without API changes.
- Split large tests and consolidate module-scoped fixtures.

## Validation Gates

Every task must run its smallest focused tests first. Every completed phase must also pass:

- `npm run build`;
- `npm run test:login-routing`;
- all new Vitest behaviour tests;
- focused Maven tests for changed modules;
- relevant App Gate, tenant/store isolation, duplicate submission, idempotency, evidence, and transaction tests;
- local PostgreSQL integration tests when persistence or transaction behaviour is touched, using the runtime referenced by `target/local-postgres-current.txt`;
- `git diff --check` and a clean review of the intended diff.

The pre-refactor baseline recorded during design is:

- frontend production build passed;
- login routing passed 7 of 7 tests;
- Reservation seating, Queue seating, and staff reception validation passed 53 of 53 focused Maven tests;
- the Git worktree was clean;
- database-dependent full integration tests were not run.

## Acceptance Criteria

- No existing API contract or database schema changes.
- No state-machine transition changes.
- No direct application-service construction remains in the three migrated seating workflows.
- Migrated workflows use injected clocks and typed snapshot codecs.
- Shared idempotency and evidence capabilities replace duplicated lifecycle mechanics without changing results or evidence.
- Table, Queue, and Create Reservation route-level components are composition shells with behaviourally tested composables and child components.
- Application services depend on ports rather than concrete persistence adapters in every migrated repository area.
- Repeated Reservation resource-assignment query semantics have one owned implementation.
- Existing and new focused tests pass after every incremental migration.

## Release And Rollback

The work is delivered as a sequence of behaviour-preserving commits. Each capability introduction precedes the workflow that consumes it. No phase requires a coordinated database deployment.

Rollback is performed at the smallest migrated workflow or page boundary. Snapshot readers remain backward compatible, so rolling application code backward does not require data repair. If a new shared capability causes a regression, revert the consuming workflow before removing the capability. Frontend rollback restores the previous route-level component while leaving test infrastructure in place.
