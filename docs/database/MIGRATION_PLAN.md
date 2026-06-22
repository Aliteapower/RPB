# Migration Plan V1

## Purpose

This document plans the future Flyway migration sequence for the Reservation Platform schema. It is not a migration file and does not contain executable SQL. No database connection or migration execution is part of this round.

## Migration Scope

Future migrations should create the schema in dependency order so Tenant and Store scope exist before Store resources, operations, audit, and idempotency records.

This plan covers:

- Table build order.
- Status/check constraint strategy.
- Foreign key order.
- Index order.
- Soft delete and audit strategy.
- Idempotency table order.
- i18n message catalog order.
- State machine log order.
- Rollback risk notes.

## Recommended Table Creation Order

### Phase 1: Tenant and Store Roots

1. `tenants`
2. `stores`
3. `store_policies`
4. `reason_codes`
5. `i18n_message_catalog`

Reason:

- Tenant and Store scope must exist first.
- Store locale and policies are foundational for time display, reservation hold, queue call hold, and expected dining duration.
- Reason codes and i18n keys support status/reason text without hardcoded display strings.

### Phase 2: Store Resources

1. `store_areas`
2. `dining_tables`
3. `table_groups`
4. `table_group_members`
5. `table_locks`

Reason:

- Areas and Tables are needed before TableGroup membership.
- Table locks depend on resource tables.
- TableGroup validation depends on membership and same Store/Tenant scope.

### Phase 3: Customer and Arrival Flow

1. `customers`
2. `reservations`
3. `reservation_preassignments`
4. `queue_groups`
5. `walk_ins`
6. `queue_tickets`

Reason:

- Customer is Tenant-scoped and referenced by Reservation, WalkIn, and QueueTicket.
- Reservation preassignment depends on Reservation and resources.
- QueueTicket depends on QueueGroup and optional Reservation/WalkIn source.

### Phase 4: Seating, Cleaning, Turnover

1. `seatings`
2. `seating_resources`
3. `cleanings`
4. `turnovers`

Reason:

- Seating is the occupancy root.
- SeatingResources depend on Seating and table resources.
- Cleaning depends on Seating and resource.
- Turnover depends on Seating and Cleaning output.

### Phase 5: Governance, Events, Audit, Idempotency

1. `idempotency_records`
2. `audit_logs`
3. `business_events`
4. `state_transition_logs`

Reason:

- Idempotency is needed by critical future operations.
- Audit and events refer to many targets and may use generic target references.
- State transition logs preserve legal state movement for stateful objects.

Alternative:

- Create idempotency and audit earlier if future migration scripts need to backfill operational audit from the start. For V1, either placement is acceptable if no data migration runs before audit exists.

## Status and Check Constraint Strategy

Use explicit check constraints or equivalent validated constraints for stable status code sets.

Recommended status groups:

- Tenant: created, active, suspended, closed.
- Store: created, active, inactive, archived.
- Area: created, active, inactive, archived.
- Table: available, locked, reserved, occupied, cleaning, inactive.
- Fixed TableGroup: created, active, inactive, deleted.
- Temporary TableGroup: created, locked, occupied, released, ended.
- Reservation: draft, confirmed, arrived, seated, completed, cancelled, no_show.
- QueueTicket: waiting, called, skipped, rejoined, seated, cancelled, expired.
- WalkIn: arrived, queued, seated, cancelled, abandoned.
- Seating: planned, locked, occupied, completed, cleaning_triggered, cancelled.
- Cleaning: pending, cleaning, completed, released, cancelled.
- Turnover: pending, recorded, archived.
- IdempotencyRecord: started, completed, failed, expired.

Notes:

- Check constraints protect allowed values.
- Legal transition rules still belong to state machine policy and `state_transition_logs`; do not rely on status value constraints alone.
- User-facing text comes from i18n keys, not status code labels.

## Foreign Key Creation Order

Strict ownership references should be created first:

1. Store to Tenant.
2. Store policy to Store and Tenant.
3. Area to Store and Tenant.
4. DiningTable to Area, Store, and Tenant.
5. TableGroup to Store and Tenant.
6. TableGroupMember to TableGroup and DiningTable.
7. Customer to Tenant.
8. Reservation to Tenant, Store, and Customer.
9. ReservationPreassignment to Reservation and resource.
10. QueueGroup to Store and Tenant.
11. WalkIn to Tenant, Store, and Customer.
12. QueueTicket to Tenant, Store, QueueGroup, Customer, optional Reservation, optional WalkIn.
13. Seating to Tenant, Store, optional Reservation, optional QueueTicket, optional WalkIn.
14. SeatingResource to Seating and resource.
15. Cleaning to Seating and resource.
16. Turnover to Seating and optional Cleaning.

Generic target tables:

