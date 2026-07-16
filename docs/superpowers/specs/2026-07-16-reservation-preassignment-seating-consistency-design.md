# Reservation Preassignment And Seating Consistency Design

## Purpose

Fix the inconsistency where an employee can preassign a table that the operational model already considers unavailable, then receive `TABLE_NOT_AVAILABLE` when seating the same reservation.

The confirmed business meaning is:

- A table preassignment gives one Reservation exclusive ownership for its reserved time range.
- The preassignment is planned ownership, not physical occupancy and not an all-day Table lock.
- At CheckIn and Seating, the Reservation's own active preassignment is the authoritative intended resource.
- Seating may convert that ownership into physical occupancy only when no other active Seating, lock, cleaning flow, inactive state, or invalid grouping blocks the Table.
- A successful Seating consumes the active preassignment in the same transaction.

## Production Evidence And Root Cause

The reproduced Reservation `R-20260716-2727` had an active preassignment for `room 3`, but `room 3` still had an active Seating from the prior Reservation `R-20260715-6464` and its physical status was `occupied`. The failed Seating was correctly audited as `reservation.seat.failed` with `table_not_available`.

The inconsistency originates earlier in the assignment flow:

1. The assignable-table query excludes only `inactive`; it still returns `locked`, `reserved`, `occupied`, and `cleaning` rows.
2. The assignment command repeats the same permissive rule and can persist a preassignment for such a Table.
3. The Seating flow later applies the stricter physical-availability rule and rejects the selected Table.
4. A successful Seating does not currently consume the active preassignment, so the planned ownership can outlive its purpose.

This is not fixed by ignoring `occupied`. Doing so could create two active Seating records for one Table and violate the core occupancy invariant.

## Confirmed Scope

In scope:

- Make assignment query and command validation use the same current physical Table eligibility rule.
- Keep preassignment conflicts time-bound with half-open overlap semantics: `[reservedStartAt, reservedEndAt)`.
- Recognize the Reservation's own active preassignment during direct Seating.
- Permit the preassigned Table when it is physically `available`, or when it is `reserved` and that reserved ownership belongs to the same Reservation.
- Continue rejecting `occupied`, `cleaning`, `inactive`, actively locked, invalidly grouped, or actively occupied Tables.
- Release the matching active preassignment atomically after Seating has acquired the same resource.
- Preserve existing Tenant/Store isolation, capacity checks, CheckIn rules, Reservation state transitions, idempotency, audit, business events, and concurrency protection.
- Keep public and employee-created Reservations behaviorally identical.

Out of scope:

- Automatically completing a prior Seating because its Reservation end time has passed.
- Force-seating over an active occupancy.
- Holding a physical Table from assignment time until a future Reservation starts.
- Adding Table Switch or manual override behavior.
- Changing Reservation arrival-time policy.
- Changing public APIs, request/response payloads, permissions, or database schema.
- Repairing historical active Seating records automatically.

## Approaches Considered

### 1. Time-range ownership with physical-state reconciliation — chosen

Keep `ReservationPreassignment` as the time-bound ownership record. Tighten assignment eligibility, make Seating ownership-aware, and consume the preassignment on successful Seating.

Benefits:

- Matches the confirmed reservation-time ownership model.
- Preserves the distinction between planned Reservation intent and active Seating occupancy.
- Prevents assignment and Seating from applying contradictory Table rules.
- Does not block a Table for an entire day when a Reservation is many hours away.
- Preserves the one-active-occupancy invariant.

### 2. Set the physical Table to `reserved` immediately on assignment

Rejected because a future Reservation could make the Table unavailable from assignment time, even when earlier valid service is still possible. The global Table status has no time range, while the preassignment does.

### 3. Let an owning preassignment override `occupied`

Rejected because preassignment is planned ownership, not proof that the current physical Seating ended. This would permit two active parties on one Table and hide stale operational records.

## Architecture And Module Boundaries

### Reservation table-assignment use case

`ReservationTableAssignmentApplicationService` remains responsible for Reservation-scoped planned ownership. Its query and command paths must call the same reusable eligibility rule.

The rule evaluates:

- Same Tenant and Store.
- Table exists and is not deleted.
- Reservation remains `confirmed` and unassigned.
- Table status is currently `available`.
- Party size is within Table capacity.
- No active Table lock exists.
- No active SeatingResource occupancy exists.
- No other active ReservationPreassignment overlaps the Reservation's `[start,end)` range.

