# Data Model Checklist V1

## Purpose

Use this checklist to review database design before any migration, SQL, Java, API, UI, or test work. It is a database design checklist only.

## Phase Safety

- [ ] Does the work stay inside database design documentation?
- [ ] Has no Flyway Migration been created?
- [ ] Has no SQL file been created?
- [ ] Has no Java Entity been created?
- [ ] Has no Repository been created?
- [ ] Has no Service or Controller been created?
- [ ] Has no API been implemented?
- [ ] Has no Vue page or component been created?
- [ ] Has no test code been created?
- [ ] Has no mock business data been created?
- [ ] Has no configuration file been changed?

## Governance Inputs

- [ ] Has BUSINESS_GLOSSARY.md been read?
- [ ] Has BUSINESS_RULES.md been read?
- [ ] Has DATA_STANDARD.md been read?
- [ ] Has DATA_CHECKLIST.md been read?
- [ ] Has ARCHITECTURE.md been read?
- [ ] Has SKILL_OVERVIEW.md been read?
- [ ] Has SKILL.md been read?

## Object Boundary Checks

- [ ] Is Reservation kept separate from QueueTicket?
- [ ] Is WalkIn kept separate from QueueTicket?
- [ ] Is CheckIn kept separate from Seating?
- [ ] Is Seating kept separate from Reservation?
- [ ] Is Cleaning kept separate from Turnover?
- [ ] Is Table kept separate from TableGroup?
- [ ] Is Customer kept separate from Member?

## tenant_id / store_id Checks

- [ ] Does each table have a clear Platform, Tenant, Store, Store Operation, Cross-Store Shared, or Audit level?
- [ ] Are Platform tables not forced to have tenant_id or store_id?
- [ ] Are Tenant tables scoped by tenant_id?
- [ ] Are Store configuration tables scoped by tenant_id and store_id?
- [ ] Are Store operation tables scoped by tenant_id and store_id?
- [ ] Are Cross-Store shared tables Tenant-scoped without unnecessary store_id?
- [ ] Are audit/event tables scoped according to their related target?
- [ ] Is the design avoiding the mistake of requiring store_id on every table?
- [ ] Is every Store-level reference protected from cross-Tenant access?

## Customer Checks

- [ ] Does Customer uniqueness remain Tenant-scoped?
- [ ] Does Customer support missing phone number?
- [ ] Does Customer support anonymous customer?
- [ ] Does Customer support temporary customer?
- [ ] Does Customer support walk-in guest?
- [ ] Does Customer support no-phone lookup by temporary number and scenario information?
- [ ] Are phone numbers E.164 when present?
- [ ] Is Customer not treated as Member or loyalty account?

## Reservation Checks

- [ ] Does Reservation represent advance capacity, not a queue number?
- [ ] Does Reservation default to Store + date + time slot + party-size capacity?
- [ ] Does Reservation avoid locking a specific Table by default?
- [ ] Is optional table preassignment separate from final Seating?
- [ ] Does Reservation support confirmed, arrived, seated, completed, cancelled, and no_show lifecycle?
- [ ] Does Reservation require CheckIn before arrived state?
- [ ] Is the V1 active duplicate rule represented: same Tenant + Store + Customer + time slot cannot have multiple confirmed / arrived / seated Reservations?
- [ ] Is the 15-minute default hold policy represented as Store-configurable policy?

## Queue Checks

- [ ] Does QueueTicket represent waiting after arrival?
- [ ] Can QueueTicket source be checked-in Reservation?
- [ ] Can QueueTicket source be WalkIn?
- [ ] Is QueueTicket not required for every Reservation?
- [ ] Is QueueTicket not required for every WalkIn?
- [ ] Does V1 QueueGroup use Store + Party Size Group?
- [ ] Are default groups represented: 1-2, 3-4, 5-6, 7+?
- [ ] Is queue ticket number uniqueness scoped by Tenant + Store + queue operating window + QueueGroup?
- [ ] Is the 3-minute default call hold policy represented as Store-configurable policy?
- [ ] Does rejoined keep original number without default queue cutting?

## WalkIn Checks

- [ ] Does WalkIn exist separately from QueueTicket?
- [ ] Can WalkIn go directly to Seating?
- [ ] Can WalkIn create QueueTicket only when no suitable table is available?
- [ ] Can WalkIn reference temporary or anonymous Customer?
- [ ] Is WalkIn not modeled as advance reservation?

## CheckIn and Event Checks

- [ ] Is CheckIn not modeled as a primary table?
- [ ] Is CheckIn represented by business event, audit, and Reservation state transition?
- [ ] Does CheckIn avoid creating occupancy?
- [ ] Is CheckIn idempotency considered?
- [ ] Is CheckIn audited with actor, role, source, scope, before state, after state, and failure reason when applicable?

## Seating Checks