- `business_events`, `state_transition_logs`, and `audit_logs` may use generic target fields. The migration should avoid impossible cross-table foreign keys where a single target can point to many table types. Scope and target validation belongs to domain policy or later database trigger design if approved.

## Index Creation Order

Create indexes after base tables and foreign keys.

Recommended index groups:

1. Tenant and Store lookup indexes.
2. Store resource indexes for Area, DiningTable, TableGroup, and TableGroupMember.
3. Reservation schedule and active conflict indexes.
4. QueueGroup and QueueTicket operating-window indexes.
5. Seating and active resource occupancy indexes.
6. Cleaning active resource indexes.
7. TableLock active/expiry indexes.
8. Audit/event/state-transition timeline indexes.
9. Idempotency lookup and expiry indexes.
10. i18n lookup indexes.

Key uniqueness boundaries:

- Tenant code at Platform scope.
- Store code within Tenant.
- Area code within Store.
- Table code within Store.
- TableGroup code within Store.
- Customer code within Tenant.
- Reservation code within Store.
- Active duplicate Reservation for Tenant + Store + Customer + time slot and active statuses.
- QueueTicket number within Tenant + Store + QueueGroup + business date/window.
- Active table resource occupancy.
- Idempotency key within source/action/scope.
- i18n key + locale within message scope.

## Soft Delete Strategy

Migration should add `deleted_at` to all business/configuration tables where lifecycle history matters:

- tenants
- stores
- store_policies
- store_areas
- dining_tables
- table_groups
- table_group_members
- customers
- reservations
- reservation_preassignments
- queue_groups
- queue_tickets
- walk_ins
- seatings
- seating_resources
- cleanings
- turnovers
- reason_codes
- i18n_message_catalog

Notes:

- Key business records must not be physically deleted.
- Unique constraints should account for soft-deleted rows where active uniqueness is needed.
- Audit/event/state-transition logs should generally not be soft-deleted.

## Audit Field Strategy

Business/configuration tables should have:

- `created_at`
- `updated_at`
- `deleted_at` where applicable
- `version` for stateful or frequently updated rows

Critical operation detail belongs in:

- `audit_logs`
- `business_events`
- `state_transition_logs`

Audit must capture:

- Actor.
- Actor role.
- Tenant scope.
- Store scope where applicable.
- Operation source.
- Before state.
- After state.
- Idempotency key.
- Failure reason when applicable.

## Idempotency Table Strategy

Create `idempotency_records` before or alongside operational tables that will use idempotent commands.

Required support:

- Nullable `store_id` for Tenant/Platform actions.
- `idempotency_key`.
- `request_hash`.
- Nullable `response_snapshot`.
- Status.
- Expiry.
- Scope/action/source uniqueness.

Future operations depending on idempotency:

- Reservation create/confirm.
- CheckIn.
- QueueTicket create/call/rejoin.
- Seating.
- Table lock acquire/release.
- Cleaning completion.
- Third-party calls.
- Future webhook retry.

## i18n Message Catalog Strategy

Create `i18n_message_catalog` early, after Tenant/Store roots or before reason-code seed data in a later approved seed-data round.

Support:

- Platform-level catalog when `tenant_id` and `store_id` are absent.
- Tenant-level override when `tenant_id` is present and `store_id` is absent.
- Optional Store-level override for future use.
- Unique message per scope + i18n key + locale.

This round does not create seed data or message content.

## State Machine Log Strategy

Create `state_transition_logs` after operational stateful tables are known, or create it earlier with generic target fields.

Stateful targets:

- reservations
- queue_tickets
- dining_tables
- table_groups
- seatings
- cleanings
- turnovers where lifecycle is tracked

Notes:

- Status value constraints belong on each target table.
- Legal transition validation belongs to reusable StateMachine / TransitionPolicy code in a later implementation round.
- The log preserves evidence and auditability.

## Rollback Risk Notes

High-risk rollback areas:

- Dropping or changing status values after production data exists.
- Changing Tenant/Store scope fields after references exist.
- Changing reservation active conflict uniqueness after real bookings exist.
- Changing QueueTicket numbering uniqueness after queue history exists.
- Changing seating resource occupancy rules after service history exists.
- Removing audit, event, or transition records.
- Tightening nullable fields around Customer phone or anonymous guests.
- Changing timezone/local business date derivation after reports exist.

Rollback posture:

- Prefer additive migrations.
- Avoid destructive changes to history tables.
- Add new status values before migrating data to them.
- Backfill before enforcing stricter not-null or uniqueness rules.
- Keep old audit/event data readable even if new target rules are introduced.

## Explicit Non-Actions This Round

- No Flyway migration created.
- No SQL file created.
- No database migration executed.
- No database connection opened.
- No Java Entity, Repository, Service, Controller, or DTO created.
- No API implemented.
- No Vue UI created.
- No tests or mock data created.
- No configuration or dependency file changed.
