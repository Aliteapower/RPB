# Backend Domain Model Design V1

## Purpose

This document defines the backend domain model boundaries for the Reservation Platform after the V1 migration validation passed.

This is a design document only. It does not create Java code, Entity classes, Repositories, Services, Controllers, DTOs, API routes, UI, tests, migrations, SQL, seed data, mock data, configuration, Docker files, CI/CD files, or dependencies.

## Inputs Read

Governance documents:

- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/skills/reservation-system/SKILL.md`

Database documents:

- `docs/database/DATABASE_DESIGN.md`
- `docs/database/ERD.md`
- `docs/database/DATA_MODEL_CHECKLIST.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_PLAN.md`
- `docs/database/SCHEMA_REVIEW_CHECKLIST.md`
- `docs/database/MIGRATION_REVIEW_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `docs/database/migrations/V001__reservation_platform_bootstrap.sql`

Migration validation confirmed:

- PostgreSQL version: 17.10.
- Execution method: temporary local empty PostgreSQL cluster with `psql -v ON_ERROR_STOP=1`.
- Migration execution: completed successfully.
- Catalog result: 24 tables, 55 check constraints, 69 foreign keys, 107 indexes, and `pgcrypto` enabled.

## OOD First Principle

Backend design must follow this order:

```text
Business Object
-> Responsibility Boundary
-> State Machine
-> Rule / Policy / Validator
-> Reusable Capability
-> Code Implementation
```

The domain model must not be organized around pages, controllers, database mappers, or single feature handlers.

Forbidden future design shapes:

- One Service owns Reservation, QueueTicket, WalkIn, Seating, Cleaning, and Turnover together.
- One Controller contains business state transitions.
- One DTO represents several unrelated workflows.
- One status field has multiple business meanings.
- One method creates reservations, calls queues, seats guests, and completes cleaning together.

## Domain Object Catalog

