# Reservation Create Persistence Implementation Report

## 1. Read Documents

- `docs/backend/RESERVATION_CREATE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_CONTRACT.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_CHECKLIST.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/ENTITY_MAPPING_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`

Confirmed starting boundary:

- Reservation Create persistence contract completed.
- Create Reservation only.
- No CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, Reservation preassignment, TableLock, API, UI, migration, or database schema changes are part of this round.

## 2. Implemented Repository Ports

Implemented or completed the minimum ports required for Reservation Create persistence:

- `ReservationRepositoryPort`
  - `findById(StoreScope, ReservationId)`
  - `findByCode(StoreScope, ReservationCode)`
  - `existsByReservationCode(StoreScope, ReservationCode)`
  - `findStoreSchedule(StoreScope, BusinessDate, TimeRange)`
  - `existsActiveDuplicate(StoreScope, CustomerId, TimeRange)`
  - `existsActiveConflict(StoreScope, CustomerId, TimeRange)` as compatibility alias
  - `findActiveConflicts(StoreScope, CustomerId, TimeRange)`
  - `findActiveCapacityUsage(StoreScope, BusinessDate, TimeRange)`
  - `save(StoreScope, Reservation)`
- `StorePolicyRepositoryPort`
  - `findByStoreScope(StoreScope)`
- `StoreRepositoryPort`
  - `findByScope(StoreScope)` default alias for existing `findById(StoreScope)`

Existing `CustomerRepositoryPort`, `BusinessEventRepositoryPort`, `StateTransitionLogRepositoryPort`, `AuditLogRepositoryPort`, and `IdempotencyRepositoryPort` were already backed by prior vertical slices and were not duplicated.

## 3. Implemented Spring Data Repositories

Created:

- `ReservationJpaRepository`

The repository provides store-scoped lookup, reservation code existence checks, active duplicate overlap checks, active capacity usage aggregation, schedule overlap lookup, and active conflict lookup.

No Reservation API repository, Queue repository, Seating repository, TableLock repository, or ReservationPreassignment repository was created in this round.

## 4. Implemented Mapper Classes

Created:

- `DefaultReservationMapper`

Mapping coverage:

- `id`
- `tenantId`
- `storeId`
- nullable `customerId`
- `reservationCode`
- `partySize`
- `businessDate`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `status`
- `sourceChannel`
- nullable `cancellationReasonCode`
- nullable `noShowReasonCode`
- nullable `note`
- `createdAt`
- `updatedAt`
- nullable `deletedAt`

The mapper only performs Entity-to-Domain conversion, Domain-to-Entity conversion, status code mapping, and UTC-aware timestamp mapping. It does not decide availability, duplicate behavior, idempotency, audit behavior, or time-range policy.

## 5. Implemented Persistence Adapters

Created:

- `ReservationPersistenceAdapter`

Updated:

- `StorePersistenceAdapter` now also implements `StorePolicyRepositoryPort`.

Adapter behavior:

- Passes tenant/store scope into repository queries.
- Converts `TimeRange` `Instant` values to UTC `OffsetDateTime`.
- Short-circuits duplicate checks for `customerId = null` so anonymous reservations are not treated as the same customer.
- Persists new UUID-assigned `ReservationEntity` through `EntityManager.persist(...)`, matching existing WalkIn and Cleaning persistence style.
- Does not compute capacity limits or derive `reservedEndAt` / `holdUntilAt`.

## 6. Tables Touched By This Slice

Persistence mapping and queries touch:

- `reservations`
- `store_policies`
- `stores` through existing store lookup behavior

Reused existing infrastructure ports can later append to:

- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`

Tables intentionally not used by this Reservation Create persistence slice:

- `queue_tickets`
- `seatings`
- `seating_resources`
- `table_locks`
- `reservation_preassignments`
- `cleanings`
- `turnovers`

## 7. Capacity Usage Query Behavior

`ReservationJpaRepository.sumActiveOverlappingPartySize(...)` returns the aggregate active party-size usage for:

- same `tenant_id`
- same `store_id`
- same `business_date`
- `status in ('confirmed', 'arrived', 'seated')`
- `deleted_at is null`
- overlap condition:

```text
existing.reserved_start_at < requestedEnd
and existing.reserved_end_at > requestedStart
```

The adapter exposes this as:

```text
ReservationRepositoryPort.findActiveCapacityUsage(...)
```

This returns a numeric party-size total only. The repository does not compute capacity availability and does not apply a capacity limit. V001 has no explicit `reservation_capacity_limit`, so the application layer must later compare usage plus requested `partySize` against StorePolicy or a temporary application-level fallback. That fallback remains temporary and is not implemented in persistence.

## 8. Duplicate Query Behavior

`ReservationJpaRepository.existsActiveDuplicate(...)` checks:

- same `tenant_id`
- same `store_id`
- same `customer_id`
- `status in ('confirmed', 'arrived', 'seated')`
- `deleted_at is null`
- arbitrary time overlap using:

```text
existing.reserved_start_at < requestedEnd
and existing.reserved_end_at > requestedStart
```

If `customerId` is null, `ReservationPersistenceAdapter.existsActiveDuplicate(...)` returns `false` without querying by null customer id. This preserves the rule that anonymous/no-phone customers must not be collapsed into one duplicate identity.

## 9. Reservation Code Behavior

The port supports:

```text
existsByReservationCode(StoreScope, ReservationCode)
```

The backing query is scoped by:

- `tenant_id`
- `store_id`
- `reservation_code`
- `deleted_at is null`

The migration already provides an active unique index for reservation code:

```text
ux_reservations_code_active
```

The application layer should still use `existsByReservationCode(...)` before save and handle any save-time unique conflict as a persistence failure or conflict in a later application-service round.

The adapter wraps `DataIntegrityViolationException` and JPA `PersistenceException` from save-time writes as:

```text
reservation_persistence_write_failed
```

## 10. StorePolicy Behavior

Created `StorePolicyRepositoryPort.findByStoreScope(StoreScope)`.

`StorePersistenceAdapter` delegates it to the existing current-policy query using the current UTC timestamp.

If no current policy exists, the port returns `Optional.empty()`. It does not invent defaults. Reservation capacity fallback remains an application-layer concern for the next round.

## 11. Tests Executed

Red:

```text
mvn -q '-Dtest=ReservationCreatePersistenceTest' test
```

Initial result:

- Failed at compilation as expected because Reservation Create persistence mapper, repository, adapter, and StorePolicy port did not exist yet.

Green:

```text
mvn -q '-Dtest=ReservationCreatePersistenceTest' test
```

Result:

- Passed.

Full verification:

```text
mvn test
```

First full run:

- Failed because two existing WalkIn/Cleaning boundary tests had a stale Vue file whitelist that did not include the previously approved `StoreStaffHomePage.vue`.

Minimal compatibility fix:

- Updated existing boundary test whitelists to include `src/pages/StoreStaffHomePage.vue`, which is documented in the Store Staff operational handoff and demo readiness review.
- No UI source files were created or modified.

Final result:

- `mvn test` passed.
- Tests run: 172.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

## 12. Test Result

Result:

```text
BUILD SUCCESS
Tests run: 172, Failures: 0, Errors: 0, Skipped: 0
```

## 13. Boundary Check

CheckIn implemented: No  
Queue implemented: No  
Seating implemented: No  
No-show implemented: No  
Cancellation implemented: No  
Table assignment implemented: No  
Reservation preassignment used: No  
Table lock used: No  
Application Service created: No  
Controller created: No  
API implemented: No  
UI implemented: No  
Migration changed: No  
SQL created: No  
Database schema changed: No  
Production database touched: No  
Seed data inserted: No  
Runtime mock data inserted: No  

## 14. Files Changed

Reservation persistence:

- `src/main/java/com/rpb/reservation/reservation/domain/Reservation.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/entity/ReservationEntity.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/mapper/DefaultReservationMapper.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationJpaRepository.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`
- `src/test/java/com/rpb/reservation/reservation/persistence/ReservationCreatePersistenceTest.java`

Store policy port support:

- `src/main/java/com/rpb/reservation/store/application/port/out/StorePolicyRepositoryPort.java`
- `src/main/java/com/rpb/reservation/store/application/port/out/StoreRepositoryPort.java`
- `src/main/java/com/rpb/reservation/store/persistence/adapter/StorePersistenceAdapter.java`

Existing boundary test compatibility:

- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`

Report:

- `docs/backend/RESERVATION_CREATE_PERSISTENCE_IMPLEMENTATION_REPORT.md`

## 15. Open Questions

- What application-level fallback capacity limit should be used when V001 has no explicit reservation capacity policy field?
- Should Reservation save-time unique violations be mapped to a dedicated persistence exception type in the next application-service round?
- Should Reservation Create application tests include a database-backed scenario for active capacity overlap, or keep this round's query coverage at repository adapter/unit level until the application implementation exists?

## 16. Open Conflicts

None.

The only unrelated issue found was stale boundary-test expectations around `StoreStaffHomePage.vue`. This page was already documented as part of the previously approved Store Staff closed loop and was added to the test whitelist without modifying UI code.

## 17. Next Step Recommendation

Proceed to:

```text
Reservation Create Application Implementation
```

Recommended scope:

- Implement only the application service for Create Reservation.
- Use the persistence ports added in this round.
- Apply idempotency action `create_reservation`.
- Resolve or create customer identity.
- Compare active capacity usage against StorePolicy or temporary application-level fallback.
- Write `reservation.created`, `reservation.confirmed`, state transition, and audit.
- Do not implement Reservation API, UI, CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, TableLock, or ReservationPreassignment.
