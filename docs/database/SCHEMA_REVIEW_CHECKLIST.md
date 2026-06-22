# Schema Review Checklist V1

## Purpose

Use this checklist to review the PostgreSQL schema draft before any Flyway migration, executable SQL, Java, API, UI, test, or mock-data work.

## Phase Safety

- [ ] Is this still a Markdown-only schema design round?
- [ ] Has no Flyway migration file been created?
- [ ] Has no SQL executable file been created?
- [ ] Has no database connection been opened?
- [ ] Has no database migration been executed?
- [ ] Has no Java Entity been created?
- [ ] Has no Repository been created?
- [ ] Has no Service, Controller, or DTO been created?
- [ ] Has no API been implemented?
- [ ] Has no Vue page or component been created?
- [ ] Has no test code been created?
- [ ] Has no mock data been created?
- [ ] Has no configuration or dependency file been changed?

## Required Inputs

- [ ] BUSINESS_GLOSSARY.md was read.
- [ ] BUSINESS_RULES.md was read.
- [ ] DATA_STANDARD.md was read.
- [ ] DATA_CHECKLIST.md was read.
- [ ] ARCHITECTURE.md was read.
- [ ] SKILL_OVERVIEW.md was read.
- [ ] SKILL.md was read.
- [ ] DATABASE_DESIGN.md was read.
- [ ] ERD.md was read.
- [ ] DATA_MODEL_CHECKLIST.md was read.

## Core Table Coverage

- [ ] `tenants` is covered.
- [ ] `stores` is covered.
- [ ] `store_policies` is covered.
- [ ] `store_areas` is covered.
- [ ] `dining_tables` is covered.
- [ ] `table_groups` is covered.
- [ ] `table_group_members` is covered.
- [ ] `table_locks` is covered.
- [ ] `customers` is covered.
- [ ] `reservations` is covered.
- [ ] `reservation_preassignments` is covered if optional preassignment is retained.
- [ ] `queue_groups` is covered.
- [ ] `queue_tickets` is covered.
- [ ] `walk_ins` is covered.
- [ ] `seatings` is covered.
- [ ] `seating_resources` is covered.
- [ ] `cleanings` is covered.
- [ ] `turnovers` is covered.
- [ ] `business_events` is covered.
- [ ] `state_transition_logs` is covered.
- [ ] `audit_logs` is covered.
- [ ] `idempotency_records` is covered.
- [ ] `reason_codes` is covered.
- [ ] `i18n_message_catalog` is covered.

## Business Boundary Checks

- [ ] Reservation and QueueTicket are not merged.
- [ ] Reservation does not depend on QueueTicket.
- [ ] QueueTicket can optionally reference Reservation.
- [ ] WalkIn and QueueTicket are not merged.
- [ ] WalkIn can directly enter Seating.
- [ ] WalkIn creates QueueTicket only when no table is available.
- [ ] CheckIn is not created as a primary table.
- [ ] CheckIn is represented through business_events, state_transition_logs, and audit_logs.
- [ ] Seating is separate from Reservation.
- [ ] Seating is separate from QueueTicket.
- [ ] Cleaning is separate from Turnover.
- [ ] Table is separate from TableGroup.
- [ ] Customer is separate from Member.

## Seating Source Checks

- [ ] `seatings.reservation_id` is nullable.
- [ ] `seatings.queue_ticket_id` is nullable.
- [ ] `seatings.walk_in_id` is nullable.
- [ ] The schema draft requires exactly one of Reservation, QueueTicket, or WalkIn as Seating source.
- [ ] Seating source scope must match `tenant_id` and `store_id`.
- [ ] Seating does not create Reservation or QueueTicket.

## Customer Checks

- [ ] `customers` has `tenant_id`.
- [ ] `customers` does not require `store_id`.
- [ ] Phone number is nullable.
- [ ] Phone number follows E.164 when present.
- [ ] Phone number is not primary key.
- [ ] No-phone Customer lookup is supported by customer code, name, nickname, notes, or scenario data.
- [ ] Customer uniqueness is Tenant-scoped.
- [ ] Anonymous and temporary customer types are supported.

## tenant_id / store_id Checks

- [ ] Platform-level tables do not mechanically require `tenant_id` or `store_id`.
- [ ] Tenant-level tables require `tenant_id`.
- [ ] Store configuration tables require `tenant_id` and `store_id`.
- [ ] Store operation tables require `tenant_id` and `store_id`.
- [ ] Cross-store shared tables avoid unnecessary `store_id`.
- [ ] Audit/event tables use scope according to target object.
- [ ] No table treats `store_id` as a substitute for `tenant_id`.
- [ ] Cross-Tenant references are forbidden by design.

## Time and I18n Checks

- [ ] Business instants use `timestamptz`.
- [ ] UTC storage is required.
- [ ] Store-local business date is separate from fact instants where needed.
- [ ] Store timezone is present.
- [ ] Store locale is present.
- [ ] Store date format is present.
- [ ] Store time format is present.
- [ ] Store currency is present.
- [ ] Status display text is not hardcoded.
- [ ] Reason display text uses `i18n_key`.
- [ ] i18n catalog supports `i18n_key`, `locale`, and `message`.

## Reservation Constraint Checks