The list path filters ineligible rows. The command path revalidates all authoritative conditions after scoped row locks and immediately before saving, so a stale dialog cannot bypass the rule.

This tightens the previous design, which deliberately ignored current operational states for future reservations. The Product Owner has now confirmed that post-booking manual assignment is from the currently empty/eligible table set.

### Reservation direct-Seating use case

`ReservationArrivedDirectSeatingApplicationService` remains responsible for converting an arrived Reservation into Seating and occupancy.

Resource resolution becomes ownership-aware:

1. Load the Reservation's active preassignment before final Table availability validation.
2. If a preassignment exists, require the requested resource type and ID to match it.
3. Validate capacity, Store scope, locks, active occupancy, grouping, and physical status.
4. Treat `available` as seatable.
5. Treat `reserved` as seatable only when the matching active preassignment belongs to this Reservation.
6. Never treat `occupied`, `cleaning`, or `inactive` as seatable through ownership.
7. Create Seating and SeatingResource, transition the Table to `occupied`, change Reservation to `seated`, then release the matching preassignment in the same transaction.

The current `reservation.arrived`, `reservation.seated`, `seating.created`, and `table.occupied` events remain unchanged. The completed Seating audit records the consumed preassignment ID/resource and release outcome. No extra public event contract is introduced.

### Preassignment persistence boundary

Extend `ReservationPreassignmentRepositoryPort` with a narrow, scoped release operation rather than reconstructing and saving a new domain record.

The persistence implementation updates the matching active, non-deleted preassignment by Tenant, Store, Reservation, resource type, and resource ID:

- `status = 'released'`
- `released_at = now`
- `updated_at = now`

The operation must report whether exactly one active row was released. Zero rows is accepted only when an idempotent Seating replay proves the Reservation is already seated on the same resource; otherwise it is a consistency failure. The existing schema already contains `status` and `released_at`, so no migration is required.

### Frontend boundary

The existing assignment and Seating dialogs remain focused components. No new endpoint or payload is required.

- The assignment dialog naturally stops showing operationally unavailable Tables because the backend list is authoritative.
- The Seating dialog continues to show the Reservation's assigned Table.
- A failed physical-availability recheck remains visible as `TABLE_NOT_AVAILABLE`; the client must not reinterpret it as success.
- Existing i18n keys and error mapping remain unchanged unless a missing localized message is discovered by tests.

## Data Flow

### Assignment

```text
Employee opens 指定桌号
  -> load scoped confirmed Reservation
  -> load current Table rows
  -> filter by physical eligibility + capacity + time-overlap ownership
  -> employee selects one Table
  -> lock Reservation then Table
  -> repeat physical and overlap checks
  -> save active ReservationPreassignment
  -> audit/event/idempotency complete
  -> Reservation remains confirmed
```

### Arrival and Seating

```text
Employee checks in Reservation
  -> Reservation becomes arrived
Employee confirms 入桌 using assigned Table
  -> load own active preassignment
  -> require selected Table matches ownership
  -> recheck physical eligibility and active occupancy
  -> create Seating + SeatingResource
  -> transition Table to occupied
  -> transition Reservation to seated
  -> release matching preassignment
  -> audit/event/idempotency complete
```

Any failure rolls back Seating, Table, Reservation, and preassignment mutations together.

## Error Handling

Existing stable errors remain authoritative:

| Condition | Result |
|---|---|
| Assignment Table is not currently `available` | `TABLE_NOT_AVAILABLE` |
| Another Seating actively occupies the Table | `TABLE_NOT_AVAILABLE` |
| Active Table lock exists | Existing lock-conflict error |
| Selected Seating resource differs from own preassignment | `TABLE_NOT_AVAILABLE` / existing group error |
| `reserved` Table belongs to another Reservation | `TABLE_NOT_AVAILABLE` |
| Own active preassignment points to `available` or own `reserved` Table | Seating proceeds |
| Preassignment release persistence fails | Whole transaction fails with repository error |
| Completed idempotent Seating command is repeated | Existing completed snapshot is replayed; no second release |

The fix does not convert stale occupancy into success. Employees must complete the prior Seating and Cleaning lifecycle through the existing operational flow before the Table becomes assignable again.

## Concurrency And Transaction Rules

