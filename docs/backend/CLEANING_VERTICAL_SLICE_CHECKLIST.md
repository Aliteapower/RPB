# Cleaning Vertical Slice Checklist V1

## 1. Phase Boundary

- [x] This round only covers Cleaning Complete application contract.
- [x] This round does not create Java code.
- [x] This round does not create repository implementation.
- [x] This round does not create application service code.
- [x] This round does not create controller code.
- [x] This round does not create REST API.
- [x] This round does not create API DTO.
- [x] This round does not create Vue UI.
- [x] This round does not create migration.
- [x] This round does not create SQL.
- [x] This round does not connect to database.
- [x] This round does not insert seed data or mock runtime data.

## 2. Business Object Boundary

- [x] Cleaning is kept separate from Turnover.
- [x] Cleaning is kept separate from Seating source ownership.
- [x] Cleaning does not create Reservation.
- [x] Cleaning does not create QueueTicket.
- [x] Cleaning does not modify WalkIn identity.
- [x] Cleaning does not depend on Customer identity.
- [x] Cleaning does not implement payment, POS, marketing, or membership.

## 3. Selected Slice

- [x] Selected vertical slice is Cleaning Complete.
- [x] Start Cleaning command is designed.
- [x] Complete Cleaning command is designed.
- [x] The slice supports DiningTable target.
- [x] The slice supports existing TableGroup target.
- [x] `tableId` / `tableGroupId` XOR is required.
- [x] StoreScope validation is required.
- [x] Tenant scope is server-side, not trusted from request body.

## 4. State Boundary

- [x] DiningTable `occupied -> cleaning` is defined.
- [x] DiningTable `cleaning -> available` is defined.
- [x] Direct `occupied -> available` is forbidden.
- [x] `available -> cleaning` is forbidden.
- [x] `inactive -> cleaning` is forbidden.
- [x] Cleaning `none -> pending` is defined.
- [x] Cleaning `pending -> cleaning` is defined.
- [x] Cleaning `cleaning -> completed` is defined.
- [x] Cleaning `completed -> released` is defined.
- [x] Cleaning terminal/replay behavior is defined.
- [x] Seating source must not change.

## 5. Required Ports

- [x] `StoreRepositoryPort` is required.
- [x] `DiningTableRepositoryPort` is required.
- [x] `TableGroupRepositoryPort` is required for TableGroup targets.
- [x] `SeatingRepositoryPort` is required.
- [x] `CleaningRepositoryPort` is required.
- [x] `BusinessEventRepositoryPort` is required.
- [x] `StateTransitionLogRepositoryPort` is required.
- [x] `AuditLogRepositoryPort` is required.
- [x] `IdempotencyRepositoryPort` is required.
- [x] `ReservationRepositoryPort` is not required.
- [x] `QueueTicketRepositoryPort` is not required.
- [x] Broad Turnover BI repository use is not required.

## 6. Required Rules / Policies / Validators

- [x] `StoreAccessPolicy` is required.
- [x] `DiningTableStateMachine` is required.
- [x] `CleaningStateMachine` is required.
- [x] `TableAvailabilityRule` is required.
- [x] `SeatingResourceValidator` is required.
- [x] `CleaningResourceValidator` is required.
- [x] `AuditRule` is required.
- [x] `BusinessEventRule` is required.
- [x] `StateTransitionRule` is required.
- [x] `IdempotencyRule` is required.
- [x] `TableGroupValidationRule` is required for TableGroup targets.
- [x] `ReservationAvailabilityRule` is not required.
- [x] `QueueCallingRule` is not required.

## 7. Audit / Event / Transition

- [x] `cleaning.started` event is defined.
- [x] `cleaning.completed` event is defined.
- [x] `table.cleaning` event is defined.
- [x] `table.available` event is defined.
- [x] BusinessEvent write is required.
- [x] StateTransitionLog write is required.
- [x] AuditLog write is required.
- [x] Audit metadata includes actor, scope, resource, previous/new status, reason/note, and idempotency key.
- [x] No user-facing display copy is hardcoded in the contract.

## 8. Idempotency

- [x] Start Cleaning idempotency action is defined as `start_cleaning`.
- [x] Complete Cleaning idempotency action is defined as `complete_cleaning`.
- [x] Same key + same hash + completed replays result.
- [x] Same key + same hash + in-progress returns retry-later.
- [x] Same key + same hash + failed requires a new key.
- [x] Same key + different hash returns conflict.
- [x] Repeated Complete Cleaning must not write duplicate events.
- [x] Repeated Complete Cleaning must not apply duplicate table status changes.

## 9. Failure Cases

- [x] Store not found is covered.
- [x] Store scope mismatch is covered.
- [x] Seating not found is covered.
- [x] Table not found is covered.
- [x] TableGroup invalid is covered.
- [x] Table not occupied is covered.
- [x] Table already cleaning is covered.
- [x] Table already available is covered.
- [x] Illegal table transition is covered.
- [x] Cleaning not found is covered.
- [x] Cleaning already completed is covered.
- [x] Resource target invalid is covered.
- [x] Idempotency conflict is covered.
- [x] Audit write failure is covered.
- [x] Repository save failure is covered.

## 10. Test Contract

- [x] Start Cleaning success tests are defined.
- [x] Start Cleaning failure tests are defined.
- [x] Start Cleaning idempotency tests are defined.
- [x] Complete Cleaning success tests are defined.
- [x] Complete Cleaning failure tests are defined.
- [x] Complete Cleaning idempotency tests are defined.
- [x] Boundary tests are defined.
- [x] No test code is written in this round.

## 11. Out-of-Scope Confirmation

- [x] Reservation implemented: No.
- [x] Queue implemented: No.
- [x] WalkIn changed: No.
- [x] Seating source changed: No.
- [x] Turnover BI implemented: No.
- [x] Repository implementation created: No.
- [x] Application Service code created: No.
- [x] Controller created: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.
- [x] Database touched: No.
