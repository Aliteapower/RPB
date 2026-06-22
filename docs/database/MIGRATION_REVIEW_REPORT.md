# Migration Review Report V1

## 1. Migration File Path

- docs/database/migrations/V001__reservation_platform_bootstrap.sql

Flyway runtime path not found. Migration draft created under docs/database/migrations for review only.

## 2. Created Tables

Tenant / Store base:

- tenants
- stores
- store_policies
- store_areas
- dining_tables

Table management:

- table_groups
- table_group_members
- table_locks

Customer:

- customers

Reservation / Queue / WalkIn:

- reservations
- reservation_preassignments
- queue_groups
- queue_tickets
- walk_ins

Seating / Cleaning / Turnover:

- seatings
- seating_resources
- cleanings
- turnovers

Governance / Event / Audit:

- business_events
- state_transition_logs
- audit_logs
- idempotency_records
- reason_codes
- i18n_message_catalog

## 3. Created Constraints

Primary constraints:

- UUID primary key on all created tables using `gen_random_uuid()`.
- `pgcrypto` extension creation is included.

Scope constraints:

- Store-scoped tables include `tenant_id` and `store_id`.
- Tenant-scoped `customers` includes `tenant_id` and does not require `store_id`.
- Audit/event/idempotency tables allow nullable `store_id` according to scope.
- Composite foreign keys are used where practical to keep Store references inside Tenant scope.

Business constraints:

- Reservation status check covers draft, confirmed, arrived, seated, completed, cancelled, no_show.
- QueueTicket status check covers waiting, called, skipped, rejoined, seated, cancelled, expired.
- DiningTable status check covers available, locked, reserved, occupied, cleaning, inactive.
- DiningTable capacity uses `capacity_min` and `capacity_max`.
- TableGroup capacity uses `capacity_min` and `capacity_max`.
- Reservation, QueueTicket, and WalkIn guest count use `party_size`.
- Seating captures the served party count as `party_size_snapshot`.
- TableGroup supports fixed and temporary lifecycle status groups.
- Seating source check enforces exactly one of reservation_id, queue_ticket_id, walk_in_id.
- SeatingResource resource check enforces exactly one of table_id, table_group_id.
- Cleaning resource check enforces exactly one of table_id, table_group_id.
- QueueTicket source check prevents both reservation_id and walk_in_id being populated together.
- Customer phone E.164 check allows null phone.
- TableLock active resource and lock-key uniqueness are represented with partial unique indexes.
- Idempotency null-scope uniqueness is handled with partial unique indexes for Platform, Tenant, and Store scopes.

## 4. Created Indexes

Reservation:

- Store schedule index on tenant_id, store_id, business_date, reserved_start_at.
- Store reservation code unique index.
- Customer start lookup index.
- Active customer slot conflict unique index for confirmed, arrived, seated.
- Status and hold-until lookup index.

QueueTicket:

- Unique queue number index by tenant_id, store_id, queue_group_id, business_date, ticket_number.
- Active queue ordering index by tenant_id, store_id, queue_group_id, business_date, status, queue_position.
- Call timeout index by tenant_id, store_id, status, called_at.
- Source lookup indexes for reservation_id and walk_in_id.

TableLock:

- Active lock key unique index.
- Active resource unique index.
- Resource/status index.
- Expiry scan index.
- Idempotency lookup index.

SeatingResource:

- Seating lookup index.
- Resource/status index.
- Active table occupancy unique index.
- Active TableGroup occupancy unique index.

Audit / Event / Transition:

- Target timeline indexes on target_type, target_id, occurred_at.
- Scope timeline indexes on tenant_id, store_id, occurred_at.
- Audit operation index on operation_code, occurred_at.

Idempotency:

- Platform-scope unique index.
- Tenant-scope unique index.
- Store-scope unique index.
- Expiry scan index.

i18n / Reason:

- Platform, Tenant, and Store message unique indexes.
- Reason code Tenant and Store unique indexes.
- Active reason lookup index.

## 5. Items Not Fully Guaranteed by SQL

The following must be handled by later Rule / Policy / Validator design:

- TableGroupValidationRule must validate inactive table usage and temporary group lifecycle beyond static membership constraints.
- TableGroupValidationRule must prevent semantic circular composition if future design ever allows nested groups. Current migration prevents group-to-group membership by design.
- TableAssignmentRule must validate full table availability across fixed/temporary TableGroup membership, because active group occupancy can imply member table unavailability.
- ReservationAvailabilityRule must detect true overlapping time windows beyond same start/end slot uniqueness.
- StateMachine / TransitionPolicy must enforce legal state transitions. Migration only constrains allowed status values.
- TenantScope / StoreScope policies must validate generic target references in audit_logs, business_events, and state_transition_logs.
- AuditRule must ensure every critical operation writes audit entries.
- IdempotencyRule must define request replay behavior and response consistency.

