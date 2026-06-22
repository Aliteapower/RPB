## Reservation Arrived Direct Seating Application Implementation Result

### 1. Read Documents
- Read `RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_CONTRACT.md` and `RESERVATION_ARRIVED_DIRECT_SEATING_VERTICAL_SLICE_CHECKLIST.md`.
- Read Reservation Create / CheckIn implementation, API, UI validation, and local runtime security reports.
- Read WalkIn Direct Seating application/API reports, Cleaning Complete implementation report, and Store Staff closed-loop smoke report.
- Read App Gate operational handoff, integration checklist, new slice template, permission metadata alignment documents, and `AppGateRequiredPermission.java`.
- Read governance, architecture, reservation-system skill, schema design, and V001/V002 migration constraints.
- Confirmed baseline: Reservation Create passed, Reservation CheckIn passed, Reservation can reach `arrived`, WalkIn Direct Seating has reusable seating/resource/idempotency/audit patterns, and Cleaning Complete can release occupied resources.

### 2. Application Classes Created
- `SeatArrivedReservationCommand`
- `ReservationArrivedDirectSeatingApplicationService`
- `ReservationArrivedDirectSeatingResult`
- `ReservationArrivedDirectSeatingError`
- `ReservationArrivedSeatingRule`
- `ReservationArrivedDirectSeatingApplicationServiceTest`

### 3. Rules / Validators Implemented
- Reused `DefaultStoreAccessPolicy`, table availability/capacity/lock rules, `DefaultTableGroupValidationRule`, `DefaultSeatingResourceValidator`, audit/event/transition/idempotency rules, `ReservationStateMachine`, and `DiningTableStateMachine`.
- Added `ReservationArrivedSeatingRule` for `arrived`-only seating, terminal status rejection, and Reservation-source validation.
- Kept Queue, No-show, Cancellation, Auto-assignment, Cleaning, and Turnover policies out of this slice.

### 4. Command Behavior
- Requires `tenantId`, `storeId`, `reservationId`, `idempotencyKey`, `actorId`, and `actorType`.
- Enforces `tableId XOR tableGroupId`.
- Rejects missing idempotency key before starting idempotency.
- Does not expose or accept QueueTicket, WalkIn, CheckIn timestamp, No-show, Cancellation, Cleaning, or Turnover fields.

### 5. Resource Selection
- Single table path resolves `DiningTable` by Store scope and validates available status, capacity, active lock conflict, and active occupancy.
- TableGroup path resolves `TableGroup`, validates group status/members/capacity, checks group lock/occupancy, and validates each member table availability/lock/occupancy.
- No automatic assignment branch was implemented.

### 6. Table Seating
- Creates `Seating` with source `reservation`.
- Creates active `SeatingResource` for `dining_table`.
- Moves selected table to `occupied` through the existing table state-machine lock-to-occupied validation style.
- Records table occupancy transition evidence as `available -> occupied`.

### 7. TableGroup Seating
- Creates `SeatingResource` for `table_group`.
- Does not mutate fixed TableGroup configuration solely to represent occupancy.
- Updates every member `DiningTable` to `occupied`.
- Records member table transition evidence for each occupied member.

### 8. State Transition
- Fresh success validates and persists Reservation `arrived -> seated`.
- Rejects `confirmed`, `cancelled`, `no_show`, and `completed` as application-level errors.
- `seated` Reservation follows the already-seated path only when matching active seating evidence exists.

### 9. Seating / SeatingResource
- `Seating.status = occupied`.
- `SeatingResource.status = active`.
- `Seating.partySizeSnapshot` comes from Reservation party size.
- Existing `seatings` and `seating_resources` tables are reused; no new seating table was introduced.

### 10. Already Seated
- Same completed idempotency key replays the stored snapshot.
- Different key with matching active Reservation-source Seating and active resource returns `alreadySeated = true`.
- Already-seated path does not create duplicate Seating, SeatingResource, BusinessEvent, StateTransitionLog, AuditLog, or table mutations.
- Seated Reservation without matching active seating returns `RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING`.

### 11. Idempotency
- Action is `seat_arrived_reservation`.
- Completed same hash replays.
- Started same hash returns retry-later.
- Failed same hash requires a new key.
- Same key with different hash returns conflict.
- Fresh success and already-seated success complete idempotency with replayable snapshots.

### 12. Events / Transition / Audit
- Business events: `reservation.seated`, `seating.created`, `table.occupied`.
- State transitions include Reservation `arrived -> seated`, Seating `planned -> occupied`, and table/member-table `available -> occupied`.
- Success audit operation: `reservation.seat`.
- Failure audit operation: `reservation.seat.failed`.

### 13. Success Cases
- Arrived Reservation seated to a single table.
- Arrived Reservation seated to a TableGroup.
- Reservation persisted as `seated`.
- Seating and active SeatingResource created.
- Table and TableGroup member tables become `occupied`.
- Event, transition, audit, and idempotency completion verified.

### 14. Failure Cases
- Covered command selection conflict and missing resource selection.
- Covered Reservation not found and invalid statuses: confirmed, cancelled, no_show, completed, seated without active seating.
- Covered table not found, unavailable, capacity insufficient, and locked.
- Covered TableGroup not found, invalid, member unavailable, and capacity insufficient.
- Covered idempotency conflict, in-progress retry-later, failed-key reuse, event failure, transition failure, audit failure, and persistence failure.

### 15. Tests
- ReservationArrivedDirectSeatingApplicationServiceTest: `mvn -q "-Dtest=ReservationArrivedDirectSeatingApplicationServiceTest" test` exited 0.
- mvn test: `Tests run: 315, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

### 16. Boundary Check
Queue implemented: No
Auto assignment implemented: No
No-show implemented: No
Cancellation implemented: No
Cleaning implemented: No
Turnover implemented: No
New seating table created: No
Controller created: No
API DTO created: No
UI implemented: No
Migration changed: No
Production database touched: No
Seed data inserted: No

### 17. Open Questions
- None blocking for this application-layer slice.
- Future API round still needs approved endpoint, App Gate permission metadata update, and runtime security allowlist work.

### 18. Open Conflicts
- None.

### 19. Next Step Recommendation
- Proceed to a separate Reservation Arrived Direct Seating API contract/implementation round.
- Future API should use `app_key = reservation_queue` and permission `reservation.seat` only after the approved App Gate metadata round.
