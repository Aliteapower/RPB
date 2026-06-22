# Database Design V1

## Purpose

This document defines the V1 database design direction for the Reservation Platform. It is based on the previous governance documents and the current Product Owner confirmations for the formerly open rules.

This is a design document only. It does not create migrations, executable SQL, Java entities, repositories, services, controllers, APIs, Vue pages, tests, configuration, or mock data.

## Inputs Read

- docs/governance/BUSINESS_GLOSSARY.md
- docs/governance/BUSINESS_RULES.md
- docs/governance/DATA_STANDARD.md
- docs/governance/DATA_CHECKLIST.md
- docs/architecture/ARCHITECTURE.md
- docs/skills/reservation-system/SKILL_OVERVIEW.md
- docs/skills/reservation-system/SKILL.md

## Current Round Confirmed Rules

| Rule | Database Design Impact | Status |
|---|---|---|
| Reservation hold duration is Store configurable; V1 default is 15 minutes after reservation time. | Store policy data must support reservation hold minutes. | Confirmed |
| Queue call hold duration is Store configurable; V1 default is 3 minutes after call. | Store policy data must support queue call hold minutes. | Confirmed |
| Expected dining duration is Store configurable; V1 default is 90 minutes. Future extension may vary by business type, party size, table type, and time slot. | Store policy data must support expected dining minutes and leave extension space. | Confirmed |
| Same Tenant + Store + Customer + time slot cannot have multiple active confirmed / arrived / seated Reservations. | Reservation uniqueness must be scoped by Tenant, Store, Customer, time slot, and active statuses. | Confirmed |
| V1 Queue group defaults to Store + Party Size Group. Groups are 1-2, 3-4, 5-6, and 7+. | Queue group data must support Store-scoped party-size groups and ticket number uniqueness per group. | Confirmed |

## Data Model Overview

The data model separates platform configuration, tenant ownership, store configuration, customer identity, and store operations.

Core model layers:

```text
Platform setup
  -> Tenant setup
      -> Store setup and Store policies
          -> Area and Table resources
              -> Reservation / WalkIn / QueueTicket operations
                  -> Seating
                      -> Cleaning
                          -> Turnover
```

Core data principles:

- Reservation and QueueTicket remain independent tables.
- WalkIn is preserved as its own arrival scenario, so direct seating does not require a QueueTicket.
- CheckIn is persisted as an event and audited state transition, not as a primary business table.
- Seating is persisted separately as an occupancy record.
- Cleaning is persisted separately as a table-resource status flow.
- Turnover is persisted or derived as a result from Seating + completion + Cleaning.
- Customer is Tenant-scoped and supports no-phone and temporary customers.
- Store owns timezone, locale, date format, time format, currency, and operational policy defaults.

## Platform / Tenant / Store / Operation Data Boundaries

| Level | Example Tables | tenant_id | store_id | Rule |
|---|---|---:|---:|---|
| Platform | platform_roles, i18n_message_catalog where platform-owned | No | No | Global platform data. Do not force tenant/store scope. |
| Tenant | tenants, tenant_users, tenant_roles, customers | Yes where child of Tenant | No | Tenant is isolation boundary. |
| Store | stores, store_operation_policies, areas, dining_tables, queue_groups | Yes | Yes | Store configuration and resources. |
| Store Operations | reservations, queue_tickets, walk_ins, seatings, cleanings, turnovers, table_locks | Yes | Yes | Live operational data. |
| Cross-Store Shared | customers, tenant-level reason codes, tenant-level policy templates | Yes | Nullable or No | Shared only inside one Tenant. |
| Audit / Events | audit_logs, business_events, state_transition_logs, idempotency_records | Contextual | Nullable | Scope follows the related actor and object. |

## Core Table Recommendations

These are conceptual table recommendations, not executable schema.

