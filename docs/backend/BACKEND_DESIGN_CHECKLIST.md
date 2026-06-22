# Backend Design Checklist V1

## Purpose

Use this checklist before entering Backend Domain Skeleton Implementation. It verifies that the backend domain model design preserves governance, database, migration, OOD, state machine, audit, idempotency, and phase boundaries.

## Required Inputs

- [ ] `docs/governance/BUSINESS_GLOSSARY.md` was read.
- [ ] `docs/governance/BUSINESS_RULES.md` was read.
- [ ] `docs/governance/DATA_STANDARD.md` was read.
- [ ] `docs/governance/DATA_CHECKLIST.md` was read.
- [ ] `docs/architecture/ARCHITECTURE.md` was read.
- [ ] `docs/skills/reservation-system/SKILL_OVERVIEW.md` was read.
- [ ] `docs/skills/reservation-system/SKILL.md` was read.
- [ ] `docs/database/DATABASE_DESIGN.md` was read.
- [ ] `docs/database/ERD.md` was read.
- [ ] `docs/database/DATA_MODEL_CHECKLIST.md` was read.
- [ ] `docs/database/SCHEMA_DESIGN.md` was read.
- [ ] `docs/database/MIGRATION_PLAN.md` was read.
- [ ] `docs/database/SCHEMA_REVIEW_CHECKLIST.md` was read.
- [ ] `docs/database/MIGRATION_REVIEW_REPORT.md` was read.
- [ ] `docs/database/MIGRATION_VALIDATION_REPORT.md` was read.
- [ ] `docs/database/migrations/V001__reservation_platform_bootstrap.sql` was read.
- [ ] Migration validation success was confirmed from the validation report.

## Phase Safety

- [ ] Work stays inside backend domain model design documentation.
- [ ] No Java code is created.
- [ ] No Entity is created.
- [ ] No Repository is created.
- [ ] No Service is created.
- [ ] No Controller is created.
- [ ] No DTO is created.
- [ ] No Mapper is created.
- [ ] No API is implemented.
- [ ] No Vue page or component is created.
- [ ] No test code is created.
- [ ] No mock data is created.
- [ ] No seed data is created.
- [ ] No configuration file is changed.
- [ ] No Docker, CI/CD, dependency, or Spring Boot structure file is created.
- [ ] No Flyway Migration is created or changed.
- [ ] No SQL file is created or changed.
- [ ] No database migration is run.
- [ ] No database connection is opened.
- [ ] No production database is touched.

## Business Boundary Checks

- [ ] Reservation and QueueTicket are not mixed.
- [ ] Reservation does not create QueueTicket by default.
- [ ] Reservation is modeled as advance Store + date + time slot + party-size capacity intent.
- [ ] QueueTicket is modeled as waiting after arrival.
- [ ] WalkIn and QueueTicket are not mixed.
- [ ] WalkIn can go directly to Seating.
- [ ] WalkIn creates QueueTicket only when waiting is needed.
- [ ] CheckIn is not designed as a primary entity.
- [ ] CheckIn is represented by BusinessEvent, StateTransitionLog, and AuditLog.
- [ ] Seating and Reservation are not mixed.
- [ ] Seating creates occupancy and requires exactly one source.
- [ ] Cleaning and Turnover are not mixed.
- [ ] Cleaning owns the resource status flow.
- [ ] Turnover owns the result/metric.
- [ ] DiningTable and TableGroup are not mixed.
- [ ] Customer and Member are not mixed.

## Domain Object Coverage

- [ ] Tenant is designed.
- [ ] Store is designed.
- [ ] StorePolicy is designed.
- [ ] Area is designed.
- [ ] DiningTable is designed.
- [ ] TableGroup is designed.
- [ ] TableGroupMember is designed.
- [ ] TableLock is designed.
- [ ] Customer is designed.
- [ ] Reservation is designed.
- [ ] ReservationPreassignment is designed.
- [ ] QueueGroup is designed.
- [ ] QueueTicket is designed.
- [ ] WalkIn is designed.
- [ ] Seating is designed.
- [ ] SeatingResource is designed.
- [ ] Cleaning is designed.
- [ ] Turnover is designed.
- [ ] BusinessEvent is designed.
- [ ] StateTransitionLog is designed.
- [ ] AuditLog is designed.
- [ ] IdempotencyRecord is designed.
- [ ] ReasonCode is designed.
- [ ] I18nMessage is designed.

## State Machine Checks

- [ ] Reservation state machine is centralized.
- [ ] Reservation initial, terminal, legal, and illegal transitions are defined.
- [ ] QueueTicket state machine is centralized.
- [ ] QueueTicket call hold duration and rejoin policy are defined.
- [ ] QueueTicket rejoin does not cut queue by default.
- [ ] DiningTable state machine is centralized.
- [ ] DiningTable `reserved` and `occupied` are separated.
- [ ] DiningTable `locked` expiry is defined.
- [ ] DiningTable `cleaning` must complete before `available`.
- [ ] DiningTable `inactive` blocks Reservation, Queue, Seating, and recommendation.
- [ ] Seating status codes are designed.
- [ ] Cleaning status codes are designed.
- [ ] Turnover status codes are designed.
- [ ] TableGroup status codes are designed.
- [ ] TableLock status codes are designed.
- [ ] IdempotencyRecord status codes are designed.
- [ ] Status logic is not scattered into Controller or page code.
- [ ] AuditLog requirement is identified for critical transitions.
- [ ] StateTransitionLog requirement is identified for stateful transitions.
- [ ] Idempotency requirement is identified for repeatable commands.

## Rule / Policy / Validator Checks

