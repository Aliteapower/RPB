# Rule Policy Validator Design V1

## Purpose

This document defines reusable backend Rule, Policy, and Validator boundaries for the Reservation Platform. It prepares the next Java implementation round without creating Java code.

Rule / Policy / Validator components must be reusable domain capabilities, not page helpers, Controller logic, or one-off Service methods.

## Component Types

| Type | Meaning | Typical Output |
|---|---|---|
| Rule | Determines whether a business condition is valid for a specific decision. | `accepted`, `violation_code`, optional facts. |
| Policy | Resolves configured behavior such as duration, ordering, expiry, locale, or cancellation rule. | Policy value or policy decision. |
| Validator | Checks structural integrity and domain boundaries before mutation. | Validation result with violation codes. |

Failures should return stable violation codes and structured details. They should not return display text. Display text belongs to i18n resolution in later API/UI rounds.

## Scope Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| TenantScope | Value boundary | Carries Tenant identity and prevents cross-Tenant ambiguity. | `tenant_id`, actor context. | Valid Tenant scope or `tenant_scope_missing`. | All Tenant-owned objects. | Store authorization or business validation. | No audit by itself; used by audited flows. |
| StoreScope | Value boundary | Carries Tenant + Store identity for Store operations. | `tenant_id`, `store_id`, actor context. | Valid Store scope or `store_scope_missing`. | Store, tables, reservations, queues, seatings, cleanings. | Table availability or queue ordering. | No audit by itself; used by audited flows. |
| ScopeGuard | Validator | Ensures referenced objects share the required Tenant/Store scope. | Source object scope, target object scope, target type. | Pass or `cross_tenant_reference` / `cross_store_reference`. | All cross-object operations. | Loading data or authorization roles. | Failure should be auditable for critical commands; no transition. |
| TenantAccessPolicy | Policy | Determines whether actor can act inside Tenant scope. | Actor, role, tenant scope, source. | Access decision or `tenant_access_denied`. | Tenant management and all Tenant-scoped commands. | Business correctness. | Failed critical command should audit; no transition. |
| StoreAccessPolicy | Policy | Determines whether actor can act inside Store scope. | Actor, role, store scope, source. | Access decision or `store_access_denied`. | Store operation commands. | Table availability or queue decisions. | Failed critical command should audit; no transition. |

## Reservation Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| ReservationAvailabilityRule | Rule | Detects whether Store has capacity for requested time and party size. | StoreScope, business date, start/end instants, party size, StorePolicy, active reservations, table capacity facts. | Availability decision or `reservation_capacity_unavailable`. | Reservation create/confirm. | Customer duplicate rules or final table assignment. | Audit on command; no transition unless status changes; idempotency required for create/confirm. |
| ReservationDuplicateRule | Rule | Blocks same Tenant + Store + Customer + time slot from multiple active confirmed/arrived/seated Reservations. | StoreScope, Customer id, reserved start/end, active statuses. | Pass or `duplicate_active_reservation_slot`. | Reservation create/confirm/reschedule later. | Anonymous customer identity merge. | Audit on failure for critical command; no transition by itself; idempotency recommended. |
| ReservationHoldPolicy | Policy | Computes Reservation hold window. V1 default: reservation time plus 15 minutes, Store configurable. | StorePolicy, reserved start, source. | `hold_until_at` or `reservation_hold_policy_missing`. | Reservation confirm/create. | Queue call hold or expected dining duration. | No audit by itself; command audit required; idempotency through command. |
| ReservationCancellationPolicy | Policy | Determines whether Reservation can be cancelled and which reason is required. | Reservation status, actor/source, current time, reason code. | Cancellation decision or `reservation_cancellation_not_allowed`. | Reservation cancellation. | Queue cancellation or no-show. | Audit and StateTransitionLog required; idempotency required. |
| NoShowPolicy | Policy | Determines when confirmed/arrived Reservation can become `no_show`. | Reservation, StorePolicy, current time, actor/source, reason. | No-show decision or `reservation_no_show_not_allowed`. | Reservation no_show transition. | Cleaning/turnover. | Audit and StateTransitionLog required; idempotency required. |

