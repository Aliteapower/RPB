# Cleaning Complete Persistence Implementation Report V1

## 1. Read Documents

- `docs/backend/CLEANING_COMPLETE_APPLICATION_CONTRACT.md`
- `docs/backend/CLEANING_VERTICAL_SLICE_CHECKLIST.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/backend/PERSISTENCE_SKELETON_IMPLEMENTATION_REPORT.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Confirmed previous result:

- WalkIn Direct Seating browser-to-backend validation: Passed.
- Previous `mvn test`: Passed, 92 tests, 0 failures, 0 errors.
- WalkIn / Seating / SeatingResource / BusinessEvent / AuditLog / StateTransitionLog / Idempotency persisted.
- Table status changed to `occupied`.
- Reservation / QueueTicket / Cleaning / Turnover remained untouched before this Cleaning persistence round.

## 2. Implemented Repository Ports

- `CleaningRepositoryPort` is now backed by `CleaningPersistenceAdapter`.
- `SeatingRepositoryPort` now exposes active SeatingResource lookup by `seatingId` for Cleaning resource derivation.
- Existing `DiningTableRepositoryPort`, `TableGroupRepositoryPort`, `BusinessEventRepositoryPort`, `StateTransitionLogRepositoryPort`, `AuditLogRepositoryPort`, and `IdempotencyRepositoryPort` were reused.
- No `ReservationRepositoryPort` or `QueueTicketRepositoryPort` was introduced for this slice.

## 3. Implemented Spring Data Repositories

- Added `CleaningJpaRepository`.
- Added scoped queries for:
  - `findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull`
  - `findFirstByTenantIdAndStoreIdAndSeatingIdAndDeletedAtIsNullOrderByStartedAtDesc`
  - `findActiveByResource`
- Extended `SeatingResourceJpaRepository` with active resource lookup by `tenantId + storeId + seatingId + status`.

## 4. Implemented Mapper Classes

- Added `DefaultCleaningMapper`.
- Preserves Cleaning target XOR:
  - `resource_type = dining_table` requires `table_id` and no `table_group_id`.
  - `resource_type = table_group` requires `table_group_id` and no `table_id`.
- Maps Cleaning status codes through `CleaningStatus`.
- Sets persistence timestamps needed by V1 status constraints:
  - `cleaning/completed/released` get `started_at`.
  - `completed/released` get `completed_at`.
  - `released` gets `released_at`.

## 5. Implemented Persistence Adapters

- Added `CleaningPersistenceAdapter`.
- Supports:
  - scoped lookup by `cleaningId`
  - scoped latest lookup by `seatingId`
  - scoped active lookup by resource target
  - save new or existing Cleaning records
- Extended `SeatingPersistenceAdapter` to derive active resource from `seatingId`.

## 6. Resource Derivation Behavior

- V1 Cleaning resource can be derived from persisted `SeatingResource` by `seatingId`.
- Complete Cleaning can use persisted `Cleaning` by `cleaningId`.
- No duplicated caller-supplied `tableId/tableGroupId` is required for this persistence boundary.
- Explicit resource override remains deferred.

## 7. Cleaning Target Mapping

- `DefaultCleaningMapper` rejects invalid resource target shape.
- Mapper does not decide whether a table can start or complete cleaning.
- Mapper does not access repositories or perform business validation.

## 8. Seating Resource Mapping

- Existing `DefaultSeatingResourceMapper` remains the target mapping owner for SeatingResource.
- Added repository support for active resource lookup by `seatingId`.
- Seating source ownership is unchanged.

## 9. Table / TableGroup Persistence Behavior

- Existing `DiningTablePersistenceAdapter` supports persisting `cleaning` and `available` table statuses.
- Existing `TableGroupPersistenceAdapter` remains available for scoped group lookup and validation support.
- Fixed TableGroup lifecycle is not mutated by this round.
- No TableGroup is auto-created by Cleaning persistence.

## 10. Idempotency Behavior

- Existing `IdempotencyPersistenceAdapter` supports Cleaning action keys such as `start_cleaning` and `complete_cleaning`.
- Request hash and status persistence are unchanged.
- Completed replay, in-progress retry-later, failed-new-key, and hash-conflict decisions remain application-layer behavior.

## 11. Audit / Event / Transition Persistence

- Existing generic persistence adapters are reused:
  - `BusinessEventPersistenceAdapter`
  - `StateTransitionLogPersistenceAdapter`
  - `AuditLogPersistenceAdapter`
- Cleaning event codes remain application-layer inputs:
  - `cleaning.started`
  - `cleaning.completed`
  - `table.cleaning`
  - `table.available`
- This round did not implement application orchestration that writes those records.

## 12. Tests Executed

- Red:
  - `mvn -q '-Dtest=CleaningCompleteMapperImplementationTest,CleaningCompleteRepositoryAdapterTest' test`
  - Failed as expected because Cleaning mapper/repository/adapter and seating resource lookup were missing.
- Green:
  - `mvn -q '-Dtest=CleaningCompleteMapperImplementationTest,CleaningCompleteRepositoryAdapterTest' test`
- Full:
  - `mvn test`

## 13. Test Result

- Target persistence tests: Passed.
- Full Maven test suite: Passed.
- Result: 101 tests, 0 failures, 0 errors, 0 skipped.

## 14. Boundary Check

Application Service created: No  
Controller created: No  
API DTO created: No  
API implemented: No  
UI implemented: No  
Reservation implemented: No  
Queue implemented: No  
Turnover BI implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No

Additional checks:

- No Cleaning Controller was created.
- No Cleaning API DTO was created.
- No Vue file was created or changed.
- No SQL or Flyway migration was created or changed.
- No Reservation / Queue / Turnover implementation was added.

## 15. Files Changed

- `src/main/java/com/rpb/reservation/cleaning/persistence/entity/CleaningEntity.java`
- `src/main/java/com/rpb/reservation/cleaning/persistence/mapper/DefaultCleaningMapper.java`
- `src/main/java/com/rpb/reservation/cleaning/persistence/repository/CleaningJpaRepository.java`
- `src/main/java/com/rpb/reservation/cleaning/persistence/adapter/CleaningPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/seating/application/port/out/SeatingRepositoryPort.java`
- `src/main/java/com/rpb/reservation/seating/persistence/repository/SeatingResourceJpaRepository.java`
- `src/main/java/com/rpb/reservation/seating/persistence/adapter/SeatingPersistenceAdapter.java`
- `src/test/java/com/rpb/reservation/cleaning/persistence/CleaningCompleteMapperImplementationTest.java`
- `src/test/java/com/rpb/reservation/cleaning/persistence/CleaningCompleteRepositoryAdapterTest.java`
- `src/test/java/com/rpb/reservation/walkin/application/WalkInDirectSeatingApplicationServiceTest.java`
- `docs/backend/CLEANING_COMPLETE_PERSISTENCE_IMPLEMENTATION_REPORT.md`

## 16. Open Questions

- Should Cleaning application implementation persist final status as `released` after Complete Cleaning, or keep `completed` and defer release to a later command? Current contract allows Complete Cleaning to move through `completed -> released` in one command.
- Should future DB schema add a partial unique index to enforce one active Cleaning per resource at database level? This round did not change migration.

## 17. Open Conflicts

None.

## 18. Next Step Recommendation

Proceed to `Cleaning Complete Application Implementation`, limited to application service orchestration and tests for Start Cleaning / Complete Cleaning. Do not jump to Controller, API, Vue UI, Reservation, Queue, or Turnover BI.