## 6. Consistency With SCHEMA_DESIGN.md

- Consistent with SCHEMA_DESIGN.md table coverage.
- Uses `timestamptz` for fact instants.
- Preserves Customer as Tenant-scoped and phone nullable.
- Preserves Reservation / QueueTicket / WalkIn / Seating separation.
- Preserves CheckIn as event/audit/state transition only.
- Preserves state logs, audit logs, and idempotency boundaries.

## 7. check_ins Primary Table

- Created: No

CheckIn is represented by:

- business_events
- state_transition_logs
- audit_logs

## 8. Reservation / QueueTicket / WalkIn / Seating Boundary

- Reservation is independent from QueueTicket.
- QueueTicket optionally references Reservation or WalkIn.
- WalkIn is independent and can go directly to Seating.
- Seating is an independent occupancy table and requires exactly one source.

## 9. tenant_id / store_id Layering

- Platform-level i18n messages can have null tenant_id and store_id.
- Tenant-level customers require tenant_id and do not require store_id.
- Store configuration and operation tables require tenant_id and store_id.
- Audit/event/idempotency tables support nullable store_id according to target scope.

## 10. No-Phone Customer Support

- customers.phone_e164 is nullable.
- phone_e164 is not a primary key.
- Partial unique index applies only when phone_e164 is not null.
- customer_type supports anonymous, temporary, boss_friend, and walk_in_guest.

## 11. Seating Source Three-Way Rule

- seatings has reservation_id, queue_ticket_id, and walk_in_id.
- Check constraint requires exactly one source to be non-null.

## 12. SeatingResource Two-Way Resource Rule

- seating_resources has table_id and table_group_id.
- Check constraint requires exactly one resource to be non-null.

## 13. Cleaning Two-Way Resource Rule

- cleanings has table_id and table_group_id.
- Check constraint requires exactly one resource to be non-null.

## 14. OOD & Reuse Support

Supported by schema boundaries:

- TenantScope: tenant_id and composite scope references.
- StoreScope: store_id plus tenant_id on Store-owned data.
- StateMachine: status fields plus state_transition_logs.
- TransitionPolicy: status value constraints and transition logs.
- AuditRule: audit_logs, business_events, state_transition_logs.
- IdempotencyRule: idempotency_records and table_locks.idempotency_key.
- TableLockRule: table_locks.
- TableAssignmentRule: dining_tables, table_groups, table_group_members, table_locks, seating_resources.
- TableGroupValidationRule: table_groups and table_group_members.
- CustomerIdentityRule: customers with Tenant-scope and nullable E.164 phone.
- StoreLocaleRule: stores timezone, locale, date_format, time_format, currency.

## 14.1 Party Size and Capacity Review

Covered in migration:

- `dining_tables.capacity_min`
- `dining_tables.capacity_max`
- `table_groups.capacity_min`
- `table_groups.capacity_max`
- `reservations.party_size`
- `queue_tickets.party_size`
- `walk_ins.party_size`
- `seatings.party_size_snapshot`

Not added in V1 migration:

- `adult_count`
- `child_count`
- `infant_count`
- `high_chair_required`
- `wheelchair_required`

Reason:

- V1 rules require total party size for reservation availability, queue matching, WalkIn seating, table assignment, and turnover baseline.
- More granular guest composition and accessibility needs are valid future extensions, but they were not yet confirmed as required V1 schema fields.

## 15. Java / Vue / API / UI

- Java changed: No
- Vue changed: No
- API implemented: No
- UI implemented: No
- Entity created: No
- Repository created: No

## 16. Business Data

- Seed data inserted: No
- Mock data inserted: No
- Production database touched: No

## 17. Validation Result

Static validation:

- Migration draft file exists.
- All required core tables are present.
- Required source/resource check constraints are present.
- Required reservation, queue, table lock, idempotency, audit, event, and transition indexes are present.
- No `check_ins` primary table is created.
- No `insert into` seed, mock, or business data statement is present.
- No Java, Vue, API, config, or test artifacts were created.

Execution validation:

- PostgreSQL 17.10 client and server tools were available locally.
- A temporary local empty PostgreSQL cluster was initialized under the workspace.
- The migration was executed with `psql -v ON_ERROR_STOP=1`.
- The migration completed successfully in the temporary empty database.
- PostgreSQL catalog checks confirmed 24 public tables, 55 check constraints, 69 foreign keys, 24 primary keys, 12 unique constraints, and 107 indexes.
- No duplicate constraint or index names were found.
- The temporary PostgreSQL cluster was stopped and removed after validation.
