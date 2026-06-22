# Data Standard

## Purpose

This document defines data governance standards for later database, API, and implementation rounds. It intentionally avoids concrete DDL, migration files, entity classes, table fields, API paths, or executable schema.

## Data Level Standard

| Data Level | tenant_id | store_id | Examples | Rule |
|---|---:|---:|---|---|
| Platform | No | No | System roles, global configuration, industry templates | Must not be forced into Tenant or Store scope. |
| Tenant | Yes | No | Brand configuration, tenant permissions, customer identity, tenant rules | Isolated by Tenant. |
| Store | Yes | Yes | Store, Area, Table, business hours, Store locale | Belongs to one Store under one Tenant. |
| Store Operations | Yes | Yes | Reservation, QueueTicket, Seating, Cleaning | Must resolve to one Store. |
| Cross-Store Shared | Yes | Nullable | Customer, member boundary, brand-level rules | Shared only within Tenant. |
| Audit | Contextual | Nullable | Platform, Tenant, Store, and integration audit | Scope must match the audited object and actor. |

Rules:

- Do not require every future data object to have both tenant_id and store_id.
- tenant_id follows tenant isolation.
- store_id follows physical or operational Store ownership.
- Cross-Tenant references are forbidden.
- Store operational records must not reference resources from another Store.

## Uniqueness Rules

| Boundary | Standard |
|---|---|
| Platform unique | Applies only to platform-level identifiers and global templates. |
| Tenant unique | Customer identity and tenant-level configuration identifiers are unique within Tenant. |
| Store unique | Area names, Table labels, Store-local operation identifiers, and Store queue settings are unique within Store where applicable. |
| Tenant + Store unique | Store operational records must be unique within both Tenant and Store scope when local numbering or labels are used. |
| Time-slot unique | Reservation capacity and conflict checks are scoped by Store, date, time slot, party size/capacity, and policy. |
| Table resource occupancy unique | One Table or effective TableGroup cannot be actively occupied by multiple valid Seating flows at the same time. |
| Customer duplicate reservation | Same-customer overlapping Reservation policy is Open and must be resolved before database design. |
| Queue group number unique | QueueTicket number is unique within the relevant queue group and queue operating window. |

## Integrity Rules

- Required fields must be driven by business lifecycle, not convenience.
- Nullable fields must represent real optionality, such as no-phone Customer or nullable store scope for Tenant-level data.
- Temporary Customer records must include enough scenario data to support lookup without phone number.
- Reservation core completeness requires Store, customer or temporary customer context, date, time slot, party size, status, source, and audit context in later design.
- QueueTicket core completeness requires Store, queue group, ticket number, party size, status, source, customer or temporary customer context, and audit context in later design.
- Seating core completeness requires Store, Table or TableGroup assignment, source flow, occupancy status, actor, and audit context in later design.
- Table status completeness requires a valid state and valid transition history for operational changes.
- Store operational data must always be attributable to one Tenant and one Store.

## Reference Rules

Confirmed reference direction:

```text
Platform -> Tenant
Tenant -> Store
Store -> Area
Area -> Table
Store -> Reservation
Store -> QueueTicket
Reservation -> Customer
QueueTicket -> Customer
Seating -> Table / TableGroup
Seating -> Reservation or QueueTicket or WalkIn
Cleaning -> Table / TableGroup
Turnover -> Seating / Cleaning
```

Reference constraints:

- No object may reference another Tenant's private data.
- Store operational data may not reference a Table, Area, Reservation, QueueTicket, Seating, or Cleaning record from another Store.
- TableGroup may reference only member Tables in the same Store.
- TableGroup must prevent circular references.
- Temporary TableGroup must not be referenced by new flows after release or end.
- Soft-deleted, inactive, archived, cancelled, expired, or terminal resources must not be used by new business flows unless the later design defines a specific read-only or historical reference rule.

## Deletion Rules