| Domain Object | Table | Scope | Business Responsibility | Not Responsible For | Core Fields | Lifecycle / Status | Upstream / Downstream | Invariants, Methods, Boundaries |
|---|---|---|---|---|---|---|---|---|
| Tenant | `tenants` | Platform-owned Tenant root | Tenant lifecycle, isolation root, tenant code, default locale. | Store operations, table resources, queue or seating decisions. | `id`, `tenant_code`, `display_name`, `status`, `default_locale`, audit/version fields. | `created`, `active`, `suspended`, `closed`. | Upstream: Platform. Downstream: Store, Customer, Tenant-scoped rules. | Invariant: no cross-Tenant sharing. Methods: activate, suspend, close, rename. Boundary: never substitutes for Store. |
| Store | `stores` | Tenant + Store | Physical or operational branch, Store timezone, locale, date/time format, currency, Store status. | Customer identity across Tenant, table occupancy, reservation rules by itself. | `tenant_id`, `store_code`, `timezone`, `locale`, `date_format`, `time_format`, `currency`, `status`. | `created`, `active`, `inactive`, `archived`. | Upstream: Tenant. Downstream: StorePolicy, Area, DiningTable, Reservation, QueueTicket, Seating, Cleaning. | Invariant: belongs to one Tenant. Methods: activate, archive, updateLocale, validateOperational. Boundary: Store owns operational scope but not Customer uniqueness. |
| StorePolicy | `store_policies` | Store configuration | Store defaults for reservation hold, queue call hold, expected dining duration, queue rejoin policy, table assignment policy. | Individual Reservation or QueueTicket status. | `reservation_hold_minutes`, `queue_call_hold_minutes`, `expected_dining_minutes`, policy codes, effective range. | Effective current policy where `effective_to_at` is null; soft-deletable history. | Upstream: Store. Downstream: ReservationHoldPolicy, QueueCallingRule, DateTimePolicy, TableAssignmentRule. | Invariant: one current active policy per Store. Methods: currentForStore, effectiveAt, replacePolicy. Boundary: config only, not business action history. |
| Area | `store_areas` | Store resource | Store zone for table grouping and assignment priority. | Customer identity, queue number, reservation ownership. | `area_code`, `display_name`, `status`, `sort_order`. | `created`, `active`, `inactive`, `archived`. | Upstream: Store. Downstream: DiningTable. | Invariant: unique active code inside Store. Methods: activate, archive, reorder. Boundary: Area does not own capacity by itself. |
| DiningTable | `dining_tables` | Store resource | Physical table resource, capacity range, combinability, current table status. | Customer identity, queue ordering, final seating source. | `area_id`, `table_code`, `capacity_min`, `capacity_max`, `status`, `is_combinable`. | `available`, `locked`, `reserved`, `occupied`, `cleaning`, `inactive`. | Upstream: Store, Area. Downstream: TableGroupMember, ReservationPreassignment, SeatingResource, Cleaning, TableLock. | Invariants: capacity positive; active occupancy cannot overlap; inactive cannot be assigned. Methods: lock, reserve, occupy, startCleaning, releaseAvailable, deactivate. Boundary: Table is not TableGroup. |
| TableGroup | `table_groups` | Store resource | Fixed or temporary combined table resource with capacity and lifecycle. | Base Table definition, customer identity, queue ordering. | `group_code`, `group_type`, `status`, `capacity_min`, `capacity_max`, `active_from_at`, `active_until_at`. | Fixed: `created`, `active`, `inactive`, `deleted`. Temporary: `created`, `locked`, `occupied`, `released`, `ended`. | Upstream: Store, DiningTable members. Downstream: ReservationPreassignment, SeatingResource, Cleaning, TableLock. | Invariants: same Tenant/Store members; no group-to-group nesting in V1; temporary groups release members. Methods: activateFixed, lockTemporary, occupyTemporary, release, end. Boundary: not a replacement for member DiningTables. |
| TableGroupMember | `table_group_members` | Store join boundary | Connects one TableGroup to one DiningTable. | Group lifecycle, seating occupancy, capacity calculation alone. | `table_group_id`, `table_id`, `member_role`, `deleted_at`. | Active while not soft-deleted. | Upstream: TableGroup, DiningTable. Downstream: TableGroupValidationRule, TableAssignmentRule. | Invariants: member table and group share Tenant/Store; no duplicate active member in same group. Methods: addMember, removeMember. Boundary: cannot reference another TableGroup. |
| TableLock | `table_locks` | Store operation | Durable lock record for DiningTable or TableGroup assignment decisions. | Final occupancy, Reservation existence, QueueTicket ordering. | `resource_type`, `resource_id`, `lock_key`, `lock_owner`, `locked_until_at`, `source_type`, `source_id`, `status`, `idempotency_key`. | `active`, `released`, `expired`, `cancelled`. | Upstream: Reservation, QueueTicket, WalkIn, Seating, manual/system source. Downstream: SeatingResource, TableAvailabilityRule. | Invariants: active lock unique by resource and lock key; `locked_until_at > locked_at`. Methods: acquire, release, expire, cancel, isActiveAt. Boundary: generic resource id requires domain validation. |
| Customer | `customers` | Tenant shared | Tenant-scoped customer identity including anonymous, temporary, no-phone, walk-in guest, boss friend, and special-note customers. | Member/loyalty, marketing, payment. | `customer_code`, `customer_type`, `display_name`, `nickname`, `phone_e164`, `email`, `lookup_note`, `status`, `merged_into_customer_id`. | `active`, `merged`, `archived`. | Upstream: Tenant. Downstream: Reservation, WalkIn, QueueTicket. | Invariants: Tenant-scoped uniqueness; phone nullable; E.164 if present. Methods: register, anonymize, updatePhone, mergeInto, archive. Boundary: Customer is not Member. |
| Reservation | `reservations` | Store operation | Advance Store + date + time slot + party-size capacity intent, reservation status, hold window, optional preassignment boundary. | Queue number, queue calling, live table occupancy, cleaning, turnover metric. | `customer_id`, `reservation_code`, `party_size`, `business_date`, `reserved_start_at`, `reserved_end_at`, `hold_until_at`, `status`, reason codes. | `draft`, `confirmed`, `arrived`, `seated`, `completed`, `cancelled`, `no_show`. | Upstream: Store, Customer, StorePolicy. Downstream: CheckIn event, optional QueueTicket, Seating, ReservationPreassignment. | Invariants: party size positive; end after start; active same-customer slot uniqueness. Methods: confirm, checkIn, seat, complete, cancel, markNoShow. Boundary: Reservation is not QueueTicket and not Seating. |
| ReservationPreassignment | `reservation_preassignments` | Store operation | Optional planned resource preassignment before final Seating. | Final occupancy and QueueTicket creation. | `reservation_id`, `resource_type`, `table_id`, `table_group_id`, `status`, `preassigned_at`, `released_at`. | `active`, `released`, `cancelled`. | Upstream: Reservation, DiningTable or TableGroup. Downstream: Seating proposal, TableLockRule. | Invariants: exactly one resource target; same Tenant/Store; active preassignment does not equal occupied table. Methods: assignTable, assignGroup, release, cancel. Boundary: not a SeatingResource. |
| QueueGroup | `queue_groups` | Store configuration | Store-scoped queue grouping policy. V1 groups by party size: 1-2, 3-4, 5-6, 7+. | Individual waiting lifecycle, table assignment. | `group_code`, `min_party_size`, `max_party_size`, `display_i18n_key`, `status`, `sort_order`. | `active`, `inactive`. | Upstream: Store, StorePolicy. Downstream: QueueTicket. | Invariants: valid non-overlapping party-size band by Store policy; display through i18n key. Methods: matchesPartySize, activate, deactivate, reorder. Boundary: not a queue ticket. |
| QueueTicket | `queue_tickets` | Store operation | Waiting record after arrival, ticket number, queue position, call, skip, rejoin, cancellation, expiry. | Advance reservation capacity, final table occupancy, cleaning, turnover. | `queue_group_id`, `customer_id`, `reservation_id`, `walk_in_id`, `ticket_number`, `party_size`, `business_date`, `status`, queue timestamps. | `waiting`, `called`, `skipped`, `rejoined`, `seated`, `cancelled`, `expired`. | Upstream: arrived Reservation or WalkIn, QueueGroup, Customer. Downstream: Seating. | Invariants: party size positive; at most one Reservation/WalkIn source; number unique in QueueGroup/day. Methods: call, skip, rejoin, seat, cancel, expire. Boundary: QueueTicket is not Reservation and not WalkIn. |
| WalkIn | `walk_ins` | Store operation | On-site arrival without advance Reservation; can go direct to Seating or create QueueTicket when waiting is needed. | Advance booking, membership, payment, marketing. | `customer_id`, `walk_in_code`, `party_size`, `business_date`, `arrived_at`, `status`, `note`. | `arrived`, `queued`, `seated`, `cancelled`, `abandoned`. | Upstream: Store, Customer or temporary/anonymous customer context. Downstream: QueueTicket or Seating. | Invariants: party size positive; same Store scope; queue is optional. Methods: queue, seatDirectly, cancel, abandon. Boundary: WalkIn is not QueueTicket. |
| Seating | `seatings` | Store operation | Formal seating event and occupancy record with exactly one source and party-size snapshot. | Reservation creation, QueueTicket creation, cleaning completion. | `reservation_id`, `queue_ticket_id`, `walk_in_id`, `seating_code`, `party_size_snapshot`, `status`, `seated_at`, `completed_at`, `manual_override_reason_code`. | `planned`, `locked`, `occupied`, `completed`, `cleaning_triggered`, `cancelled`. | Upstream: exactly one Reservation, QueueTicket, or WalkIn. Downstream: SeatingResource, Cleaning, Turnover. | Invariants: source XOR; party size snapshot positive; completed requires completed time. Methods: plan, lock, occupy, complete, triggerCleaning, cancel. Boundary: Seating is not CheckIn or Reservation. |
| SeatingResource | `seating_resources` | Store operation | Resource assignment for a Seating to one DiningTable or TableGroup. | Seating source validation, customer identity. | `seating_id`, `resource_type`, `table_id`, `table_group_id`, `assigned_at`, `released_at`, `status`. | `active`, `released`, `cancelled`. | Upstream: Seating, DiningTable or TableGroup. Downstream: Cleaning, Turnover, Table availability. | Invariants: resource XOR; active resource occupancy unique; same Tenant/Store. Methods: assignTable, assignGroup, release, cancel. Boundary: not the Seating source. |
| Cleaning | `cleanings` | Store operation | Cleaning status flow after Seating completion or guest departure. | Turnover metric, payment, marketing, queue ordering. | `seating_id`, `resource_type`, `table_id`, `table_group_id`, `status`, `started_at`, `completed_at`, `released_at`. | `pending`, `cleaning`, `completed`, `released`, `cancelled`. | Upstream: Seating, SeatingResource. Downstream: DiningTable/TableGroup availability, Turnover. | Invariants: resource XOR; completed requires completed time; resource unavailable until completed/released. Methods: start, complete, releaseResource, cancel. Boundary: Cleaning is not Turnover. |
| Turnover | `turnovers` | Store operation result | Table-use cycle result or metric derived from Seating completion and Cleaning. | Live seating, queue calling, table locking, reservation creation. | `seating_id`, `cleaning_id`, `business_date`, `seated_at`, `completed_at`, `cleaning_completed_at`, `duration_minutes`, `status`. | `pending`, `recorded`, `archived`. | Upstream: Seating, Cleaning. Downstream: reporting boundary. | Invariants: not sourced from Reservation alone; duration non-negative. Methods: prepareFromSeating, recordFromCleaning, archive. Boundary: result only, not an action workflow. |
| BusinessEvent | `business_events` | Contextual audit/event scope | Domain event evidence such as CheckIn, queue call, skip, rejoin, manual override, table release. | Owning current state or replacing primary entities. | `event_type`, `target_type`, `target_id`, actor/source, before/after state, reason, idempotency key, metadata, `occurred_at`. | Append-only event record. | Upstream: any event-producing domain object. Downstream: AuditLog, StateTransitionLog, notifications/webhooks later. | Invariants: scope must match target; stable event types. Methods: recordEvent. Boundary: CheckIn is represented here but not as CheckInEntity. |
| StateTransitionLog | `state_transition_logs` | Contextual transition scope | Reusable history of state changes for stateful domain objects. | Legal transition decision by itself. | `target_type`, `target_id`, `from_status`, `to_status`, `transition_code`, `triggered_by`, actor/source snapshots, `audit_log_id`. | Append-only transition evidence. | Upstream: state machines. Downstream: audit, compliance, debugging. | Invariants: scope matches target; from/to status are stable codes. Methods: recordTransition. Boundary: not the current state owner. |
| AuditLog | `audit_logs` | Contextual audit scope | Mandatory audit for critical operations, overrides, failures, permission/config changes, integration calls. | Business state mutation by itself. | `operation_code`, `target_type`, `target_id`, actor/source, before/after state, reason, idempotency key, failure reason, metadata. | Append-only audit evidence. | Upstream: rules, policies, state machines, application services later. Downstream: compliance, support, investigation. | Invariants: critical flows cannot skip audit. Methods: recordSuccess, recordFailure. Boundary: not a business command handler. |
| IdempotencyRecord | `idempotency_records` | Platform/Tenant/Store scoped operation guard | Deduplicates critical commands and integration calls. | Business validation or state transition by itself. | `tenant_id`, `store_id`, `idempotency_key`, `source`, `action`, `target_type`, `target_id`, `request_hash`, `response_snapshot`, `status`, `expires_at`. | `started`, `completed`, `failed`, `expired`. | Upstream: Command source. Downstream: reservation create/confirm, CheckIn, queue create/call/rejoin, Seating, TableLock, Cleaning completion. | Invariants: scope/action/key uniqueness; replay uses request hash. Methods: start, complete, fail, expire, replay. Boundary: not a lock replacement. |
| ReasonCode | `reason_codes` | Tenant or Store override | Stable reason codes for cancellation, no_show, skip, override, cleaning, table release. | Transition execution or display rendering. | `reason_type`, `code`, `i18n_key`, `status`, `sort_order`. | `active`, `inactive`. | Upstream: Tenant/Store configuration. Downstream: Reservation, QueueTicket, Cleaning, AuditLog, BusinessEvent. | Invariants: active reason code unique in scope; display via i18n key. Methods: activate, deactivate, validateForType. Boundary: not free-form user text. |
| I18nMessage | `i18n_message_catalog` | Platform/Tenant/optional Store | Message catalog for stable domain and system display keys. | Business state, API response model, UI rendering logic. | `tenant_id`, `store_id`, `i18n_key`, `locale`, `message`, `status`. | `active`, `inactive`. | Upstream: platform/tenant/store message configuration. Downstream: reason labels, status display, future API/UI display. | Invariants: unique key/locale per scope; system text uses keys. Methods: resolveMessage, activate, deactivate. Boundary: not business rule ownership. |