## Queue Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| QueueGroupPolicy | Policy | Selects QueueGroup for party size. V1 default groups: 1-2, 3-4, 5-6, 7+. | StoreScope, party size, active QueueGroups. | QueueGroup or `queue_group_not_found`. | QueueTicket create. | Queue ordering or table assignment. | Command audit required; no transition by itself. |
| QueueCallingRule | Rule | Validates calling a waiting ticket and call hold behavior. | QueueTicket, StorePolicy, current time, actor/source. | Call decision or `queue_ticket_not_callable`. | Queue call. | Rejoin placement or table assignment. | Audit and StateTransitionLog required; idempotency required. |
| QueueRejoinRule | Rule | Validates skipped ticket rejoin and preserves original number. | QueueTicket, StorePolicy, current queue facts. | Rejoin decision or `queue_ticket_not_rejoinable`. | Queue rejoin. | New ticket number generation. | Audit and StateTransitionLog required; idempotency required. |
| QueueExpiryPolicy | Policy | Determines when QueueTicket expires. | QueueTicket status/timestamps, StorePolicy, current time. | Expiry decision or no-op. | Queue expiry job or command. | No-show policy. | Audit and StateTransitionLog when expiring; idempotency recommended. |
| QueueOrderingPolicy | Policy | Computes ticket number and mutable queue position. Default rejoin goes to tail of same group. | StoreScope, QueueGroup, business date, current queue, rejoin policy. | Number/position or `queue_ordering_conflict`. | QueueTicket create, rejoin, manual reorder later. | Table availability. | Audit required for create/reorder; idempotency required for create/rejoin. |

## Table and Seating Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| TableAvailabilityRule | Rule | Determines if Table or TableGroup can be used now. | StoreScope, resource type/id, DiningTable/TableGroup status, active locks, active seating, active cleaning, active group facts. | Available decision or `table_resource_unavailable`. | Reservation preassignment, Seating, table recommendations later. | Queue order or customer identity. | Command audit; no transition by itself. |
| TableLockRule | Rule | Acquires/releases durable table or group lock and guards concurrent assignment. | StoreScope, resource target, lock owner, source type/id, lock expiry, idempotency key. | Lock decision or `table_lock_conflict`. | Seating, preassignment, table assignment. | Final occupancy. | Audit required; StateTransitionLog recommended for TableLock; idempotency required. |
| TableAssignmentRule | Rule | Selects valid table/group candidate for party size and flow. | Party size, StorePolicy, active tables/groups, availability facts, area preference, preassignment facts. | Assignment candidate or `no_assignable_table`. | Seating and future recommendation. | Queue number or customer identity. | Audit on manual override; no transition until Seating/Table state changes; idempotency through Seating command. |
| TableCapacityRule | Rule | Validates party size against `capacity_min`/`capacity_max`. | Party size, DiningTable or TableGroup capacity range. | Pass or `party_size_outside_capacity`. | Reservation availability, queue matching, Seating assignment. | Expected dining duration. | No audit by itself; command audit if failure affects critical command. |
| SeatingSourceValidator | Validator | Enforces exactly one Seating source among Reservation, QueueTicket, WalkIn. | Reservation id, QueueTicket id, WalkIn id, source statuses, StoreScope. | Pass or `invalid_seating_source`. | Seating create/occupy. | Resource validation. | Failed command audit; StateTransitionLog only on accepted transition; idempotency required. |
| SeatingResourceValidator | Validator | Enforces exactly one resource target per SeatingResource and matching Store scope. | Seating, resource type, table id, table group id, resource status. | Pass or `invalid_seating_resource`. | SeatingResource assignment. | Seating source validation. | Failed command audit; StateTransitionLog on accepted resource/state change; idempotency required. |

