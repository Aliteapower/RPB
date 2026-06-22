---
name: reservation-system
description: Use for reservation, booking, queue, walk-in, table management, seating, check-in, cleaning, turnover, 拼桌, 组合桌, and related governance, design, or implementation tasks in the Reservation Platform.
---

# Reservation System Skill

## Purpose

Use this Skill to keep Reservation Platform work aligned with the confirmed governance documents. This Skill governs the complete business loop:

```text
Reservation / WalkIn
  -> CheckIn when applicable
  -> QueueTicket when waiting is needed
  -> Seating
  -> Cleaning
  -> Turnover
```

The Skill protects business boundaries, data governance, multi-tenant scope, internationalization, state machines, audit, idempotency, concurrency, and OOD reuse.

## Trigger Words

Use this Skill when the request includes any of:

```text
reservation
booking
queue
walk-in
table
table management
seating
check-in
cleaning
turnover
排队
预约
订位
取号
叫号
入座
清台
翻台
拼桌
组合桌
```

## Required Inputs

Before acting, read the current governance documents:

- docs/governance/BUSINESS_GLOSSARY.md
- docs/governance/BUSINESS_RULES.md
- docs/governance/DATA_STANDARD.md
- docs/governance/DATA_CHECKLIST.md
- docs/architecture/ARCHITECTURE.md
- docs/skills/reservation-system/SKILL_OVERVIEW.md
- docs/skills/reservation-system/SKILL.md

For later database, API, UI, or implementation rounds, also read any phase-specific design document created after this governance round.

## Outputs

Depending on the requested phase, outputs may be:

- Governance clarification.
- Database design proposal.
- API design proposal.
- UI design proposal.
- Implementation plan.
- Code changes in later approved development rounds.
- Review findings.

Output must match the current phase. Do not skip phases.

## Execution Flow

1. Identify the current phase.
2. Read governance documents before proposing or changing anything.
3. Confirm the request stays inside the allowed phase.
4. Check business object boundaries.
5. Check Tenant and Store scope.
6. Check state machine rules.
7. Check audit, idempotency, and concurrency requirements.
8. Check internationalization requirements.
9. Check existing reusable objects, rules, policies, validators, and state machine boundaries before creating anything new.
10. Produce only the artifacts allowed by the current phase.

Governance phase order:

```text
Business Governance
-> Data Governance
-> Architecture Review
-> OOD & Reuse Governance where enabled by project bootstrap
-> Technical Baseline
-> Reservation Skill Governance
-> Database Design
-> API Design
-> UI Design
-> Development
```

## Core Objects

The Skill owns the following business vocabulary:

- Platform
- Tenant
- Store
- Area
- Table
- TableGroup
- Customer
- Reservation
- QueueTicket
- WalkIn
- CheckIn
- Seating
- Cleaning
- Turnover

Do not mix:

- Reservation != QueueTicket
- WalkIn != QueueTicket
- CheckIn != Seating
- Seating != Reservation
- Cleaning != Turnover
- Table != TableGroup
- Customer != Member

## State Machines

### Reservation

```text
draft
confirmed
arrived
seated
completed
cancelled
no_show
```

Rules:

- confirmed customer arrival must become arrived through CheckIn.
- arrived can proceed to Seating when a suitable table exists.
- arrived can enter Queue when no suitable table exists.
- seated can become completed.
- cancelled, no_show, and completed are terminal states.
- seated must not happen before CheckIn for Reservation flow.

### QueueTicket

```text
waiting
called
skipped
rejoined
seated
cancelled
expired
```

Rules:

- called can become skipped after no response.
- skipped can become rejoined.
- rejoined keeps the original number.
- rejoined does not cut the queue by default.
- seated, cancelled, and expired are terminal states.

### Table

```text
available
locked
reserved
occupied
cleaning
inactive
```

Rules:

- locked must expire or be released.
- reserved is not occupied.
- occupied requires valid Seating.
- cleaning must complete before available.
- inactive cannot be used for reservation, queue, seating, or recommendation.
- temporary TableGroup release must release related Tables.

## Business Rules

Confirmed rules:

- Reservation does not necessarily generate QueueTicket.
- Reserved customer arrival requires CheckIn before queue or seating decision.
- WalkIn can be seated directly without taking a number.
- CheckIn is a business event in V1, not a primary entity.
- Seating is a business event plus occupancy record.
- Cleaning is a status flow.
- Turnover is a result or metric from Seating + Completed + Cleaning.
- Reservation locks Store + date + time slot + party-size capacity by default.
- Customer uniqueness scope is Tenant.
- Phone number is optional.
- Fixed TableGroup is configuration; Temporary TableGroup is a single-service resource.
- Rejoined skipped QueueTicket keeps the original number and does not cut the queue by default.
- tenant_id and store_id depend on data level and ownership.

## Data Governance Rules

- Platform, Tenant, Store, Store Operations, Cross-Store Shared, and Audit scopes must be separated.
- Cross-Tenant references are forbidden.
- Store operational data must resolve to one Store.
- Do not require store_id for all data mechanically.
- Key business records use lifecycle state, cancellation, archive, inactive, or soft deletion rather than physical deletion.
- Critical operations require audit.
- Critical repeated operations require idempotency.
- Table locks, table occupancy, queue calling, queue rejoin, temporary TableGroup, seating, and cleaning release require concurrency protection.

## Internationalization Rules

- Store timestamps in UTC.
- Exchange time as ISO8601.
- Display time by Store timezone and locale.
- Store must configure timezone, locale, date_format, time_format, and currency.
- Singapore default: Asia/Singapore, en-SG, DD-MM-YYYY, 24H, SGD.
- Phone numbers use E.164 when present.
- Phone number is not mandatory.
- User-facing text must use i18n keys in later UI/API work.

## OOD and Reuse Check

Before any later development task, answer:

1. Is there an existing similar business object?
2. Is there an existing similar state machine?
3. Is there an existing Rule, Policy, or Validator?
4. Is there a reusable service or capability?
5. Is there common Tenant scope validation?
6. Is there common Store scope validation?
7. Is there common audit support?
8. Is there common i18n or timezone support?
9. Can existing capability be extended instead of duplicated?

Reusable capability candidates:

- Reservation availability rule.
- Queue calling rule.
- Queue rejoin rule.
- Table assignment rule.
- Table lock rule.
- TableGroup validation rule.
- Customer identity rule.
- Store locale rule.
- Tenant scope rule.
- Idempotency rule.
- Audit rule.

## Prohibited Actions

Do not create or modify during governance-only rounds:

- Database tables.
- Executable schema.
- SQL files.
- Flyway migrations.
- Repository.
- Entity.
- Model.
- Controller.
- Service.
- API implementation.
- Vue pages.
- Vue components.
- Configuration files.
- Dependency files.
- Docker files.
- CI/CD files.
- Deployment files.
- Test code.
- Mock business data.
- Source data.

Do not enter:

- Database Design before governance completion.
- API Design before database/design gate.
- UI Design before its design gate.
- Development before all prior gates.

## Acceptance Criteria

A reservation-system task is acceptable only when:

- Business object boundaries are respected.
- Reservation, QueueTicket, WalkIn, CheckIn, Seating, Cleaning, and Turnover are not mixed.
- Tenant and Store scope are explicit.
- State machine transitions are legal or called out as Open.
- Audit requirements are identified.
- Idempotency requirements are identified.
- Concurrency risks are identified.
- I18n requirements are preserved.
- Reuse and OOD checks are performed.
- The task stays within the current phase.

## Next Step Entrance

After this governance startup round, the next allowed phase is:

```text
Database Design
```

The Database Design round must start by reading all governance documents and must not jump directly into migrations, schema files, entities, repositories, APIs, UI, or code implementation.