## Confirmed Concept Boundaries

| Boundary | Backend Design Conclusion |
|---|---|
| Reservation vs QueueTicket | Reservation owns future capacity intent. QueueTicket owns waiting after arrival. QueueTicket may reference Reservation only after CheckIn and only when waiting is needed. |
| WalkIn vs QueueTicket | WalkIn is the arrival scenario. QueueTicket is optional waiting. A WalkIn can be seated directly. |
| CheckIn vs Seating | CheckIn is a BusinessEvent plus Reservation state transition and audit. Seating creates occupancy. No `CheckInEntity` should be designed as a primary domain entity in V1. |
| Seating vs Reservation | Seating is a separate occupancy object. It may update Reservation status but does not create or replace Reservation. |
| Cleaning vs Turnover | Cleaning is a resource status flow. Turnover is a result/metric derived from Seating and Cleaning. |
| Table vs TableGroup | DiningTable is the base resource. TableGroup is a fixed or temporary combination resource with members. |
| Customer vs Member | Customer is Tenant-scoped identity. Member, loyalty, points, and marketing are future integration boundaries outside V1. |

## Backend Package Structure Recommendation

This is a conceptual package recommendation only. No Java packages or files are created in this round.

```text
tenant/
store/
customer/
reservation/
queue/
walkin/
table/
table/group/
table/lock/
seating/
cleaning/
turnover/
audit/
idempotency/
i18n/
common/scope/
common/state/
common/rule/
common/time/
common/result/
```