- [ ] Is Seating a separate occupancy record?
- [ ] Does Seating reference exactly one source: Reservation, QueueTicket, or WalkIn?
- [ ] Does Seating avoid creating Reservation or QueueTicket?
- [ ] Does Seating support Table or TableGroup resources?
- [ ] Does Seating prevent overlapping active occupancy?
- [ ] Does Seating support manual override audit and reason?
- [ ] Is expected dining duration represented as Store-configurable policy with V1 default 90 minutes?

## Cleaning and Turnover Checks

- [ ] Is Cleaning a separate status flow after Seating?
- [ ] Does Cleaning reference Seating?
- [ ] Does Cleaning target Table or TableGroup?
- [ ] Does Cleaning block resource availability until completion?
- [ ] Is Turnover derived from Seating + completion + Cleaning?
- [ ] Is Turnover not calculated from Reservation alone?
- [ ] Is Turnover not modeled as live seating or cleaning action?

## Table and TableGroup Checks

- [ ] Is Table a Store and Area resource?
- [ ] Does Table support available, locked, reserved, occupied, cleaning, and inactive?
- [ ] Is reserved different from occupied?
- [ ] Does locked have expiry and release boundary?
- [ ] Does inactive block reservation, queue, seating, and recommendation?
- [ ] Does TableGroup support fixed and temporary types?
- [ ] Does TableGroup membership enforce same Tenant and Store?
- [ ] Does TableGroup design prevent circular references?
- [ ] Does TableGroup design prevent duplicate active membership for temporary groups?
- [ ] Does temporary TableGroup release member Tables?
- [ ] Does TableGroup remain separate from base Table?

## Soft Delete Checks

- [ ] Is soft delete or lifecycle status the default?
- [ ] Are key business records protected from physical deletion?
- [ ] Are soft-deleted resources blocked from new operations?
- [ ] Can historical records still reference soft-deleted resources?
- [ ] Is deletion audit captured?
- [ ] Are active locks, active seating, active cleaning, and active temporary TableGroups resolved before deletion or deactivation?

## Audit Checks

- [ ] Is audit_logs included as a reusable data boundary?
- [ ] Are Reservation creation, confirmation, cancellation, and no_show audited?
- [ ] Is CheckIn audited?
- [ ] Are QueueTicket creation, call, skip, and rejoin audited?
- [ ] Is Seating audited?
- [ ] Are table changes audited?
- [ ] Are TableGroup creation, use, and release audited?
- [ ] Are Cleaning start and completion audited?
- [ ] Is table release audited?
- [ ] Is manual override audited with reason?
- [ ] Are configuration changes and permission changes audited?
- [ ] Are third-party calls audited?

## Idempotency Checks

- [ ] Is idempotency_records included as a reusable data boundary?
- [ ] Is Reservation creation idempotent?
- [ ] Is Reservation confirmation idempotent?
- [ ] Is CheckIn idempotent?
- [ ] Is QueueTicket creation idempotent?
- [ ] Is Queue call idempotent?
- [ ] Is Queue rejoin idempotent?
- [ ] Is Seating idempotent?
- [ ] Is Table lock acquisition and release idempotent?
- [ ] Is Cleaning completion idempotent?
- [ ] Are third-party calls idempotent?

## Time and I18n Checks

- [ ] Are all instants stored in UTC?
- [ ] Are system time values exchanged as ISO8601?
- [ ] Is Store local business date derived from Store timezone where needed?
- [ ] Does Store store timezone, locale, date_format, time_format, and currency?
- [ ] Is Singapore default represented: Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD?
- [ ] Are status display values represented by codes and i18n keys?
- [ ] Are reason codes compatible with i18n keys?
- [ ] Is hardcoded system display text avoided?

## Concurrency and Lock Checks

- [ ] Is Reservation capacity protected from concurrent overbooking?
- [ ] Is active duplicate Reservation protected for same Tenant + Store + Customer + time slot?
- [ ] Is Table lock protected from concurrent acquisition?
- [ ] Is Table occupancy protected from overlapping active Seating?
- [ ] Is Temporary TableGroup protected from duplicate active use?
- [ ] Is Queue call protected from stale or duplicate calls?
- [ ] Is Queue rejoin protected from duplicate placement?
- [ ] Is Cleaning completion protected from double release?
- [ ] Is Redis allowed for fast locks while PostgreSQL preserves durable boundaries?

## OOD and Reuse Checks

- [ ] Does the model preserve business objects instead of building one large operational table?
- [ ] Are state machine transitions reusable through state_transition_logs?
- [ ] Is Tenant/Store isolation reusable through scope fields and reference rules?
- [ ] Is Store timezone and locale reusable through Store data?
- [ ] Is E.164 phone validation anchored in Customer data?
- [ ] Is idempotency reusable through idempotency_records?
- [ ] Is audit reusable through audit_logs?
- [ ] Is soft deletion consistent across business tables?
- [ ] Is Table lock reusable through table_locks?
- [ ] Is Queue sorting supported by QueueGroup and policy data?
- [ ] Is Reservation time conflict supported by Reservation time-slot data?
- [ ] Is Table availability supported by Table, lock, seating, and cleaning data?
- [ ] Is TableGroup validity supported by TableGroup and member data?