- Assignment keeps the existing lock order: Reservation first, Table second.
- Assignment query is advisory; the command always repeats physical and overlap checks under the transaction.
- Seating keeps its existing idempotency boundary and active-occupancy checks.
- Preassignment release uses a conditional update on `status = 'active'` and matching scoped resource ownership.
- Table occupancy remains protected by the existing active SeatingResource uniqueness and Table state transition rules.
- No code path may release another Reservation's preassignment.
- `[start,end)` overlap behavior is unchanged, so boundary-touching Reservations do not conflict.

## Audit And Observability

Successful assignment continues to write:

- `reservation.table_assigned` business event.
- `reservation.table_assign` audit entry.
- Completed idempotency snapshot.

Successful Seating continues to write its existing Reservation, Seating, and Table events and transitions. Its audit metadata additionally records:

- Preassignment ID.
- Preassignment resource type and ID.
- Previous preassignment status `active`.
- New preassignment status `released`.
- Release timestamp.

Failed assignment and Seating retain stable failure codes. Logs and audits must not expose customer PII beyond existing masked projections.

## Testing Strategy

Follow red-green-refactor and use the PostgreSQL runtime referenced by `target/local-postgres-current.txt` for integration tests.

### Assignment application tests

- Assignable list excludes `locked`, `reserved`, `occupied`, `cleaning`, and `inactive` Tables.
- Assignable list includes only `available`, capacity-compatible, same-Store Tables.
- Command rejects a Table that became unavailable after the list was loaded.
- Command rejects active Seating occupancy and active lock conflicts.
- Overlapping other preassignments conflict.
- Boundary-touching preassignments do not conflict.
- Public and employee-created Reservations use identical rules.
- Successful assignment leaves Reservation status `confirmed`.
- Assignment replay remains idempotent and does not create a second preassignment/event/audit.

### Direct-Seating application tests

- Arrived Reservation seats successfully on its own active preassigned `available` Table.
- Own preassigned `reserved` Table seats successfully when no physical blocker exists.
- A different requested Table is rejected while an active preassignment exists.
- Another Reservation's preassignment is rejected.
- `occupied`, `cleaning`, and `inactive` remain rejected even for the owning Reservation.
- Active lock and capacity failures remain unchanged.
- Successful Seating releases exactly the matching active preassignment.
- A release failure rolls back Seating, Table occupancy, Reservation state, events, transitions, audit, and idempotency completion.
- A repeated completed command replays without a second release.
- Reservation transitions remain `arrived -> seated`; assignment still leaves `confirmed` unchanged.

### Persistence and integration tests

- Scoped release updates only the matching active row and sets `released_at`.
- Cross-Tenant, cross-Store, wrong Reservation, wrong resource, deleted, and already released rows are untouched.
- Assignment API never returns an operationally unavailable Table.
- End-to-end public booking -> employee assignment -> CheckIn -> Seating produces one active SeatingResource, Table `occupied`, Reservation `seated`, and preassignment `released`.
- Concurrent assignment and Seating attempts preserve one-owner/one-occupancy invariants.
- Audit metadata and existing business events are present.

### Frontend regression

- Assigned Table remains displayed on the reservation card and in share templates before Seating.
- Seating dialog submits the assigned Table ID.
- Stable `TABLE_NOT_AVAILABLE` error rendering remains intact for a real physical blocker.
- Existing WhatsApp, WeChat, system share, copy link, cancellation, no-show, arrival, and queue actions remain unchanged.

## Acceptance Criteria

- Employees cannot assign a Table that the operational model currently marks locked, reserved, occupied, cleaning, inactive, or actively occupied.
- A confirmed public or employee-created Reservation can own one eligible Table for its exact reserved time range.
- Assignment leaves the Reservation status `confirmed` and creates no Seating or occupancy.
- After CheckIn, the Reservation can seat on its own preassigned physically eligible Table without `TABLE_NOT_AVAILABLE`.
- Successful Seating changes Reservation to `seated`, creates one active SeatingResource, changes Table to `occupied`, and changes the matching preassignment to `released` atomically.
- A real active occupancy is never overwritten, even if the requesting Reservation has a preassignment.
- Tenant/Store scope, capacity, time boundaries, idempotency, audit, events, and concurrency protections remain effective.
- No API contract, permission, schema, sharing template, or unrelated workflow changes.
