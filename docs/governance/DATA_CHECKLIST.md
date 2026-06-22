# Data Checklist

## Purpose

Use this checklist before database design, API design, implementation, review, or migration work. This checklist is governance-only and does not define concrete tables, fields, DDL, migrations, APIs, or code.

## Scope Checklist

- [ ] Does the data belong to Platform, Tenant, Store, Store Operations, Cross-Store Shared, or Audit scope?
- [ ] Is tenant_id required by the data level?
- [ ] Is store_id required by actual Store ownership rather than mechanically added to every object?
- [ ] Is any Platform-level data incorrectly forced into Tenant or Store scope?
- [ ] Is any Tenant-level shared data incorrectly forced into one Store?
- [ ] Is any Store operational data missing Store ownership?

## Multi-Tenant Checklist

- [ ] Is any cross-Tenant reference possible?
- [ ] Can a Store-level record reference another Tenant's Store, Area, Table, Reservation, QueueTicket, Seating, or Cleaning?
- [ ] Is Customer identity shared only within one Tenant?
- [ ] Are audit records scoped to the correct Tenant and Store context?
- [ ] Are integration calls prevented from crossing Tenant boundaries?

## Object Separation Checklist

- [ ] Is Reservation kept separate from QueueTicket?
- [ ] Is WalkIn kept separate from QueueTicket?
- [ ] Is CheckIn kept separate from Seating?
- [ ] Is Seating kept separate from Reservation creation?
- [ ] Is Cleaning kept separate from Turnover?
- [ ] Is Table kept separate from TableGroup?
- [ ] Is Customer kept separate from Member, loyalty, and marketing concerns?

## Customer Checklist

- [ ] Does the design support Customer without phone number?
- [ ] Does the design support anonymous customer, walk-in guest, boss friend, temporary customer, and no-phone customer?
- [ ] Can no-phone customers be found by temporary number and scenario information?
- [ ] Are phone numbers, when present, normalized to E.164?
- [ ] Is Customer uniqueness scoped to Tenant rather than Platform or Store?

## Reservation Checklist

- [ ] Does Reservation lock Store + date + time slot + party-size capacity by default?
- [ ] Does Reservation avoid locking a specific Table by default?
- [ ] Is optional pre-assignment clearly separated from final Seating?
- [ ] Does confirmed Reservation require CheckIn before Seating or Queue decision?
- [ ] Are cancelled, no_show, and completed handled as terminal states?
- [ ] Is same-customer overlapping Reservation policy explicitly decided before implementation?

## Queue Checklist

- [ ] Is QueueTicket created only when the arrived customer needs to wait?
- [ ] Is QueueTicket number unique within its queue group?
- [ ] Does rejoined keep the original number?
- [ ] Does rejoined avoid default queue cutting?
- [ ] Is queue placement after rejoin governed by Store policy?
- [ ] Are called, skipped, rejoined, seated, cancelled, and expired transitions legal and audited?

## WalkIn Checklist

- [ ] Can WalkIn be seated directly without taking a number?
- [ ] Does WalkIn create QueueTicket only when no suitable table is available?
- [ ] Can WalkIn use temporary or anonymous Customer context?
- [ ] Is WalkIn not treated as a permanent Customer type?

## CheckIn and Seating Checklist

- [ ] Is CheckIn modeled as a business event in V1, not a primary entity?
- [ ] Does CheckIn trigger Reservation confirmed -> arrived?
- [ ] Does CheckIn avoid creating table occupancy?
- [ ] Does Seating create table occupancy?
- [ ] Does Seating originate from Reservation, QueueTicket, or WalkIn?
- [ ] Does Seating prevent duplicate active occupancy?
- [ ] Does manual table override require audit and reason?

## Table and TableGroup Checklist

- [ ] Can Table support area, capacity, status, Reservation, Queue, WalkIn, Seating, Cleaning, and Turnover?
- [ ] Are available, locked, reserved, occupied, cleaning, and inactive states covered?
- [ ] Does locked have an expiry rule?
- [ ] Is reserved clearly different from occupied?
- [ ] Must cleaning complete before Table becomes available?
- [ ] Is inactive blocked from Reservation, Queue, Seating, and recommendation?
- [ ] Is TableGroup circular reference prevented?
- [ ] Is duplicate Table use across active TableGroups prevented?
- [ ] Is temporary TableGroup release defined?
- [ ] Is fixed TableGroup blocked from recommendation when inactive?

## Data Integrity Checklist

- [ ] Are required fields justified by lifecycle and business meaning?
- [ ] Are nullable fields intentionally nullable?
- [ ] Is no-phone Customer support preserved?
- [ ] Is Store operational ownership explicit?
- [ ] Are Reservation core data requirements complete enough for later design?
- [ ] Are QueueTicket core data requirements complete enough for later design?
- [ ] Are Seating core data requirements complete enough for later design?
- [ ] Is Table status complete and valid?

## State Machine Checklist

- [ ] Is the initial state defined?
- [ ] Are legal transitions defined?
- [ ] Are illegal transitions defined?
- [ ] Is each transition trigger defined?
- [ ] Are transition preconditions defined?
- [ ] Are transition business impacts defined?
- [ ] Is each critical transition audited?
- [ ] Are idempotency requirements defined?
- [ ] Are concurrency requirements defined?
- [ ] Is any state transition duplicated across future modules or pages?

## Audit Checklist

- [ ] Is reservation creation audited?
- [ ] Is reservation confirmation audited?
- [ ] Is reservation cancellation audited?
- [ ] Is reservation no_show audited?
- [ ] Is Customer CheckIn audited?
- [ ] Is queue number creation audited?
- [ ] Is queue call audited?
- [ ] Is skipped audited?
- [ ] Is rejoined audited?
- [ ] Is Seating audited?
- [ ] Is table change audited?
- [ ] Is TableGroup creation, use, and release audited?
- [ ] Is Cleaning start and completion audited?
- [ ] Is table release audited?
- [ ] Is manual override audited with reason?
- [ ] Are critical configuration and permission changes audited?
- [ ] Are third-party calls audited?

## I18n Checklist

- [ ] Are timestamps stored in UTC?
- [ ] Are time values exchanged in ISO8601?
- [ ] Is Store timezone used for display?
- [ ] Is Store locale used for display?
- [ ] Are date format, time format, and currency Store-configured?
- [ ] Is Singapore default Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD?
- [ ] Are user-facing messages represented by i18n keys?
- [ ] Is hardcoded display text avoided in later UI/API work?
- [ ] Are phone numbers E.164 when present?

## Idempotency and Concurrency Checklist

- [ ] Is an idempotency key required for external calls?
- [ ] Is an idempotency key required for critical repeatable operations?
- [ ] Is Table lock acquisition protected from duplicate requests?
- [ ] Is Table occupancy protected from race conditions?
- [ ] Is Reservation capacity protected from concurrent overbooking?
- [ ] Is Queue call protected from double-calling or stale calls?
- [ ] Is rejoin protected from duplicate placement?
- [ ] Is temporary TableGroup protected from duplicate use and unreleased resources?
- [ ] Is Cleaning completion protected from double release?

## Deletion Checklist

- [ ] Is soft deletion the default?
- [ ] Are cancel, deactivate, archive, and physical delete clearly separated?
- [ ] Are key business records protected from physical deletion?
- [ ] Can soft-deleted resources be blocked from new operations?
- [ ] Can historical records still reference soft-deleted resources for audit?
- [ ] Are deletion actor, time, reason, source, and scope captured?
