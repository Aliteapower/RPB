# Repository Port Design V1

## Purpose

This document designs Repository Port boundaries for the Reservation Platform backend.

It is design-only. It does not create Java interfaces, Spring Data repositories, repository implementations, entities, mappers, services, controllers, API DTOs, UI, SQL, migrations, seed data, or mock data.

## Port Design Principles

- Repository Port is an application-facing abstraction.
- Repository Port is not Spring Data Repository.
- Repository Port is not generated mechanically from database tables.
- Application Service depends on Repository Port.
- Repository Implementation depends on Spring Data, JPA, Entity, and Mapper later.
- Domain Object does not depend on Repository Port.
- Repository Port returns Domain Object, domain projection, or simple decision values.
- Repository Port must not return Persistence Entity to Domain Object or Application Service.
- Every Store operation query must accept `StoreScope`.
- Every Tenant-level query must accept `TenantScope`.
- Platform-level queries must use `PlatformScope` or a clearly documented no-tenant boundary in a later platform round.
- Cross-tenant queries are forbidden unless explicitly designed as platform administration use cases in a later round.

## Common Input and Output Boundaries

Recommended common inputs:

- `TenantScope`
- `StoreScope`
- Future `PlatformScope`
- Domain identity value objects such as `ReservationId`, `QueueTicketId`, `CustomerId`, `TableId`
- Domain filter value objects such as `BusinessDate`, `TimeRange`, `PartySize`, or status enum sets
- Optional `IdempotencyKey` only for ports that guard idempotent command execution

Recommended outputs:

- Domain Object
- Domain projection or read model explicitly designed later
- Optional domain object
- Boolean existence result
- Count or ordered id list where a full aggregate is unnecessary

Not allowed as outputs:

- Persistence Entity
- Spring Data Page of Entity
- JPA Tuple
- Raw Object array
- DTO intended for API response
- Unscoped database row map

## Repository Port Catalog