## TableGroup Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| TableGroupValidationRule | Rule | Validates fixed/temporary group member consistency, status, active use, and no nested groups in V1. | TableGroup, TableGroupMembers, DiningTable statuses, active locks/occupancy/cleaning. | Valid group or `invalid_table_group`. | Group creation, activation, temporary lock/occupy/release. | Queue ordering or customer identity. | Audit required; StateTransitionLog for group status; idempotency required for temporary group operations. |
| FixedTableGroupPolicy | Policy | Governs long-term fixed group lifecycle and recommendation eligibility. | TableGroup type/status, members, StoreScope. | Fixed group decision or `fixed_group_not_usable`. | Fixed group activate/deactivate/recommend. | Temporary single-service release. | Audit and StateTransitionLog required for status changes. |
| TemporaryTableGroupPolicy | Policy | Governs single-service temporary group creation, use, release, and end. | Candidate tables, Seating, lock facts, cleaning facts. | Temporary group decision or `temporary_group_not_usable`. | Seating large parties, temporary combined tables. | Fixed configuration lifecycle. | Audit and StateTransitionLog required; idempotency required. |
| TableGroupMemberRule | Rule | Validates adding/removing member tables. | TableGroup, DiningTable, existing members, StoreScope. | Pass or `invalid_table_group_member`. | Group member changes. | Group status transition by itself. | Audit required; StateTransitionLog if group status changes; idempotency recommended. |

## Customer Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| CustomerIdentityRule | Rule | Determines whether Customer identity is sufficient for Reservation, WalkIn, or QueueTicket. | TenantScope, customer type, code, phone, display/nickname/lookup note. | Identity decision or `customer_identity_insufficient`. | Reservation, WalkIn, QueueTicket. | Member/loyalty. | Audit for customer create/merge/archive; idempotency for create/update commands. |
| CustomerPhoneRule | Rule | Validates nullable E.164 phone and phone uniqueness when present. | TenantScope, phone value, existing active phone facts. | Pass or `invalid_phone_e164` / `customer_phone_duplicate`. | Customer create/update/search. | No-phone lookup policy. | Audit for changes; no transition except Customer status changes; idempotency recommended. |
| AnonymousCustomerPolicy | Policy | Defines allowed anonymous/temporary customer usage. | Scenario, StoreScope, customer type, lookup note. | Policy decision or `anonymous_customer_not_allowed`. | WalkIn, no-phone Reservation, QueueTicket. | Customer merge. | Audit on create/update; idempotency recommended. |
| TenantCustomerUniquenessRule | Rule | Enforces Customer uniqueness inside Tenant, not Store or Platform. | TenantScope, customer code, phone when present, merge target. | Pass or `customer_duplicate_in_tenant`. | Customer create/merge/update. | Membership identity. | Audit required for merge/archive; idempotency recommended. |

## I18n and Time Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| StoreLocaleRule | Rule | Resolves Store locale, date/time format, and display preferences. | Store, optional Tenant default. | Locale settings or `store_locale_missing`. | API/UI representation later, reason/status display. | Business state decisions. | No audit unless config changes. |
| StoreTimeZoneRule | Rule | Resolves Store timezone and Store-local business date. | Store timezone, UTC instant. | Local date/time view or `store_timezone_missing`. | Reservation business date, queue operating day, turnover reporting. | Database storage timezone conversion side effects. | No audit unless config changes. |
| DateTimePolicy | Policy | Enforces UTC instants, ISO8601 exchange, and Store-local date derivation. | Input instant/date, Store timezone, source. | Normalized time values or `invalid_datetime`. | Reservation, QueueTicket, Seating, Cleaning, Turnover. | Locale message text. | Command audit if invalid input rejects critical command. |
| I18nMessageRule | Rule | Resolves stable i18n key by Platform/Tenant/Store scope and locale. | Scope, i18n key, locale. | Message record or `i18n_message_missing`. | ReasonCode display, status display, future API/UI messages. | Business rule decisions. | No transition; audit config changes. |
| CurrencyPolicy | Policy | Resolves Store currency for future display and integration boundaries. | Store currency, Tenant defaults. | Currency code or `store_currency_missing`. | Store configuration and future reporting display. | Payment implementation. | No transition; audit config changes. |

## Audit and Idempotency Components

