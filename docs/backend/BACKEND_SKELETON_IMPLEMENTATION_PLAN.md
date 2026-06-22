# Backend Skeleton Implementation Plan V1

## Purpose

This plan records the Backend Domain Skeleton Implementation branch decision for the current workspace.

Current result:

- Java / Spring Boot backend project found: No.
- Java source root found: No.
- Build file found: No.
- Domain skeleton code created: No.

Per the current round rules, because no backend project exists, this round must not create a Spring Boot project, build file, Controller, API, configuration file, Repository, or Java domain skeleton by force.

## Inputs Read

Governance documents:

- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/skills/reservation-system/SKILL.md`

Database / schema / migration documents:

- `docs/database/DATABASE_DESIGN.md`
- `docs/database/ERD.md`
- `docs/database/DATA_MODEL_CHECKLIST.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_PLAN.md`
- `docs/database/SCHEMA_REVIEW_CHECKLIST.md`
- `docs/database/MIGRATION_REVIEW_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `docs/database/migrations/V001__reservation_platform_bootstrap.sql`

Backend design documents:

- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/BACKEND_DESIGN_CHECKLIST.md`

Migration validation result treated as confirmed:

- PostgreSQL 17.10.
- Migration execution completed successfully.
- 24 tables.
- 55 check constraints.
- 69 foreign keys.
- 107 indexes.
- `pgcrypto` enabled.

## Governance Sync

`docs/governance/BUSINESS_RULES.md` was synchronized before any Java implementation attempt.

The following five historical Open Questions are now confirmed:

1. Reservation hold duration is Store configurable; V1 default is 15 minutes after reservation time.
2. Queue call-hold duration is Store configurable; V1 default is 3 minutes after call.
3. Expected dining duration is Store configurable; V1 default is 90 minutes.
4. Same Tenant + Store + Customer + time slot cannot have multiple active confirmed / arrived / seated Reservations.
5. V1 QueueGroup is Store + Party Size Group by default: 1-2, 3-4, 5-6, and 7+.

## Backend Project Check

Checked for backend project signals:

- `pom.xml`: not found.
- `build.gradle`: not found.
- `build.gradle.kts`: not found.
- `settings.gradle`: not found.
- `settings.gradle.kts`: not found.
- `mvnw`: not found.
- `gradlew`: not found.
- `src/main/java`: not found.
- `backend/src/main/java`: not found.
- `server/src/main/java`: not found.
- Existing `.java` files: none found.

Conclusion:

- Existing backend project found: No.
- Backend skeleton implementation cannot proceed in this workspace without first creating or adding an approved backend project structure in a later phase.

## Why Java Code Was Not Created

The current round explicitly says that if no backend project exists:

```text
Do not create a complete Spring Boot project.
Do not create build.gradle / pom.xml.
Do not create Controller / API.
Do not create configuration files.
Only output docs/backend/BACKEND_SKELETON_IMPLEMENTATION_PLAN.md and stop.
```

Therefore this round stops at this plan and does not create Java skeleton files.

## Future Skeleton Scope After Backend Project Exists

When a Java / Spring Boot backend project already exists, the next allowed implementation pass should create domain skeleton only.

Suggested package shape, using the existing root package:

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

Do not use page, screen, mobile, admin-ui, controller-centric, or API-centric packages.

## Future Domain Object Skeletons

Create only domain skeletons with identity, core fields, constructor or factory placeholder, simple invariant placeholder, status getter, and domain intent method placeholder.

Required domain objects:

- Tenant
- Store
- StorePolicy
- Area
- DiningTable
- TableGroup
- TableGroupMember
- TableLock
- Customer
- Reservation
- ReservationPreassignment
- QueueGroup
- QueueTicket
- WalkIn
- Seating
- SeatingResource
- Cleaning
- Turnover
- BusinessEvent
- StateTransitionLog
- AuditLog
- IdempotencyRecord
- ReasonCode
- I18nMessage

Do not create `CheckInEntity`, `Member`, `Payment`, `Marketing`, POS integration, or full CRUD.

## Future Value Object / Enum Skeletons

IDs and scopes:

- TenantId
- StoreId
- TableId
- CustomerId
- ReservationId
- QueueTicketId
- WalkInId
- SeatingId

Statuses:

- ReservationStatus
- QueueTicketStatus
- DiningTableStatus
- SeatingStatus
- CleaningStatus
- TurnoverStatus
- TableGroupStatus
- TableLockStatus
- IdempotencyStatus

Common value objects:

- PartySize
- CapacityRange
- BusinessDate
- TimeRange
- E164Phone
- I18nKey
- ReasonCodeValue
- IdempotencyKey

These value objects should express basic invariants only. They must not implement full business workflows.

## Future State Machine Skeletons

Create centralized state machine skeletons:

- ReservationStateMachine
- QueueTicketStateMachine
- DiningTableStateMachine
- SeatingStateMachine
- CleaningStateMachine
- TurnoverStateMachine
- TableLockStateMachine
- IdempotencyStateMachine

Each skeleton should include:

- Allowed transition definition.
- `canTransition` placeholder.
- `validateTransition` placeholder.
- Transition result placeholder.
- Audit / StateTransitionLog hook placeholder.

State transition checks must not be scattered into Domain Object, Service, Controller, DTO, or UI code.

## Future Rule / Policy / Validator Skeletons

Scope:

- TenantScope
- StoreScope
- ScopeGuard
- TenantAccessPolicy
- StoreAccessPolicy

Reservation:

- ReservationAvailabilityRule
- ReservationDuplicateRule
- ReservationHoldPolicy
- ReservationCancellationPolicy
- NoShowPolicy

Queue:

- QueueGroupPolicy
- QueueCallingRule
- QueueRejoinRule
- QueueExpiryPolicy
- QueueOrderingPolicy

Table / Seating:

- TableAvailabilityRule
- TableLockRule
- TableAssignmentRule
- TableCapacityRule
- SeatingSourceValidator
- SeatingResourceValidator

TableGroup:

- TableGroupValidationRule
- FixedTableGroupPolicy
- TemporaryTableGroupPolicy
- TableGroupMemberRule

Customer:

- CustomerIdentityRule
- CustomerPhoneRule
- AnonymousCustomerPolicy
- TenantCustomerUniquenessRule

I18n / Time:

- StoreLocaleRule
- StoreTimeZoneRule
- DateTimePolicy
- I18nMessageRule
- CurrencyPolicy

Audit / Idempotency:

- AuditRule
- BusinessEventRule
- StateTransitionRule
- IdempotencyRule

Each skeleton should show:

- Purpose.
- Input type placeholder.
- Output type placeholder.
- Failure result placeholder.
- No database query implementation.
- No Controller dependency.
- No UI dependency.

## Command / Query Boundary

Future implementation may create domain-layer command placeholders only when needed:

- CreateReservationCommand
- CreateQueueTicketCommand
- SeatPartyCommand
- CompleteCleaningCommand

Do not create API DTOs in this skeleton round:

- No `CreateReservationRequest`.
- No `ReservationResponse`.
- No `QueueTicketResponse`.

Keep boundaries:

```text
Command = write intent
Query = read condition
DTO = API input/output
Domain Object = business object
Persistence Entity = storage mapping
```

## TDD Note

The current workspace has no backend project and this branch forbids creating one. No production Java code is created, so no test-first cycle is possible or needed in this round.

When a backend project exists and Java skeleton code is allowed, the implementation pass should either:

- include approved test files in scope and use test-first development, or
- explicitly document why only compile/type checks are allowed for pure skeleton code.

## Compile / Validation Plan

Current round:

- Compile executed: No.
- Reason: no Java project, no build file, and no Java source root exists.

Future round after backend project exists:

- Run the project's existing compile command only.
- Do not run integration tests requiring a real database.
- Do not start the full service.
- Do not insert seed, mock, or business data.

## Boundary Check

Current round result:

- Java code created: No.
- Controller created: No.
- Repository created: No.
- API implemented: No.
- UI implemented: No.
- Migration changed: No.
- SQL created: No.
- Database touched: No.
- Seed data inserted: No.
- Mock data inserted: No.
- CheckInEntity created: No.
- Member created: No.
- Payment created: No.
- Marketing created: No.
- POS integration created: No.

## Next Step Recommendation

Before domain skeleton Java code can be created, add or approve a minimal backend project structure in a dedicated backend project setup round.

Recommended next round:

```text
Backend Project Structure Setup
```

That setup round should decide the root Java package, build tool, Spring Boot baseline, source roots, and test policy. It must still avoid Controller/API/UI/business workflow implementation unless explicitly approved by that round.