| Table | Business Responsibility | Does Not Own | Scope Rule | Key References |
|---|---|---|---|---|
| tenants | Tenant lifecycle and Tenant-level ownership boundary. | Store operations, table resources, platform-global roles. | Platform-created; tenant_id is represented by its primary identity. | Platform configuration boundary. |
| stores | Physical or operational Store under one Tenant. Stores locale and operating identity. | Customer global identity outside Tenant, table occupancy. | Requires tenant_id; store identity is Store scope root. | Tenant. |
| store_operation_policies | Store-configurable operational defaults: reservation hold minutes, queue call hold minutes, expected dining minutes, queue sorting policy, table assignment defaults. | Individual Reservation, QueueTicket, Seating, or Cleaning records. | Requires tenant_id and store_id. | Store. |
| areas | Store zones such as hall, VIP, outdoor, bar, or floor. | Customer, queue number, reservation capacity. | Requires tenant_id and store_id. | Store. |
| dining_tables | Physical table resource with capacity, area, lifecycle, and current business status. | Customer identity, queue ordering, reservation ownership. | Requires tenant_id and store_id. | Store, Area. |
| table_groups | Fixed or temporary combined table resource. | Base Table definition, customer identity. | Requires tenant_id and store_id. | Store; member Tables through table_group_members. |
| table_group_members | Membership between TableGroup and Table. | TableGroup lifecycle or occupancy by itself. | Requires tenant_id and store_id. | TableGroup, dining_table. |
| customers | Tenant-scoped customer identity, including no-phone, anonymous, temporary, and walk-in guest identities. | Membership points, marketing, POS account. | Requires tenant_id; store_id is not required for shared customer identity. | Tenant; referenced by Reservation, QueueTicket, WalkIn. |
| reservations | Advance capacity reservation for Store + date + time slot + party size. | Queue number, calling, table occupancy by itself. | Requires tenant_id and store_id. | Store, Customer. |
| reservation_preassignments | Optional pre-assignment boundary from Reservation to Table or TableGroup before final Seating. | Final occupancy. | Requires tenant_id and store_id. | Reservation, Table or TableGroup. |
| queue_groups | Store-scoped queue grouping policy. V1 defaults to party-size groups: 1-2, 3-4, 5-6, 7+. | Individual QueueTicket status. | Requires tenant_id and store_id. | Store. |
| queue_tickets | Waiting record and queue number after arrival when no table resource is available. | Advance reservation capacity, direct WalkIn seating. | Requires tenant_id and store_id. | Store, Customer, optional Reservation or WalkIn source, QueueGroup. |
| walk_ins | Store arrival scenario without prior Reservation. Can go direct to Seating or QueueTicket. | Advance booking, queue number by default. | Requires tenant_id and store_id. | Store, Customer. |
| seatings | Formal seating event and active or historical occupancy record. | Reservation creation, QueueTicket creation, Cleaning completion. | Requires tenant_id and store_id. | Exactly one source: Reservation, QueueTicket, or WalkIn. |
| seating_resources | Assigned Table or TableGroup resources for one Seating. Supports single table, fixed group, or temporary group. | Source flow status by itself. | Requires tenant_id and store_id. | Seating, Table or TableGroup. |
| cleanings | Cleaning process after Seating completion or guest departure. | Turnover metric by itself, payment, marketing. | Requires tenant_id and store_id. | Seating, Table or TableGroup. |
| turnovers | Result or metric for one table-use cycle derived from Seating + completion + Cleaning. | Live seating action, cleaning action. | Requires tenant_id and store_id. | Seating, Cleaning. |
| table_locks | Durable record of table or TableGroup lock intent and release/expiry boundary. Redis may own live lock speed; database preserves auditable lock state. | Final occupancy or Reservation itself. | Requires tenant_id and store_id. | Table or TableGroup; optional Reservation, QueueTicket, WalkIn, Seating. |
| business_events | Domain event records such as CheckIn, queue call, skipped, rejoined, manual override, table release. | Primary entity responsibilities. | Scope follows event target; tenant_id required for tenant/store events; store_id nullable for non-store events. | Related object by event type and target identity. |
| state_transition_logs | Reusable state transition history for Reservation, QueueTicket, Table, TableGroup, Seating, Cleaning, and other stateful objects. | Current state storage by itself. | Scope follows target object. | Target object, actor, audit log. |
| audit_logs | Mandatory audit trail for critical operations, overrides, permission changes, and integration calls. | Business state ownership. | Scope follows actor and target; store_id nullable where not Store-scoped. | Actor, related object, idempotency record. |
| idempotency_records | Deduplicates critical operations and third-party calls. | Business result by itself. | tenant_id required when tenant-scoped; store_id required when Store-scoped. | Actor/source/action/target. |
| reason_codes | Configurable cancellation, no_show, skip, override, and operational reason codes. | Business transition execution. | Tenant-level by default; store_id nullable for Store override. | Tenant, optional Store. |
| i18n_message_catalog | Platform or Tenant-owned message key catalog and optional overrides for configurable domain text. | UI rendering implementation. | Platform or Tenant scope; Store override is optional future boundary. | i18n key owner scope. |