| Port | Domain Purpose | Minimum Method Suggestions | Inputs | Outputs | Scope | Idempotency / Audit | Must Not Expose |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `TenantRepositoryPort` | Tenant lifecycle and tenant root lookup. | `findById(scope)`, `findByCode(platformScope, tenantCode)`, `save(tenant)` | `TenantScope`, future `PlatformScope`, tenant code, `Tenant` | `Tenant` or optional `Tenant` | Tenant or Platform | Audit needed for lifecycle changes; idempotency optional for onboarding later | Store operations, Entity, cross-tenant customer data |
| `StoreRepositoryPort` | Store profile, locale, timezone, and current policy lookup. | `findById(scope)`, `findOperationalProfile(scope)`, `findCurrentPolicy(scope, at)`, `save(store)`, `savePolicy(scope, policy)` | `StoreScope`, `Instant`, `Store`, `StorePolicy` | `Store`, `StorePolicy`, optional profile projection | Store | Audit needed for config changes; idempotency optional for external config calls | Table occupancy, reservation logic, Entity |
| `DiningTableRepositoryPort` | Dining table resource lookup and status persistence boundary. | `findById(scope, tableId)`, `findActiveByArea(scope, areaId)`, `findCandidates(scope, partySize, businessDate)`, `save(table)` | `StoreScope`, `TableId`, area id, `PartySize`, `BusinessDate`, `DiningTable` | `DiningTable`, table candidate projection | Store | Audit needed for status/config changes; idempotency generally not here | Assignment decision, active seating mutation, Entity |
| `TableGroupRepositoryPort` | Fixed and temporary table group lookup and membership boundary. | `findById(scope, tableGroupId)`, `findActiveMembers(scope, tableGroupId)`, `findActiveGroupsForTable(scope, tableId)`, `save(tableGroup)`, `saveMember(scope, member)` | `StoreScope`, `TableGroupId`, `TableId`, `TableGroup`, `TableGroupMember` | `TableGroup`, member list, existence result | Store | Audit needed for group creation/release; idempotency recommended for temporary group commands later | Circular validation decision, table assignment decision, Entity |
| `TableLockRepositoryPort` | Durable table or table group lock boundary. | `findActiveByResource(scope, resourceTarget)`, `existsActiveConflict(scope, resourceTarget, now)`, `save(lock)`, `release(scope, lockId, releasedAt)` | `StoreScope`, lock resource target, `Instant`, `TableLock` | `TableLock`, boolean conflict result | Store | Idempotency required for lock acquire/release; audit recommended | Final occupancy, Redis live lock details, Entity |
| `CustomerRepositoryPort` | Tenant-scoped customer identity lookup and persistence. | `findById(scope, customerId)`, `findByCode(scope, customerCode)`, `findByPhone(scope, phone)`, `searchNoPhoneCandidates(scope, criteria)`, `save(customer)` | `TenantScope`, `CustomerId`, code, nullable phone criteria, no-phone search criteria, `Customer` | `Customer`, optional `Customer`, candidate projection | Tenant | Audit needed for merge/archive; idempotency optional for registration | Store-only identity, Member/loyalty/payment/marketing, Entity |
| `ReservationRepositoryPort` | Reservation aggregate lookup, active conflict checks, and persistence. | `findById(scope, reservationId)`, `findByCode(scope, reservationCode)`, `findStoreSchedule(scope, businessDate, timeRange)`, `existsActiveConflict(scope, customerId, timeRange)`, `findActiveConflicts(scope, customerId, timeRange)`, `save(reservation)` | `StoreScope`, `ReservationId`, `ReservationCode`, `BusinessDate`, `TimeRange`, nullable `CustomerId`, `Reservation` | `Reservation`, schedule projection, conflict result | Store | Idempotency required for create/confirm/check-in; audit required for critical transitions | QueueTicket creation, table assignment, Entity |
| `QueueTicketRepositoryPort` | Queue ticket lookup, active queue ordering, next callable ticket, and persistence. | `findById(scope, queueTicketId)`, `findActiveQueue(scope, queueGroupId, businessDate)`, `findNextCallable(scope, queueGroupId, businessDate)`, `existsActiveSourceTicket(scope, sourceRef)`, `save(queueTicket)` | `StoreScope`, `QueueTicketId`, `queueGroupId`, `BusinessDate`, source ref, `QueueTicket` | `QueueTicket`, active queue projection, optional next ticket | Store | Idempotency required for create/call/rejoin; audit required for queue state changes | Reservation capacity, final seating resource, Entity |
| `WalkInRepositoryPort` | Walk-in arrival scenario lookup and persistence. | `findById(scope, walkInId)`, `findByCode(scope, walkInCode)`, `findArrivals(scope, businessDate, statusSet)`, `save(walkIn)` | `StoreScope`, `WalkInId`, walk-in code, `BusinessDate`, status filters, `WalkIn` | `WalkIn`, arrival projection | Store | Idempotency recommended for arrival intake; audit required for cancel/abandon/seat | QueueTicket lifecycle, Reservation creation, Entity |
| `SeatingRepositoryPort` | Seating aggregate lookup, source uniqueness, active occupancy, and persistence. | `findById(scope, seatingId)`, `findActiveBySource(scope, seatingSource)`, `existsActiveResourceOccupancy(scope, resourceTarget)`, `findActiveOccupancy(scope, resourceTarget)`, `save(seating)` | `StoreScope`, `SeatingId`, `SeatingSource`, seating resource target, `Seating` | `Seating`, occupancy projection, boolean result | Store | Idempotency required for seating command; audit required | Table assignment decision, cleaning completion, Entity |
| `CleaningRepositoryPort` | Cleaning flow lookup, active cleaning lookup, and persistence. | `findById(scope, cleaningId)`, `findActiveByResource(scope, cleaningResourceTarget)`, `findBySeating(scope, seatingId)`, `save(cleaning)` | `StoreScope`, `CleaningId`, `SeatingId`, cleaning target, `Cleaning` | `Cleaning`, active cleaning projection | Store | Idempotency required for completion/release; audit required | Turnover calculation, payment/marketing, Entity |
| `TurnoverRepositoryPort` | Turnover result lookup and recorded metric persistence. | `findBySeating(scope, seatingId)`, `findByBusinessDate(scope, businessDate)`, `save(turnover)` | `StoreScope`, `SeatingId`, `BusinessDate`, `Turnover` | `Turnover`, business-date turnover projection | Store | Audit recommended for manual correction; idempotency optional for generated result | Live seating or cleaning actions, BI analytics engine, Entity |
| `BusinessEventRepositoryPort` | Append and query business events such as CheckIn, call, skip, rejoin, and manual override. | `append(scope, event)`, `findByTarget(scope, targetRef)`, `findTimeline(scope, timeRange)` | Contextual scope, `BusinessEvent`, `TargetRef`, `TimeRange` | `BusinessEvent`, event timeline projection | Contextual Platform/Tenant/Store | Idempotency key when event is command-driven; audit linkage recommended | Primary entity replacement, forced FK target, Entity |
| `StateTransitionLogRepositoryPort` | Append and query state transition evidence. | `append(scope, transitionLog)`, `findByTarget(scope, targetRef)`, `findLatest(scope, targetRef)` | Contextual scope, `StateTransitionLog`, `TargetRef` | `StateTransitionLog`, optional latest transition | Contextual Platform/Tenant/Store | Idempotency key recommended for command transitions; audit linkage optional | State machine validation, Entity |
| `AuditLogRepositoryPort` | Append and query audit trail. | `append(scope, auditLog)`, `findByTarget(scope, targetRef)`, `findByOperation(scope, operationCode, timeRange)` | Contextual scope, `AuditLog`, `TargetRef`, operation code, `TimeRange` | `AuditLog`, audit timeline projection | Contextual Platform/Tenant/Store | Audit is the purpose; idempotency key retained when related | Business event ownership, Entity |
| `IdempotencyRepositoryPort` | Idempotency guard for critical commands and integration calls. | `findByScopeActionKey(scope, source, action, key)`, `start(scope, source, action, key, requestHash, expiresAt)`, `complete(record, targetRef, responseSnapshot)`, `fail(record, failureReason)`, `expire(record)` | Platform/Tenant/Store scope, source, action, `IdempotencyKey`, request hash, `TargetRef`, snapshot | `IdempotencyRecord`, optional record, replay decision projection later | Platform/Tenant/Store | Core idempotency boundary; audit recommended for failures | Table lock semantics, business validation, Entity |
| `ReasonCodeRepositoryPort` | Reason code lookup for cancellation, no-show, skip, override, cleaning, and table release. | `findActiveByType(scope, reasonType)`, `findByCode(scope, reasonType, code)`, `save(reasonCode)` | `TenantScope` or `StoreScope`, reason type, code, `ReasonCode` | `ReasonCode`, active reason list projection | Tenant or Store override | Audit required for configuration changes; idempotency optional | Display rendering, transition execution, Entity |
| `I18nMessageRepositoryPort` | i18n message catalog lookup by scope, key, and locale. | `findMessage(scope, i18nKey, locale)`, `findFallbackChain(scope, i18nKey, locale)`, `save(message)` | Platform/Tenant/Store scope, `I18nKey`, locale, `I18nMessage` | `I18nMessage`, message lookup projection | Platform/Tenant/Store | Audit required for message changes; idempotency optional | Business rule text hardcoding, UI component logic, Entity |