Default deletion standard is soft deletion or lifecycle status, not physical deletion.

| Business Object | Deletion Standard |
|---|---|
| Tenant | Suspend or close; physical deletion is not allowed for active historical data. |
| Store | Inactivate or archive; operational history remains. |
| Customer | Archive or merge; respect Tenant boundary and audit. |
| Table | Inactivate or soft delete only after no active occupancy. |
| TableGroup | Fixed groups can be inactive/deleted by lifecycle; temporary groups must release and end. |
| Reservation | Cancel, no_show, complete, or archive; no physical deletion for business history. |
| QueueTicket | Cancel, expire, seat, or archive; no physical deletion for business history. |
| Seating | Completed or archived; no physical deletion for occupancy history. |
| Cleaning | Completed or archived; no physical deletion for operational history. |
| Turnover | Derived or recorded result; keep historical integrity. |

Deletion categories:

- Can delete: drafts or unused configuration only when later design allows and no references exist.
- Can deactivate: Tenant, Store, Area, Table, Fixed TableGroup, users, roles.
- Can cancel: Reservation and QueueTicket.
- Can archive: old operational records after retention policy.
- Cannot physically delete: key business history, audit logs, active operational records.

## State Machine Data Rules

Each state machine must define:

- Initial state.
- Legal transitions.
- Illegal transitions.
- Trigger actor or system source.
- Preconditions.
- Business impact after transition.
- Audit requirement.
- Idempotency requirement.
- Concurrency requirement.

State transition data must not be scattered across controllers, pages, or ad hoc service methods in later implementation. Later design should centralize state transition rules.

## Audit Rules

Operations that must be audited:

- Reservation creation, confirmation, cancellation, no_show.
- Customer CheckIn.
- Queue number creation.
- Queue call, skipped, rejoin.
- Seating.
- Table change.
- TableGroup creation, use, release.
- Cleaning start and completion.
- Table release.
- Manual override of system recommendation.
- Critical configuration change.
- Permission change.
- Third-party call.

Audit record must capture:

- Actor.
- Actor role.
- tenant scope.
- store scope.
- Operation time.
- Before state.
- After state.
- Operation source.
- Idempotency key.
- Related business object.
- Failure reason when applicable.

## Internationalization Data Rules

- Store all timestamps in UTC.
- Use ISO8601 format at system boundaries.
- Display time by Store timezone and locale.
- Store must define timezone, locale, date_format, time_format, and currency.
- Singapore default is Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD.
- Text shown to users must be referenced by i18n key in later UI/API design.
- Phone numbers, when present, must follow E.164.
- Phone number is not required for Customer.

## Idempotency Rules

Idempotency is required for:

- External integration requests.
- Reservation creation and confirmation.
- CheckIn.
- QueueTicket creation.
- Calling a QueueTicket.
- Rejoin after skipped.
- Seating.
- Table lock acquisition and release.
- Cleaning completion.
- Webhook delivery and retry in later integration design.

Idempotency keys must be scoped to actor/source, tenant, store when applicable, business action, and request intent. Exact storage and expiry are deferred to later database and architecture detail.

## Concurrency Rules

Concurrency control is mandatory for:

- Table locks.
- Table occupancy.
- Temporary TableGroup creation and release.
- Reservation capacity checks.
- Queue call ordering.
- Queue rejoin placement.
- Seating assignment.
- Cleaning completion and resource release.

Future technical design may use PostgreSQL transactions, Redis locks, optimistic versioning, or a combination, but this governance round does not prescribe executable implementation.

## Soft Delete Rules

- Soft-deleted resources must be excluded from new operational actions by default.
- Historical records may continue to reference soft-deleted resources for audit and reporting.
- Soft deletion must record actor, time, reason, scope, and source.
- Soft deletion cannot hide active occupancy, unreleased locks, or pending temporary TableGroups.
- Restore rules are Open and must be addressed before implementation if restore is required.