## Tables Intentionally Not Recommended as Primary Tables

| Not Primary Table | Reason |
|---|---|
| check_ins | CheckIn is a V1 business event, not a primary entity. It should be represented by business_events, audit_logs, and Reservation state transition from confirmed to arrived. |
| members | Customer is not Member. Membership and loyalty are future integration boundaries. |
| payments | Payment is outside V1. |
| pos_orders | POS is a future integration boundary. |
| marketing_campaigns | Marketing is outside V1. |

## tenant_id / store_id Usage Rules

- tenant_id is mandatory for Tenant-owned data and all Store operations.
- store_id is mandatory only for Store configuration, Store resources, and Store operations.
- Platform-owned tables do not require tenant_id or store_id.
- Customer uses tenant_id and does not require store_id because it is shared across Stores within one Tenant.
- Audit and event tables use tenant_id and store_id according to the related target object.
- No table may rely on store_id as a substitute for tenant_id when Tenant isolation matters.
- Cross-Tenant references are forbidden.

## Open Questions and Conflicts for Database Design

Open Questions:

- None for this database design round. The five previously open governance questions are treated as Confirmed by the current Product Owner instructions.

Open Conflicts:

- No database-design conflict was found in the existing project files. The earlier governance note about the local bootstrap file including OOD & Reuse Governance remains historical context and does not conflict with this round's allowed database documentation scope.

## Primary Key Strategy

- Use stable surrogate primary identities for all primary business tables.
- Business codes such as reservation code, queue ticket number, and table label are not primary keys; they are scoped business identifiers.
- Primary identities must not encode tenant, store, status, or business meaning.
- Human-facing codes must be unique only within the relevant business scope.
- Foreign references should use stable primary identities and validate Tenant/Store scope consistency.

## Uniqueness Strategy

| Business Area | Logical Unique Boundary |
|---|---|
| Tenant | Platform-unique Tenant identity and tenant code where configured. |
| Store | Tenant + Store code or label. |
| Area | Tenant + Store + Area label among active Areas. |
| Table | Tenant + Store + Table label among active Tables. |
| Customer | Tenant-scoped customer identity. Phone is optional; when present, normalized E.164 phone uniqueness is Tenant-scoped according to later merge policy. |
| Reservation active duplicate | Tenant + Store + Customer + time slot must not have multiple active confirmed / arrived / seated Reservations. |
| Reservation capacity | Tenant + Store + date + time slot + party-size capacity bucket governed by Store policy. |
| Queue group | Tenant + Store + party-size group code among active groups. |
| Queue ticket number | Tenant + Store + queue operating day/window + QueueGroup + ticket number. |
| Table occupancy | Same Table or effective TableGroup cannot have overlapping active Seating. |
| Temporary TableGroup | Same Table cannot appear in more than one active temporary group or active occupancy at the same time. |
| Idempotency | source + tenant scope + store scope where applicable + action + idempotency key. |

## Foreign Key and Reference Strategy

- Strong ownership references should be enforceable in later schema design: Tenant -> Store -> Area -> Table.
- Store operational records must reference Store and Tenant consistently.
- Reservation references Customer and Store.
- Reservation must not reference QueueTicket as a required child.
- QueueTicket may optionally reference Reservation or WalkIn as source, but only when the customer must wait.
- WalkIn may exist without QueueTicket.
- Seating must reference exactly one source among Reservation, QueueTicket, or WalkIn.
- Seating resources must reference either Table or TableGroup according to the assigned resource type.
- Cleaning must reference Seating and the cleaned Table or TableGroup.
- Turnover must reference Seating and Cleaning source data.
- Business event and audit references may use a generic target identity pattern, but target scope must be validated.
- TableGroup membership must ensure all member Tables are in the same Tenant and Store as the TableGroup.

## State Field Strategy

Stateful tables should store compact status codes and rely on state-transition governance rather than free text.

Required state machines:

- Reservation: draft, confirmed, arrived, seated, completed, cancelled, no_show.
- QueueTicket: waiting, called, skipped, rejoined, seated, cancelled, expired.
- Table: available, locked, reserved, occupied, cleaning, inactive.
- Fixed TableGroup: created, active, inactive, deleted.
- Temporary TableGroup: created, locked, occupied, released, ended.
- Cleaning: pending, cleaning, completed, released.
- Seating: planned/locked, occupied, completed, cleaning-triggered.

Status display text must use i18n keys, not hardcoded display strings.