| Component | Type | Solves | Inputs | Output / Failure | Applies To | Not Responsible For | Audit / Transition / Idempotency |
|---|---|---|---|---|---|---|---|
| AuditRule | Rule | Determines whether operation must create AuditLog and what snapshot is required. | Operation code, actor, scope, target, before/after state, reason, failure. | Audit decision or `audit_required_missing`. | All critical flows. | Business transition legality. | Core audit component; no transition by itself; idempotency key captured when present. |
| BusinessEventRule | Rule | Determines required BusinessEvent for domain events such as CheckIn, queue call, skip, rejoin, table release. | Event type, target, actor/source, metadata, scope. | Event decision or `business_event_invalid`. | CheckIn and operational events. | Current state storage. | Often paired with AuditLog and StateTransitionLog; idempotency recommended for critical events. |
| StateTransitionRule | Rule | Validates legal state movement and required transition log. | Target type, from/to status, trigger, actor/source, preconditions. | Transition decision or `illegal_state_transition`. | Reservation, QueueTicket, DiningTable, TableGroup, Seating, Cleaning, Turnover, TableLock. | Persistence by itself. | StateTransitionLog required for accepted critical transitions; audit required; idempotency depends on command. |
| IdempotencyRule | Rule | Deduplicates critical commands and replay behavior. | Scope, source, action, idempotency key, request hash, existing record. | Start/replay/reject decision or `idempotency_conflict`. | Reservation create/confirm, CheckIn, QueueTicket create/call/rejoin, Seating, TableLock, Cleaning complete, integrations. | Business correctness after unique command accepted. | Core idempotency component; audit captures key; no StateTransitionLog by itself. |

## Key Domain Collaboration Flows

### Reservation Create

Required collaboration:

```text
TenantScope
-> StoreScope
-> StorePolicy
-> ReservationHoldPolicy
-> ReservationAvailabilityRule
-> ReservationDuplicateRule
-> IdempotencyRule
-> AuditRule
```

Outcome: create/confirm Reservation command decision. No QueueTicket is created by default.

### Customer Arrive / CheckIn

Required collaboration:

```text
Reservation StateMachine
-> BusinessEventRule
-> StateTransitionRule
-> AuditRule
-> IdempotencyRule
```

Outcome: Reservation `confirmed -> arrived`, with CheckIn represented by BusinessEvent, StateTransitionLog, and AuditLog. No CheckInEntity.

### Queue Ticket Create

Required collaboration:

```text
QueueGroupPolicy
-> QueueOrderingPolicy
-> CustomerIdentityRule
-> IdempotencyRule
-> AuditRule
```

Outcome: QueueTicket in `waiting` state with party-size group and queue number. Reservation and WalkIn remain separate sources.

### Queue Call / Skip / Rejoin

Required collaboration:

```text
QueueTicket StateMachine
-> QueueCallingRule
-> QueueRejoinRule
-> QueueExpiryPolicy
-> StateTransitionRule
-> AuditRule
-> IdempotencyRule
```

Outcome: stable queue state movement; rejoin keeps original number and defaults to tail placement.

### Seating

Required collaboration:

```text
SeatingSourceValidator
-> TableAvailabilityRule
-> TableAssignmentRule
-> TableCapacityRule
-> TableLockRule
-> SeatingResourceValidator
-> StateTransitionRule
-> AuditRule
-> IdempotencyRule
```

Outcome: Seating creates occupancy through SeatingResource. Reservation/QueueTicket/WalkIn state updates happen through their own state machines.

### Cleaning Complete

Required collaboration:

```text
Cleaning StateMachine
-> DiningTable StateMachine
-> TableGroupValidationRule
-> Turnover recording rule
-> StateTransitionRule
-> AuditRule
-> IdempotencyRule
```

Outcome: Cleaning completes, resource is released or remains inactive by policy, and Turnover can be recorded.

## Package Structure Recommendation

Conceptual Java package boundaries for a later implementation round:

```text
reservation/
queue/
walkin/
table/
table/group/
table/lock/
seating/
cleaning/
turnover/
customer/
store/
tenant/
audit/
idempotency/
i18n/
common/scope/
common/state/
common/rule/
common/time/
common/result/
```

Do not design packages as:

```text
page/
screen/
admin/
mobile/
controller-centric/
```

## Command / Query Boundary

Future implementation should keep this flow:

```text
Controller
-> Command / Query
-> Application Service
-> Domain Rule / Policy / Validator
-> Repository
-> Persistence Entity
```

Rules:

- Command is write intent.
- Query is read condition.
- Domain Object owns business meaning.
- Persistence Entity maps storage.
- DTO expresses API contract.
- API DTO must not be treated as Domain Object.
- Entity must not be treated as the full Domain Object.

## Not Created In This Round

- No Java rule, policy, validator, state machine, entity, repository, service, controller, DTO, mapper, or test.
- No API/UI.
- No migration/SQL.
- No database connection or business data.
