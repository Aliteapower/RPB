# Cleaning Complete Application Implementation Report V1

## 1. Read Documents

- `docs/backend/CLEANING_COMPLETE_APPLICATION_CONTRACT.md`
- `docs/backend/CLEANING_VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/CLEANING_COMPLETE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Confirmed previous result:

- WalkIn Direct Seating browser-to-backend validation: Passed.
- Previous `mvn test`: Passed, 101 tests, 0 failures, 0 errors.
- WalkIn / Seating / SeatingResource / BusinessEvent / AuditLog / StateTransitionLog / Idempotency persisted.
- Table status changed to `occupied`.
- Reservation / QueueTicket / Cleaning / Turnover remained untouched before the Cleaning vertical slice started.

## 2. Implemented Commands

- `StartCleaningCommand`
- `CompleteCleaningCommand`

Command boundaries:

- `StartCleaningCommand` derives the target resource from `seatingId`.
- `CompleteCleaningCommand` derives the target resource from `cleaningId`.
- Neither command accepts caller-supplied `tableId` or `tableGroupId`.
- `tenantId`, `storeId`, `actorId`, `actorType`, and `idempotencyKey` remain application inputs from the server-side boundary.
- Commands are application commands, not API DTOs.

## 3. Implemented Application Service

- `CleaningApplicationService`
- `CleaningApplicationResult`
- `CleaningApplicationError`

Implemented orchestration:

- Start Cleaning from occupied Seating.
- Complete Cleaning from existing Cleaning.
- Validate Store scope and access.
- Derive resource target from persisted SeatingResource or Cleaning.
- Validate DiningTable or TableGroup ownership and state.
- Transition tables from `occupied` to `cleaning`.
- Transition tables from `cleaning` to `available`.
- Create or update Cleaning.
- Update Seating and SeatingResource evidence when cleaning starts.
- Append BusinessEvent, StateTransitionLog, and AuditLog records.
- Complete or fail IdempotencyRecord.

The service is annotated with `@Transactional` and keeps all mutations for each command inside one application transaction.

StartCleaning behavior:

- Accept `StartCleaningCommand`.
- Validate command and Store scope.
- Start idempotency under action `start_cleaning`.
- Load Seating by `seatingId`.
- Derive target resource from active SeatingResource.
- Reject missing or invalid target resource.
- Reject tables that are not `occupied`.
- Reject an already active Cleaning for the same resource.
- Create Cleaning in `cleaning` status.
- Move DiningTable or TableGroup member tables from `occupied` to `cleaning`.
- Write `cleaning.started`, `table.cleaning`, state transitions, audit, and idempotency completion.

CompleteCleaning behavior:

- Accept `CompleteCleaningCommand`.
- Validate command and Store scope.
- Start idempotency under action `complete_cleaning`.
- Load Cleaning by `cleaningId`.
- Derive target resource from Cleaning.
- Reject missing, invalid, completed, or released Cleaning.
- Reject tables that are not `cleaning`.
- Move DiningTable or TableGroup member tables from `cleaning` to `available`.
- Persist Cleaning as `released` after validating the `cleaning -> completed -> released` lifecycle.
- Write `cleaning.completed`, `table.available`, state transitions, audit, and idempotency completion.

## 4. Rules / Validators Implemented / Used

- `DefaultStoreAccessPolicy`
- `DiningTableStateMachine`
- `CleaningStateMachine`
- `DefaultSeatingResourceValidator`
- `CleaningResourceValidator`
- `DefaultTableGroupValidationRule`
- `DefaultAuditRule`
- `DefaultBusinessEventRule`
- `DefaultStateTransitionRule`
- `DefaultIdempotencyRule`

Implementation notes:

- `CleaningResourceValidator` is a minimal Cleaning-specific validator for resource target shape.
- `CleaningResourceValidator` rejects unknown resource types and null target ids.
- Reservation availability and Queue calling rules were not introduced.
- Turnover BI rules were not introduced.

## 5. Repository Ports Used

- `StoreRepositoryPort`
- `DiningTableRepositoryPort`
- `TableGroupRepositoryPort`
- `SeatingRepositoryPort`
- `CleaningRepositoryPort`
- `BusinessEventRepositoryPort`
- `StateTransitionLogRepositoryPort`
- `AuditLogRepositoryPort`
- `IdempotencyRepositoryPort`

No `ReservationRepositoryPort` or `QueueTicketRepositoryPort` was introduced for this application slice.

## 6. Transaction Boundary

Start Cleaning transaction:

1. Validate command structure.
2. Resolve Store scope and idempotency.
3. Validate Store scope and access.
4. Load Seating by `seatingId`.
5. Derive active SeatingResource.
6. Validate DiningTable or TableGroup target.
7. Reject active Cleaning conflict.
8. Save Cleaning as `cleaning`.
9. Transition table resource from `occupied` to `cleaning`.
10. Mark SeatingResource as released evidence for the occupied resource.
11. Mark Seating as `cleaning_triggered`.
12. Append events, transitions, and audit.
13. Complete idempotency.

Complete Cleaning transaction:

1. Validate command structure.
2. Resolve Store scope and idempotency.
3. Validate Store scope and access.
4. Load Cleaning by `cleaningId`.
5. Derive resource from Cleaning.
6. Validate DiningTable or TableGroup target.
7. Transition table resource from `cleaning` to `available`.
8. Save Cleaning as `released`.
9. Append events, transitions, and audit.
10. Complete idempotency.

## 7. Idempotency Behavior

Implemented actions:

- `start_cleaning`
- `complete_cleaning`

Behavior:

- New key starts an idempotency record before mutation.
- Same key + same request hash + `completed` replays stored result without mutation.
- Same key + same request hash + `started` returns retry-later intent.
- Same key + same request hash + `failed` requires a new idempotency key.
- Same key + different request hash returns idempotency conflict.
- Accepted command failures after `started` mark idempotency as `failed`.
- Successful command completes idempotency with target type `cleaning` and a result snapshot.

Repeated Complete Cleaning with the same completed key replays the prior result and does not write duplicate events.

## 8. State Transitions

Table transitions:

- `occupied -> cleaning`
- `cleaning -> available`

Cleaning transitions:

- Start Cleaning creates Cleaning in `cleaning` status.
- Complete Cleaning validates `cleaning -> completed -> released` and persists `released`.

Rejected transitions include:

- Starting Cleaning from an available or inactive table.
- Completing Cleaning when the table is not in `cleaning`.
- Completing an already completed or released Cleaning.

## 9. Audit / Event / Transition Writing

Business events:

- `cleaning.started`
- `table.cleaning`
- `cleaning.completed`
- `table.available`

State transition codes:

- `cleaning.start`
- `dining_table.cleaning`
- `table_group.cleaning`
- `cleaning.complete`
- `dining_table.available`
- `table_group.available`

Audit operation codes:

- `cleaning.start.completed`
- `cleaning.complete.completed`
- `cleaning.start.failed`
- `cleaning.complete.failed`

Audit metadata includes the Cleaning resource, Seating reference, previous and new table status, reason/note, actor, and failure reason where applicable.

## 10. Failure Handling

Covered application errors:

- `invalid_command`
- `store_not_found`
- `store_scope_mismatch`
- `store_access_denied`
- `seating_not_found`
- `seating_resource_not_found`
- `table_not_found`
- `invalid_table_group`
- `table_not_occupied`
- `cleaning_already_active`
- `cleaning_not_found`
- `cleaning_already_completed`
- `table_not_cleaning`
- `resource_target_invalid`
- `idempotency_conflict`
- `command_in_progress`
- `failed_idempotency_requires_new_key`
- `illegal_state_transition`
- `audit_write_failed`
- `business_event_write_failed`
- `state_transition_write_failed`
- `repository_save_failed`

Failure behavior:

- Validation errors before idempotency start do not create idempotency records.
- Failures after idempotency start mark the record as `failed`.
- Failure audit is attempted after accepted command failures.
- Repository/runtime failures are mapped to `repository_save_failed`.
- No failure path creates Reservation, QueueTicket, Turnover BI, API, or UI artifacts.

Success cases covered:

- Start Cleaning from `seatingId` with DiningTable resource.
- Start Cleaning from `seatingId` with TableGroup resource.
- Complete Cleaning by `cleaningId` with DiningTable resource.
- Complete Cleaning by `cleaningId` with TableGroup resource.
- Completed idempotency replay returns stored result without mutation.

## 11. Tests Executed

Red:

- `mvn -q '-Dtest=CleaningApplicationServiceTest' test`
- Failed as expected before implementation because Cleaning application command/service/result/error classes were missing.

Green:

- `mvn -q '-Dtest=CleaningApplicationServiceTest' test`

Full:

- `mvn test`

## 12. Test Result

- Target application tests: Passed.
- Full Maven test suite: Passed.
- Result: 120 tests, 0 failures, 0 errors, 0 skipped.

## 13. Boundary Check

Reservation implemented: No  
Queue implemented: No  
WalkIn changed: No  
Seating source changed: No  
Turnover BI implemented: No  
Repository implementation created: No  
Controller created: No  
API DTO created: No  
API implemented: No  
UI implemented: No  
Migration changed: No  
SQL created: No  
Production database touched: No  
Seed data inserted: No

Additional checks:

- No Cleaning Controller was created.
- No Cleaning API DTO was created.
- No Vue file was created or changed.
- No SQL or Flyway migration was created or changed.
- No Reservation / Queue / Turnover implementation was added.
- New Cleaning application tests use fake/in-memory ports and do not require a real database.
- The full existing Maven suite includes prior local test-profile integration tests; no production database was touched.

## 14. Files Changed

- `src/main/java/com/rpb/reservation/cleaning/application/command/StartCleaningCommand.java`
- `src/main/java/com/rpb/reservation/cleaning/application/command/CompleteCleaningCommand.java`
- `src/main/java/com/rpb/reservation/cleaning/application/CleaningApplicationError.java`
- `src/main/java/com/rpb/reservation/cleaning/application/CleaningApplicationResult.java`
- `src/main/java/com/rpb/reservation/cleaning/application/validator/CleaningResourceValidator.java`
- `src/main/java/com/rpb/reservation/cleaning/application/service/CleaningApplicationService.java`
- `src/test/java/com/rpb/reservation/cleaning/application/CleaningApplicationServiceTest.java`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`

## 15. Open Questions

- Should Cleaning API expose separate `start` and `complete` endpoints, or a combined endpoint for local runtime convenience?
- Should Complete Cleaning persist an intermediate `completed` status before `released`, or keep V1 as one transactional release action?
- Should future Turnover use Cleaning completion events directly or create a separate async projection?

## 16. Open Conflicts

None.

## 17. Next Step Recommendation

Proceed to `Cleaning Complete API Contract Design`, keeping the next round scoped to Cleaning only and without implementing Vue UI, Reservation, Queue, or Turnover BI.