## Soft Delete Strategy

- Soft delete is the default for business and configuration history.
- Key business history must not be physically deleted.
- Soft-deleted configuration cannot be used by new operational flows.
- Historical operations may still reference soft-deleted Store, Table, Customer, or TableGroup data.
- Soft deletion must capture actor, time, reason, source, and scope in audit.
- Active locks, active seating, active cleaning, and active temporary TableGroups must be resolved before deactivation or deletion is allowed.

## Audit Field and Audit Table Strategy

Business tables should carry minimal audit metadata for ownership and recency. audit_logs is the durable audit authority for critical operations.

Audit must cover:

- Reservation create, confirm, cancel, no_show.
- CheckIn event.
- Queue number create, call, skip, rejoin.
- Seating and table change.
- TableGroup create, use, release.
- Cleaning start and completion.
- Table release.
- Manual override.
- Critical configuration and permission changes.
- Third-party calls.

audit_logs must capture actor, actor role, tenant scope, store scope, operation time, source, before state, after state, idempotency key, related object, and failure reason when applicable.

## Idempotency Strategy

Use idempotency_records as the reusable data boundary for critical repeatable actions.

Actions requiring idempotency:

- Reservation creation and confirmation.
- CheckIn.
- QueueTicket creation.
- Queue call.
- Queue rejoin.
- Seating.
- Table lock acquisition and release.
- Cleaning completion.
- Third-party integration calls.
- Future webhook delivery and retry.

Idempotency records should store request identity, scope, source, action, target, request fingerprint, result pointer, status, and expiry boundary in later detailed schema design.

## Time Field Strategy

- All instants are stored in UTC and exchanged as ISO8601.
- Store-local business date may be stored as a semantic date for reservation day, queue operating day, and reporting grouping, derived from Store timezone.
- Reservation start and end instants must be UTC.
- Queue call, skip, rejoin, seating, cleaning, and turnover timestamps must be UTC.
- Store timezone decides display and business-day grouping.
- V1 defaults: reservation hold is 15 minutes, queue call hold is 3 minutes, expected dining duration is 90 minutes.

## Internationalization Field Strategy

- stores must carry timezone, locale, date format, time format, and currency configuration.
- Singapore default: Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD.
- User-facing statuses, reason codes, and configurable labels should store stable codes and i18n keys.
- Free-form customer notes and staff notes may remain user-entered text; system-generated text must use i18n keys.
- Phone numbers, when present, must be normalized to E.164.
- Phone number remains optional for Customer.

## Concurrency and Lock Strategy

Concurrency-sensitive data:

- Reservation capacity and same-customer active reservation uniqueness.
- Table lock and final occupancy.
- Temporary TableGroup creation, membership, and release.
- Queue call and rejoin ordering.
- Seating resource assignment.
- Cleaning completion and table release.
- Idempotent third-party calls.

Design stance:

- Redis may protect fast live locks and queue synchronization.
- PostgreSQL should preserve durable records, final conflict boundaries, audit, and history.
- table_locks records lock owner, target resource, expiry, release, and source flow.
- seatings and seating_resources preserve final occupancy.
- State transition logs preserve legal transition trace.

This document does not define exact locking algorithms, Redis keys, transaction isolation levels, or executable constraints.

## OOD and Reuse Data Boundaries

The model reserves data boundaries for reusable domain capabilities:

| Reusable Capability | Data Boundary |
|---|---|
| State machine transition | status fields + state_transition_logs + audit_logs. |
| Multi-tenant isolation | tenant_id scope and cross-scope reference validation. |
| Store timezone and locale | stores and store_operation_policies. |
| i18n config | stable codes, i18n keys, optional i18n_message_catalog. |
| E.164 phone validation | customers normalized phone boundary. |
| Idempotency key | idempotency_records. |
| Audit log | audit_logs. |
| Soft delete | lifecycle status plus deletion audit metadata. |
| Table lock | table_locks and seating_resources. |
| Queue sorting | queue_groups, queue_tickets, and store_operation_policies. |
| Reservation time conflict | reservations and Store policy time windows. |
| Table availability | dining_tables, table_locks, seating_resources, cleanings. |
| TableGroup validity | table_groups and table_group_members. |

## Not Entering Implementation

This round does not create:

- Flyway Migration.
- SQL file.
- Java Entity.
- Repository.
- Service.
- Controller.
- Vue page or component.
- API.
- Test code.
- Mock data.
- Configuration file.