- [ ] Rule components are independent from Controller/API DTOs.
- [ ] Policy components are independent from page/UI decisions.
- [ ] Validator components are reusable and domain-scoped.
- [ ] TenantScope is unified.
- [ ] StoreScope is unified.
- [ ] ScopeGuard prevents cross-Tenant and cross-Store references.
- [ ] ReservationAvailabilityRule is designed.
- [ ] ReservationDuplicateRule is designed.
- [ ] ReservationHoldPolicy is designed.
- [ ] ReservationCancellationPolicy is designed.
- [ ] NoShowPolicy is designed.
- [ ] QueueGroupPolicy is designed.
- [ ] QueueCallingRule is designed.
- [ ] QueueRejoinRule is designed.
- [ ] QueueExpiryPolicy is designed.
- [ ] QueueOrderingPolicy is designed.
- [ ] TableAvailabilityRule is designed.
- [ ] TableLockRule is designed.
- [ ] TableAssignmentRule is designed.
- [ ] TableCapacityRule is designed.
- [ ] SeatingSourceValidator is designed.
- [ ] SeatingResourceValidator is designed.
- [ ] TableGroupValidationRule is designed.
- [ ] FixedTableGroupPolicy is designed.
- [ ] TemporaryTableGroupPolicy is designed.
- [ ] TableGroupMemberRule is designed.
- [ ] CustomerIdentityRule is designed.
- [ ] CustomerPhoneRule is designed.
- [ ] AnonymousCustomerPolicy is designed.
- [ ] TenantCustomerUniquenessRule is designed.
- [ ] StoreLocaleRule is designed.
- [ ] StoreTimeZoneRule is designed.
- [ ] DateTimePolicy is designed.
- [ ] I18nMessageRule is designed.
- [ ] CurrencyPolicy is designed.
- [ ] AuditRule is designed.
- [ ] BusinessEventRule is designed.
- [ ] StateTransitionRule is designed.
- [ ] IdempotencyRule is designed.

## DTO / Entity / Domain Object Checks

- [ ] API DTO is not treated as Domain Object.
- [ ] Persistence Entity is not treated as full Domain Object.
- [ ] Command is defined as write-operation intent.
- [ ] Query is defined as read-operation condition.
- [ ] Domain Object owns business meaning and invariants.
- [ ] Entity maps persistence only.
- [ ] DTO expresses API contract only.
- [ ] Future flow remains Controller -> Command/Query -> Application Service -> Domain Rule/Policy -> Repository -> Entity.

## Key Flow Checks

- [ ] Reservation Create passes through TenantScope.
- [ ] Reservation Create passes through StoreScope.
- [ ] Reservation Create uses StorePolicy.
- [ ] Reservation Create uses ReservationAvailabilityRule.
- [ ] Reservation Create uses ReservationDuplicateRule.
- [ ] Reservation Create uses IdempotencyRule.
- [ ] Reservation Create uses AuditRule.
- [ ] Customer Arrive / CheckIn uses Reservation StateMachine.
- [ ] Customer Arrive / CheckIn records BusinessEvent.
- [ ] Customer Arrive / CheckIn records StateTransitionLog.
- [ ] Customer Arrive / CheckIn records AuditLog.
- [ ] Queue Ticket Create uses QueueGroupPolicy.
- [ ] Queue Ticket Create uses QueueOrderingPolicy.
- [ ] Queue Ticket Create uses CustomerIdentityRule.
- [ ] Queue Ticket Create uses IdempotencyRule.
- [ ] Queue Ticket Create uses AuditRule.
- [ ] Queue Call / Skip / Rejoin uses QueueTicket StateMachine.
- [ ] Queue Call / Skip / Rejoin uses QueueCallingRule.
- [ ] Queue Call / Skip / Rejoin uses QueueRejoinRule.
- [ ] Queue Call / Skip / Rejoin records StateTransitionLog.
- [ ] Queue Call / Skip / Rejoin records AuditLog.
- [ ] Seating uses SeatingSourceValidator.
- [ ] Seating uses TableAvailabilityRule.
- [ ] Seating uses TableAssignmentRule.
- [ ] Seating uses TableLockRule.
- [ ] Seating uses SeatingResourceValidator.
- [ ] Seating records AuditLog.
- [ ] Seating records StateTransitionLog.
- [ ] Cleaning Complete uses Cleaning StateMachine.
- [ ] Cleaning Complete uses DiningTable StateMachine.
- [ ] Cleaning Complete feeds Turnover recording.
- [ ] Cleaning Complete records AuditLog.
- [ ] Cleaning Complete records StateTransitionLog.

## OOD and Reuse Checks

- [ ] Business objects are designed before implementation classes.
- [ ] State machines are reusable.
- [ ] Rules, policies, and validators are reusable.
- [ ] Tenant and Store scope checks are not repeated ad hoc in every flow.
- [ ] Audit is unified.
- [ ] Idempotency is unified.
- [ ] Store timezone and locale are unified.
- [ ] E.164 phone validation is centralized.
- [ ] Table lock and availability rules are centralized.
- [ ] Queue ordering is centralized.
- [ ] Reservation availability is centralized.
- [ ] TableGroup validity is centralized.
- [ ] Backend package suggestion is domain-based, not page-based.
- [ ] Backend package suggestion is not Controller-centric.
- [ ] Service design avoids one large service owning all workflows.

## Final Gate

- [ ] Actual modified files are limited to `docs/backend/DOMAIN_MODEL_DESIGN.md`, `docs/backend/STATE_MACHINE_DESIGN.md`, `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`, and `docs/backend/BACKEND_DESIGN_CHECKLIST.md`.
- [ ] No business code was created.
- [ ] No migration was changed.
- [ ] No database was touched.
- [ ] Next phase may be Backend Domain Skeleton Implementation only after this checklist is accepted.
