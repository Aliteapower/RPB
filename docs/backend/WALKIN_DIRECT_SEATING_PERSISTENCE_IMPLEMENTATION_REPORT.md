# WalkIn Direct Seating Persistence Implementation Report V1

## 1. Read Documents

- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/VERTICAL_SLICE_CHECKLIST.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/ENTITY_MAPPING_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/backend/PERSISTENCE_DESIGN_CHECKLIST.md`
- `docs/backend/PERSISTENCE_SKELETON_IMPLEMENTATION_REPORT.md`
- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/BACKEND_SKELETON_IMPLEMENTATION_REPORT.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_REVIEW_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`

## 2. Implemented Repository Ports

The existing repository ports remain separated from Spring Data repositories.

Implemented persistence adapters for this slice:

- `StoreRepositoryPort`
- `CustomerRepositoryPort`
- `DiningTableRepositoryPort`
- `TableGroupRepositoryPort`
- `TableLockRepositoryPort`
- `WalkInRepositoryPort`
- `SeatingRepositoryPort`
- `BusinessEventRepositoryPort`
- `StateTransitionLogRepositoryPort`
- `AuditLogRepositoryPort`
- `IdempotencyRepositoryPort`

Contract additions required by the slice:

- `TableGroupRepositoryPort.findCandidates(StoreScope, PartySize, BusinessDate)`
- `SeatingRepositoryPort.saveResource(StoreScope, SeatingResource)`

## 3. Implemented Spring Data Repositories

- `StoreJpaRepository`
- `StorePolicyJpaRepository`
- `CustomerJpaRepository`
- `DiningTableJpaRepository`
- `TableGroupJpaRepository`
- `TableGroupMemberJpaRepository`
- `TableLockJpaRepository`
- `WalkInJpaRepository`
- `SeatingJpaRepository`
- `SeatingResourceJpaRepository`
- `BusinessEventJpaRepository`
- `StateTransitionLogJpaRepository`
- `AuditLogJpaRepository`
- `IdempotencyRecordJpaRepository`

Repository implementation notes:

- Spring Data repositories stay in `persistence.repository`.
- Repository ports still do not extend Spring Data.
- No controller, application service, API DTO, or UI code depends on these repositories in this round.

## 4. Implemented Mapper Classes

- `DefaultStoreMapper`
- `DefaultStorePolicyMapper`
- `DefaultCustomerMapper`
- `DefaultDiningTableMapper`
- `DefaultTableGroupMapper`
- `DefaultTableLockMapper`
- `DefaultWalkInMapper`
- `DefaultSeatingMapper`
- `DefaultSeatingResourceMapper`
- `DefaultBusinessEventMapper`
- `DefaultStateTransitionLogMapper`
- `DefaultAuditLogMapper`
- `DefaultIdempotencyMapper`

Mapper implementation notes:

- Existing mapper interfaces remain interfaces.
- Mapper implementations convert only between domain records and JPA entities.
- `SeatingMapper` enforces the persistence source XOR shape when mapping.
- `SeatingResourceMapper` enforces the persistence target XOR shape when mapping.
- `IdempotencyMapper` preserves `request_hash`, status, target, and response snapshot.
- `BusinessEventMapper`, `StateTransitionLogMapper`, and `AuditLogMapper` preserve generic target and metadata fields.
- `Seating` mapping preserves `manual_override_reason_code` and `note`; rule enforcement remains deferred to the application layer.

## 5. Implemented Persistence Adapters

- `StorePersistenceAdapter`
- `CustomerPersistenceAdapter`
- `DiningTablePersistenceAdapter`
- `TableGroupPersistenceAdapter`
- `TableLockPersistenceAdapter`
- `WalkInPersistenceAdapter`
- `SeatingPersistenceAdapter`
- `BusinessEventPersistenceAdapter`
- `StateTransitionLogPersistenceAdapter`
- `AuditLogPersistenceAdapter`
- `IdempotencyPersistenceAdapter`

Adapter implementation notes:

- Adapters implement repository ports only.
- Adapters delegate persistence construction to mappers.
- Adapters do not make state machine decisions.
- Adapters do not create temporary `TableGroup` automatically.
- Adapters do not implement the WalkIn Direct Seating application workflow.

## 6. Tables Touched By This Slice

Primary tables:

- `stores`
- `store_policies`
- `customers`
- `dining_tables`
- `table_groups`
- `table_group_members`
- `table_locks`
- `walk_ins`
- `seatings`
- `seating_resources`
- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`

No migration was changed or created.

## 7. Idempotency Behavior

Implemented persistence support:

- Store-scoped lookup by `tenant_id + store_id + source + action + idempotency_key`.
- Tenant-scoped lookup with `store_id is null`.
- Platform-scoped lookup with `tenant_id is null and store_id is null`.
- Start record with `request_hash` and `started` status.
- Complete record with `completed` status.
- Fail record with `failed` status and a minimally escaped JSON failure snapshot.

Not implemented in this round:

- Same-key different-hash conflict handling.
- Same-key same-hash replay result decision.
- Failed-key retry policy.

Those decisions belong to the later application service / idempotency rule implementation.

## 8. TableLock Behavior

Implemented persistence support:

- Find active non-expired lock by Store scope and resource target.
- Detect active lock conflict by Store scope, resource target, and time.
- Save a lock entity from a domain lock.
- Release a lock by setting status to `released` and preserving lock/source/idempotency fields.

Not implemented in this round:

- External live lock.
- Lock acquisition orchestration.
- Lock conflict business decision.

## 9. Seating Source Mapping

`DefaultSeatingMapper` maps exactly one source:

- `reservation_id` -> `sourceType = reservation`
- `queue_ticket_id` -> `sourceType = queue_ticket`
- `walk_in_id` -> `sourceType = walk_in`

Invalid source shape:

- Zero source ids.
- More than one source id.

Invalid source shape throws `invalid_seating_source`.

## 10. SeatingResource Target Mapping

`DefaultSeatingResourceMapper` maps exactly one resource target:

- `table_id` -> `resourceType = dining_table`
- `table_group_id` -> `resourceType = table_group`

Invalid target shape:

- Zero target ids.
- More than one target id.

Invalid target shape throws `invalid_seating_resource_target`.

## 11. Audit/Event/Transition Persistence

Implemented persistence support:

- Store, Tenant, and Platform scoped append for business events.
- Store, Tenant, and Platform scoped append for audit logs.
- Store, Tenant, and Platform scoped append for state transition logs.
- Store-scoped target lookup for event/audit/transition history.
- Store-scoped latest state transition lookup.

Not implemented in this round:

- Audit requirement rule.
- Event type policy.
- State transition legality.
- Transaction orchestration.

## 12. Tests Executed

Red verification:

- Command: `mvn -q '-Dtest=WalkInDirectSeatingMapperImplementationTest,WalkInDirectSeatingRepositoryAdapterTest' test`
- Result: Failed as expected before implementation because mapper implementations, adapter classes, repository interfaces, entity factories, and expanded persistence fields were missing.

Target green verification:

- Command: `mvn -q '-Dtest=WalkInDirectSeatingMapperImplementationTest,WalkInDirectSeatingRepositoryAdapterTest' test`
- Result: Success.

Full verification:

- Command: `mvn test`
- Result: Success.
- Tests run: 37.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

Known test runtime warnings:

- Mockito dynamic agent warning from the test framework.
- OpenJDK class data sharing warning after dynamic agent attachment.

## 13. Test Result

Passed.

The implementation is validated by mapper and adapter unit tests plus the existing skeleton boundary test suite.

No local PostgreSQL integration test was added in this round because no test database configuration or dependency change was in scope.

## 14. Boundary Check

- Application Service created: No.
- Command Handler created: No.
- Query Handler created: No.
- Controller created: No.
- REST API created: No.
- API DTO / Request / Response created: No.
- Vue UI created: No.
- Migration created: No.
- SQL created: No.
- Seed data inserted: No.
- Mock business data inserted: No.
- Docker / CI / production config changed: No.
- Full Reservation flow implemented: No.
- Full Queue flow implemented: No.
- Cleaning completion implemented: No.
- Turnover calculation implemented: No.

Static checks:

- No `Controller`, `ApplicationService`, `CommandHandler`, `QueryHandler`, API request/response, DTO, or Vue files were found under `src/main/java`.
- No new SQL source file was created.
- No executable `insert into` seed data was added.

## 15. Open Questions

- Should a later persistence integration round add a local PostgreSQL or Testcontainers profile for repository query execution?
- Should failed idempotency records keep a structured failure payload format before the application service round?
- Should override reason/note become explicit application command fields in the next WalkIn Direct Seating application implementation round?

## 16. Open Conflicts

- None.

## 17. Next Step Recommendation

Proceed to:

```text
WalkIn Direct Seating Application Implementation
```

The next round should implement the application service orchestration and rule invocation for this single vertical slice only. It should still not jump to Controller, REST API, Vue UI, full Reservation flow, full Queue flow, Cleaning completion, or Turnover calculation.
