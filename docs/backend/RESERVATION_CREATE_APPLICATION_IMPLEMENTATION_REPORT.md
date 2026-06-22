# Reservation Create Application Implementation Report

## 1. Read Documents

- `docs/backend/RESERVATION_CREATE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_CONTRACT.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_CHECKLIST.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`

## 2. Implementation Scope

Implemented only the Create Reservation application orchestration.

The implementation creates a confirmed Reservation, resolves or creates Customer identity, derives missing `reservedEndAt` from StorePolicy, computes `holdUntilAt`, checks duplicate and capacity rules, writes BusinessEvent, StateTransitionLog, AuditLog, and completes idempotency.

## 3. Created / Updated Application Files

- `src/main/java/com/rpb/reservation/reservation/application/ReservationCreateError.java`
- `src/main/java/com/rpb/reservation/reservation/application/ReservationCreateResult.java`
- `src/main/java/com/rpb/reservation/reservation/application/command/CreateReservationCommand.java`
- `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationAvailabilityRule.java`
- `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationCodePolicy.java`
- `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationDuplicateRule.java`
- `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationHoldPolicy.java`
- `src/main/java/com/rpb/reservation/reservation/application/rule/ReservationTimeRangeRule.java`
- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationCreateApplicationService.java`

## 4. Command

Implemented `CreateReservationCommand` with:

- `tenantId`
- `storeId`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `customerId`
- `customerName`
- `customerNickname`
- `phoneE164`
- `note`
- `idempotencyKey`
- `actorId`
- `actorType`
- `reservationCode`
- `source`
- `reasonCode`

The command does not include `queueTicketId`, `seatingId`, `tableId`, `tableGroupId`, `checkInAt`, or `noShowAt`.

## 5. Application Service

Implemented `ReservationCreateApplicationService`.

Responsibilities:

- Validate command.
- Build `StoreScope`.
- Check idempotency.
- Validate store access.
- Resolve StorePolicy with V1 fallback.
- Resolve or create Customer.
- Validate party size and reservation time range.
- Derive missing `reservedEndAt` from expected dining duration.
- Compute `holdUntilAt`.
- Check duplicate active reservation.
- Check V1 capacity availability.
- Generate or validate reservation code.
- Save confirmed Reservation.
- Write `reservation.created` and `reservation.confirmed`.
- Write `reservation.confirm` state transition.
- Write `reservation.create` audit log.
- Complete or fail idempotency.

Non-responsibilities:

- API parsing.
- Controller handling.
- UI message handling.
- SQL or migration changes.
- CheckIn, Queue, Seating, No-show, Cancellation, or Table assignment.

## 6. Repository Ports Used

- `StoreRepositoryPort`
- `StorePolicyRepositoryPort`
- `CustomerRepositoryPort`
- `ReservationRepositoryPort`
- `BusinessEventRepositoryPort`
- `StateTransitionLogRepositoryPort`
- `AuditLogRepositoryPort`
- `IdempotencyRepositoryPort`

No Queue, Seating, Cleaning, TableLock, or Table assignment repository port is used by this slice.

## 7. Rules / Policies / Validators

Implemented minimal application-scope helpers:

- `ReservationTimeRangeRule`
- `ReservationAvailabilityRule`
- `ReservationDuplicateRule`
- `ReservationHoldPolicy`
- `ReservationCodePolicy`

Reused existing shared rules:

- `DefaultStoreAccessPolicy`
- `DefaultIdempotencyRule`
- `DefaultBusinessEventRule`
- `DefaultStateTransitionRule`
- `DefaultAuditRule`

## 8. StorePolicy / Time Behavior

- If `reservedEndAt` is missing, it is derived as `reservedStartAt + expectedDiningMinutes`.
- `expectedDiningMinutes` comes from StorePolicy.
- If StorePolicy is missing, V1 fallback is 90 minutes.
- `holdUntilAt` is computed as `reservedStartAt + reservationHoldMinutes`.
- If StorePolicy is missing, V1 fallback is 15 minutes.
- Business date is derived from Store timezone.
- Stored command values remain UTC `Instant`.

## 9. Capacity Behavior

V1 uses the Product Owner confirmed fallback:

- capacity limit: 50 guests per overlapping Store time window
- current usage from `ReservationRepositoryPort.findActiveCapacityUsage`
- reject when `currentUsage + partySize > 50`

No schema change or capacity table was introduced.

## 10. State / Event / Audit

Reservation status:

- Create success: `confirmed`

Events:

- `reservation.created`
- `reservation.confirmed`

State transition:

- target: `reservation`
- from: `none`
- to: `confirmed`
- transition code: `reservation.confirm`

Audit:

- operation code: `reservation.create`
- target type: `reservation`
- metadata includes reservation, customer, time, hold, status, source, reason, note, and idempotency key.

## 11. Idempotency

Action:

- `create_reservation`

Behavior:

- Completed same hash: replay stored reservation snapshot.
- Started same hash: return retry-later result.
- Failed same hash: require new idempotency key.
- Same key different hash: return idempotency conflict.
- Missing key: return `MISSING_IDEMPOTENCY_KEY`.

Repeated successful retries do not create duplicate Reservation records.

## 12. Tests

Created:

- `src/test/java/com/rpb/reservation/reservation/application/ReservationCreateApplicationServiceTest.java`

Covered:

- create reservation with existing customer
- create temporary no-phone customer
- create phone customer when no existing phone match
- derive missing `reservedEndAt`
- reject fallback capacity overflow
- reject duplicate active reservation
- completed idempotency replay
- in-progress idempotency retry-later
- failed idempotency key requires new key
- idempotency hash conflict
- invalid phone
- invalid party size
- invalid time range
- past start time
- customer not found
- reservation code conflict
- business event failure
- state transition failure
- audit failure
- reservation save failure
- boundary: no Queue, Seating, TableLock, CheckIn, No-show, API DTO, Controller, or UI

## 13. Commands Executed

- `mvn -q '-Dtest=ReservationCreateApplicationServiceTest' test`
- `mvn test`

## 14. Test Result

- Targeted test: Passed.
- Full test: Passed.
- Full test result: 194 tests, 0 failures, 0 errors.

## 15. Boundary Check

CheckIn implemented: No
Queue implemented: No
Seating implemented: No
No-show implemented: No
Cancellation implemented: No
Table assignment implemented: No
TableLock created: No
Controller created: No
API DTO created: No
API implemented: No
UI implemented: No
Migration changed: No
SQL created: No
Database touched: No
Seed data inserted: No
Production database touched: No

## 16. Open Questions

- Should future API replay require storing a richer response snapshot at the idempotency persistence port level?
- Should Reservation capacity become StorePolicy-configurable in a later schema round instead of using the V1 fallback of 50?
- Should temporary reservation customer names be persisted after the Customer domain grows name/nickname fields?

## 17. Open Conflicts

None.

## 18. Next Step Recommendation

Proceed to `Reservation Create API Contract Design` only after Product Owner accepts the application boundary. The next round should still avoid CheckIn, Queue, Seating, No-show, Cancellation, table assignment, and UI.