- [ ] `reservations.tenant_id` is present.
- [ ] `reservations.store_id` is present.
- [ ] `reservations.customer_id` nullable rule is documented.
- [ ] `reservation_code` is Store-scoped unique.
- [ ] `reservations.party_size` is present and positive.
- [ ] `reserved_start_at` uses `timestamptz`.
- [ ] `reserved_end_at` uses `timestamptz`.
- [ ] `hold_until_at` uses `timestamptz`.
- [ ] Active duplicate rule covers Tenant + Store + Customer + time slot for confirmed / arrived / seated.
- [ ] Reservation status values are constrained.
- [ ] Cancellation and no_show reason codes are supported.
- [ ] Soft delete and audit metadata are represented.

## QueueTicket Constraint Checks

- [ ] `queue_tickets.tenant_id` is present.
- [ ] `queue_tickets.store_id` is present.
- [ ] `queue_group_id` is present.
- [ ] `customer_id` nullable rule is documented.
- [ ] `reservation_id` is nullable.
- [ ] `walk_in_id` is nullable.
- [ ] `ticket_number` is present.
- [ ] `queue_tickets.party_size` is present and positive.
- [ ] `business_date` is present for Store-local queue window.
- [ ] Unique boundary covers Store + QueueGroup + business date/window + ticket number.
- [ ] QueueTicket status values are constrained.
- [ ] `called_at`, `skipped_at`, `rejoined_at`, and `expires_at` use `timestamptz`.

## TableGroup Checks

- [ ] Fixed and temporary TableGroup types are supported.
- [ ] `table_groups.capacity_min` is present and positive.
- [ ] `table_groups.capacity_max` is present and greater than or equal to capacity_min.
- [ ] TableGroup status values cover fixed lifecycle.
- [ ] TableGroup status values cover temporary lifecycle.
- [ ] TableGroup membership is represented separately.
- [ ] Duplicate members are prevented.
- [ ] TableGroup-to-TableGroup membership is not allowed.
- [ ] Circular reference is prevented by design.
- [ ] Inactive Table cannot be added to active group.
- [ ] Temporary group duplicate active use is prevented.
- [ ] Temporary group release is represented.

## TableLock Checks

- [ ] `table_locks` exists.
- [ ] `lock_key` exists.
- [ ] `lock_owner` exists.
- [ ] `locked_until_at` uses `timestamptz`.
- [ ] `source_type` exists.
- [ ] `source_id` exists or is documented nullable.
- [ ] `idempotency_key` exists.
- [ ] Exactly one resource target is required.
- [ ] Active lock uniqueness is documented.
- [ ] Expiry scan index is recommended.

## Table Capacity Checks

- [ ] `dining_tables.capacity_min` is present and positive.
- [ ] `dining_tables.capacity_max` is present and greater than or equal to capacity_min.
- [ ] Capacity search indexes use `capacity_max`.
- [ ] TableAssignmentRule can compare guest count against Table capacity.
- [ ] TableAssignmentRule can compare guest count against TableGroup capacity.

## Idempotency Checks

- [ ] `idempotency_records` exists.
- [ ] `tenant_id` exists and can be nullable for Platform-only actions.
- [ ] `store_id` can be nullable for Tenant/Platform actions.
- [ ] `idempotency_key` exists.
- [ ] `request_hash` exists.
- [ ] `response_snapshot` is nullable.
- [ ] `status` exists.
- [ ] `expires_at` uses `timestamptz`.
- [ ] Scope/action/key uniqueness is documented.

## Audit and State Checks

- [ ] `audit_logs` exists.
- [ ] `business_events` exists.
- [ ] `state_transition_logs` exists.
- [ ] Audit captures actor and actor role.
- [ ] Audit captures tenant and store scope where applicable.
- [ ] Audit captures before and after state.
- [ ] Audit captures operation source.
- [ ] Audit captures failure reason when applicable.
- [ ] State transition logs capture from_status and to_status.
- [ ] State transition logs capture transition code.
- [ ] State transition logs capture trigger and actor.

## Occupancy and Cleaning Checks

- [ ] `walk_ins.party_size` is present and positive.
- [ ] `seatings.party_size_snapshot` is present and positive.
- [ ] `seating_resources` prevents duplicate active resource occupancy by design.
- [ ] Seating resource has exactly one resource target.
- [ ] Cleaning references Seating.
- [ ] Cleaning has exactly one cleaned resource target.
- [ ] Cleaning blocks availability until completed.
- [ ] Turnover references Seating.
- [ ] Turnover can reference Cleaning.
- [ ] Turnover is not sourced from Reservation alone.

## OOD and Reuse Checks

- [ ] Schema supports TenantScope.
- [ ] Schema supports StoreScope.
- [ ] Schema supports StateMachine.
- [ ] Schema supports TransitionPolicy.
- [ ] Schema supports AuditRule.
- [ ] Schema supports IdempotencyRule.
- [ ] Schema supports TableLockRule.
- [ ] Schema supports TableAssignmentRule.
- [ ] Schema supports TableGroupValidationRule.
- [ ] Schema supports CustomerIdentityRule.
- [ ] Schema supports StoreLocaleRule.
- [ ] Schema avoids compressing multiple business objects into one large table.

## Final Review Gate

- [ ] No SQL file exists for this round.
- [ ] No Migration exists for this round.
- [ ] No Java/Vue/API/UI code changed.
- [ ] Actual modified files are limited to SCHEMA_DESIGN.md, MIGRATION_PLAN.md, and SCHEMA_REVIEW_CHECKLIST.md.