Do not organize backend code by page, screen, admin area, mobile view, Controller, or API endpoint.

## Command / Query / DTO / Entity Boundary

| Concept | Meaning | Allowed Responsibility | Must Not Do |
|---|---|---|---|
| Command | Write-operation intent from an actor/source, such as create Reservation or call QueueTicket. | Carry intent, idempotency key, actor, scope, and required input. | Contain domain state machines or persistence annotations. |
| Query | Read-operation condition, such as store schedule lookup or active queue lookup. | Carry filters, scope, pagination/sort intent. | Mutate domain state. |
| Domain Object | Business object with invariants and behavior. | Own business meaning, status transitions, and invariants with help from rules/policies. | Be shaped by API JSON or database row convenience. |
| Persistence Entity | Database mapping for a table. | Map columns and relations for persistence. | Be treated as the full domain model. |
| DTO | API input/output representation. | Express external contract in later API rounds. | Become Domain Object or drive domain boundaries. |

Future implementation direction:

```text
Controller
-> Command / Query
-> Application Service
-> Domain Object + Rule / Policy / Validator
-> Repository
-> Persistence Entity
```

This round creates none of those classes.

## Open Questions

- None blocking for this backend domain model design round.
- Deferred V2 guest-composition fields remain outside V1 domain objects: `adult_count`, `child_count`, `infant_count`, `high_chair_required`, and `wheelchair_required`.

## Open Conflicts

- `BUSINESS_RULES.md` still contains earlier Open Questions, but the database design round and this round's instructions explicitly treat the five formerly open items as confirmed: reservation hold 15 minutes by Store policy, queue call hold 3 minutes by Store policy, expected dining 90 minutes by Store policy, active duplicate reservation blocked for same Tenant + Store + Customer + slot, and QueueGroup by Store + party-size bands. No blocking conflict remains for backend domain model design.

## Not Created In This Round

- No Java code.
- No Entity, Repository, Service, Controller, DTO, or Mapper.
- No API or UI.
- No migration or SQL.
- No database connection or migration execution.
- No seed, mock, or business data.