## Scope Requirements By Port

Tenant scope:

- `TenantRepositoryPort` for tenant-owned lifecycle lookups.
- `CustomerRepositoryPort`.
- Tenant-level `ReasonCodeRepositoryPort` calls.
- Tenant-level `I18nMessageRepositoryPort` calls.

Store scope:

- `StoreRepositoryPort`.
- `DiningTableRepositoryPort`.
- `TableGroupRepositoryPort`.
- `TableLockRepositoryPort`.
- `ReservationRepositoryPort`.
- `QueueTicketRepositoryPort`.
- `WalkInRepositoryPort`.
- `SeatingRepositoryPort`.
- `CleaningRepositoryPort`.
- `TurnoverRepositoryPort`.
- Store-level `ReasonCodeRepositoryPort` calls.
- Store-level `I18nMessageRepositoryPort` calls.

Contextual scope:

- `BusinessEventRepositoryPort`.
- `StateTransitionLogRepositoryPort`.
- `AuditLogRepositoryPort`.
- `IdempotencyRepositoryPort`.

No-scope query rule:

- Store operational data must not expose no-scope query methods.
- Tenant customer data must not expose cross-tenant lookup.
- Generic event/audit/transition target query must still include contextual scope.

## Read / Write Boundary

Read capabilities:

- Should be shaped around business decisions: conflict check, active queue, next callable, active occupancy, current policy, active reason list.
- May return read projections later where full aggregate loading is wasteful.
- Must still enforce scope.

Write capabilities:

- Should save domain aggregates or append immutable records.
- Should not expose physical delete.
- Should support optimistic version checks where relevant later.
- Should not bypass state machine, rule, policy, validator, audit, or idempotency orchestration.

## Entity Exposure Rule

Allowed inside Repository Implementation later:

- Persistence Entity.
- Spring Data Repository.
- JPA EntityManager.
- Mapper.

Not allowed outside Repository Implementation:

- Persistence Entity returned to Application Service.
- Spring Data Repository injected into Application Service.
- JPA Entity passed into Domain Object.
- Mapper used by Controller or API DTO layer.

## Idempotency Requirements

Ports that directly support idempotent command handling:

- `IdempotencyRepositoryPort`.
- `ReservationRepositoryPort` for create, confirm, and CheckIn-related state changes through Application Service.
- `QueueTicketRepositoryPort` for create, call, and rejoin.
- `SeatingRepositoryPort` for seating commands.
- `CleaningRepositoryPort` for completion/release.
- `TableLockRepositoryPort` for lock acquire/release.

Idempotency belongs to Application Service orchestration plus `IdempotencyRepositoryPort`; business repositories should preserve target persistence but not decide replay alone.

## Audit Requirements

Audit is required for:

- Reservation creation, confirmation, cancellation, no-show, CheckIn event.
- QueueTicket creation, call, skip, rejoin, cancellation, expiry.
- WalkIn arrival, queue, direct seating, cancellation, abandonment.
- Seating, table change, manual override.
- TableGroup creation, use, release, end.
- Cleaning start, completion, release.
- Critical store configuration, reason code, and i18n changes.
- Idempotency failures or suspicious replay where policy requires.

Repository Port should support persistence of audit/event records, but the decision to audit belongs to Application Service orchestration and audit rules.

## Mechanical CRUD Prohibition

Do not add generic methods unless a later use case proves they are needed:

- `findAll`
- `delete`
- `update`
- `saveAll`
- `getOne`
- unscoped `list`

Preferred method shape:

- Scoped.
- Named by business reason.
- Minimal output.
- Explicit about active vs historical data.
- Explicit about source/resource target where relevant.

## Not Implemented In This Round

- No Java repository interface.
- No Spring Data repository.
- No repository implementation.
- No entity.
- No mapper.
- No application service.
- No controller.
- No API.
- No UI.
- No database connection.
