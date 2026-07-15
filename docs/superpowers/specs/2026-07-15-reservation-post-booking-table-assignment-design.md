# Reservation Post-Booking Table Assignment Design

## Purpose

Allow tenant employees to assign a table after a reservation has already been created. This closes the current gap for public bookings, which are created without a table, and also gives the same consistent workflow to staff-created reservations.

After assignment, the reservation list and every existing customer-sharing channel must show the selected table code without changing the reservation arrival, queue, seating, cancellation, or completion flows.

## Confirmed Scope

In scope:

- Show a `指定桌号` action for every reservation that is `confirmed` and has no active table preassignment, regardless of whether its source is public booking or staff creation.
- Let the employee choose one dining table belonging to the same Store.
- Only offer tables whose configured capacity includes the reservation party size and which have no active preassignment overlapping the reservation time range.
- Create an active `ReservationPreassignment`; do not create Seating, occupancy, CheckIn, QueueTicket, Cleaning, or Turnover data.
- Refresh the reservation card after success so it displays the selected table code and removes the assignment action.
- Make the selected table code available immediately through the existing staff share-info API and public reservation-share page.
- Preserve Tenant/Store scope, App Gate permissions, idempotency, audit, and concurrency protections.

Out of scope:

- Changing or releasing an existing preassignment.
- Assigning a fixed or temporary TableGroup.
- Automatically selecting or recommending a table.
- Assigning a table after CheckIn or after the Reservation leaves `confirmed`.
- Changing public-booking creation, Reservation state transitions, Seating, Queue, or Table Switch behavior.
- Sending a message automatically; employees continue using the existing WhatsApp, WeChat, system-share, and copy-link actions.

## Existing Capabilities To Reuse

The codebase already provides the required domain and read-side foundations:

- `ReservationPreassignment` is the planned table-resource intent and is explicitly separate from final Seating occupancy.
- `reservation_preassignments` stores active preassignments with Tenant and Store scope.
- `ReservationPreassignmentRepositoryPort` and its persistence adapter already read and save assignments.
- `ReservationTodayView` already projects `assignedResourceType`, `assignedResourceId`, and `assignedResourceCode`.
- `ReservationShareInfoJdbcRepository` and `ReservationPublicShareJdbcRepository` already resolve the active preassignment to `tableCode`.
- `ReservationShareTemplateRenderer` already supports `{{tableCode}}`; the existing runtime text resolver supplies the pending label only when no table code exists.
- `TableResourcePicker` establishes the mobile-first visual language for choosing tables by area and capacity.

No new primary business object or notification integration is required.

## Approaches Considered

### 1. Dedicated post-booking assignment operation — chosen

Add a reservation-scoped assignable-table query and a reservation-scoped assignment command. Keep the operation inside Reservation Management while consuming table lookup and preassignment ports.

Benefits:

- Matches the business meaning of optional preassignment.
- Keeps creation, assignment, CheckIn, and Seating as separate operations.
- Supports focused validation, audit, idempotency, and concurrency controls.
- Has the smallest effect on existing workflows.

### 2. Reuse the reservation-create endpoint

Rejected because creation is already complete. Replaying or extending the create endpoint to mutate an existing reservation would mix two commands, complicate idempotency, and risk duplicate reservations.

### 3. Add a general reservation-edit endpoint

Rejected for this increment because it would introduce unrelated edit semantics for time, party size, customer details, and lifecycle behavior. The requested capability is narrower and already has a dedicated domain boundary.

## Architecture And Module Boundaries

The feature remains in the existing modular monolith.

### Reservation application module

Add a focused table-assignment use case with:

- A command containing Tenant, Store, Reservation, selected Table, actor, source, and idempotency key.
- A result and stable error enum.
- A rule that permits assignment only when the Reservation is `confirmed` and currently unassigned.
- An application service that owns transaction ordering, validation, preassignment creation, business-event recording, audit, and idempotency completion.

This service must not own table configuration or mutate table operational status. An active preassignment is planned intent; it must not change the table to `occupied` or create Seating.

### Table module

Expose reservation-specific table eligibility through a narrow read boundary. The query uses the Reservation's own party size, business date, and exact reserved time range rather than trusting arbitrary client values.

Eligibility for this increment means:

- Resource type is `dining_table`.
- Tenant and Store match the Reservation.
- Table exists, is not deleted, and is not `inactive`.
- Table capacity includes the Reservation party size.
- No other active ReservationPreassignment for a `confirmed` or `arrived` Reservation overlaps the target Reservation's time range.

Current-day operational states such as `occupied` or `cleaning` do not permanently make a future time slot ineligible. The query is for planned reservation-time availability, not immediate Seating availability. The write command repeats all authoritative checks before saving.

### API module

Expose two protected endpoints:

```text
GET /api/v1/stores/{storeId}/reservations/{reservationId}/assignable-tables
PUT /api/v1/stores/{storeId}/reservations/{reservationId}/table-assignment
```

The query response contains only the fields needed by the dialog:

```json
{
  "success": true,
  "reservationId": "...",
  "partySize": 2,
  "tables": [
    {
      "tableId": "...",
      "tableCode": "A01",
      "displayName": "A01",
      "areaName": "大厅",
      "capacityMin": 1,
      "capacityMax": 4
    }
  ]
}
```

The command requires `Idempotency-Key`:

```json
{
  "tableId": "..."
}
```

Successful response:

```json
{
  "success": true,
  "reservationId": "...",
  "tableId": "...",
  "tableCode": "A01",
  "assignmentStatus": "active",
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

The write endpoint uses the existing `reservation.create` permission because it extends reservation preparation. The read endpoint requires the existing `table.view` permission. Allowed roles remain `tenant_admin`, `store_manager`, and `store_staff`, with current Store access checks.

The frontend shows the action only when the current App Gate entry grants both permissions. This avoids offering a dialog that the employee cannot populate, while preserving the backend as the authority for every request.

## Data Design

Reuse `reservation_preassignments`; do not add a new business table.

Add a Flyway migration with a partial unique index enforcing at most one active, non-deleted preassignment per scoped reservation:

```text
(tenant_id, store_id, reservation_id)
where status = 'active' and deleted_at is null
```

The migration must not modify Reservation status or existing valid assignment data. Migration tests must verify the scope columns and partial predicate. Database review must explicitly check existing-data compatibility before runtime application.

No template schema or template-variable migration is needed because `{{tableCode}}` already exists in the supported catalog and the active assignment is already joined by both share repositories.

## Command Flow And Concurrency

The assignment transaction runs in this order:

1. Validate command fields and actor Store access.
2. Resolve and lock the Reservation row inside its Tenant/Store scope.
3. Require Reservation status `confirmed`.
4. Read the current active assignment.
5. If the same table is already assigned, return an idempotent success.
6. If another table is already assigned, reject without mutation.
7. Resolve and lock the selected dining-table row in the same Store.
8. Validate active status and capacity.
9. Recheck overlapping active preassignments for the exact Reservation time range, excluding the current Reservation.
10. Save one active `ReservationPreassignment`.
11. Append a `reservation.table_assigned` business event and a `reservation.table_assign` audit log with actor, role/type, Store scope, Reservation ID, Table ID/code, source, and idempotency key.
12. Complete the idempotency record and return the assigned table snapshot.

Locking the Reservation first serializes competing assignments for one Reservation. Locking the Table second serializes competing assignments for the same table. All callers use the same lock order to avoid deadlocks. The partial unique index is the final integrity guard for duplicate active assignments.

This operation does not emit a Reservation state-transition log because the Reservation remains `confirmed`.

## Error Handling

Stable failures include:

| Condition | HTTP | Code |
|---|---:|---|
| Missing or invalid command/idempotency key | 400 | `INVALID_COMMAND` / `MISSING_IDEMPOTENCY_KEY` |
| Actor or App Gate permission denied | 403 | `FORBIDDEN` |
| Store scope mismatch | 403 | `STORE_SCOPE_MISMATCH` |
| Reservation not found | 404 | `RESERVATION_NOT_FOUND` |
| Table not found | 404 | `TABLE_NOT_FOUND` |
| Reservation is not `confirmed` | 409 | `RESERVATION_NOT_ASSIGNABLE` |
| Reservation already has another active assignment | 409 | `RESERVATION_ALREADY_ASSIGNED` |
| Capacity does not fit | 409 | `TABLE_CAPACITY_INSUFFICIENT` |
| Table inactive or overlapping assignment detected | 409 | `TABLE_NOT_AVAILABLE` |
| Same idempotency key with different payload | 409 | `IDEMPOTENCY_CONFLICT` |
| Another identical command is still running | 409/retryable | `COMMAND_IN_PROGRESS` |
| Persistence, audit, or event write failure | 500 | Stable layer-specific error |

The dialog keeps the user's selection when a recoverable error occurs and offers refresh/retry. A conflict triggers a fresh eligible-table query so a table taken by another employee disappears.

## Frontend Design

### Reservation card

`ReservationTodayListItem` shows `指定桌号` only when:

- `item.status === 'confirmed'`;
- there is no `assignedResourceId`;
- there is no current Seating resource.

The action is independent of reservation source so public and staff-created bookings behave consistently. Existing arrival, no-show, cancellation, and sharing actions remain unchanged.

### Assignment dialog

Add a focused `ReservationTableAssignmentDialog` component. It receives the Store and selected Reservation, loads the reservation-scoped eligible tables, groups them by area, and emits a completed assignment. It supports loading, empty, error, conflict-refresh, submitting, and success states.

The dialog is single-table only. It does not expose temporary grouping or table switching. The employee sees table code, area, and capacity before confirming.

### Refresh and share behavior

After assignment:

- Reload the today view so `assignedResourceCode` appears in the card.
- Hide `指定桌号` because the reservation is no longer unassigned.
- Clear any cached `ReservationShareInfo` held by the list item when its assignment fields change.
- Subsequent WhatsApp, WeChat, system-share, copy-link, and public H5 reads render the new table code through the existing `{{tableCode}}` variable.

The feature does not automatically open a share channel after assignment. The employee remains in control of which existing channel to use.

Platform defaults and seeded Store templates already contain `{{tableCode}}`. A tenant-authored custom template that intentionally omits this variable remains unchanged; the feature supplies the assigned value to the template rather than rewriting tenant wording.

All new employee-facing text uses i18n keys for `zh-CN` and `en-SG`.

## Testing Strategy

Follow test-driven development.

Backend application tests:

- Confirmed, unassigned Reservation can receive an eligible dining table.
- Public and staff-created Reservations behave identically.
- Non-confirmed Reservations are rejected.
- Existing same-table assignment replays successfully.
- Existing different-table assignment is rejected.
- Cross-Tenant and cross-Store resources are rejected.
- Inactive, missing, and capacity-mismatched tables are rejected.
- Overlapping active assignments are rejected while non-overlapping assignments remain eligible.
- No QueueTicket, CheckIn, Seating, occupancy, Cleaning, or Reservation status write occurs.
- Event, audit, and idempotency records contain the assignment context.

API and security tests:

- Query and command contracts, role checks, App Gate permissions, Store scope, missing idempotency, error mapping, and replay behavior.
- Customer/public actors cannot call employee assignment APIs.

Persistence and migration tests:

- Exact time-range overlap query and exclusion of terminal/deleted records.
- Row-lock methods keep Tenant/Store scope.
- Partial unique index prevents two active assignments for one Reservation.
- Integration test concurrent attempts for the same Reservation and for the same Table.

Frontend tests:

- Button visibility is based on confirmed/unassigned state, not reservation source.
- Dialog renders eligible tables and all request states.
- Successful assignment reloads the list and invalidates stale share data.
- Assigned table code is rendered in the reservation card.
- Existing action visibility and behavior remain unchanged.

Share regression tests:

- Staff share text renders assigned `tableCode`.
- Public share response and H5 render assigned `tableCode` with `tablePending=false`.
- Unassigned reservations retain the existing pending-table wording.

Verification includes focused Maven tests, the frontend production build, and local PostgreSQL integration/runtime checks using the instance identified by `target/local-postgres-current.txt`.

## Acceptance Criteria

- Every confirmed, unassigned Reservation card offers `指定桌号`, regardless of source.
- The dialog offers only same-Store, capacity-compatible dining tables without overlapping Reservation preassignments.
- Concurrent assignment attempts cannot produce duplicate or conflicting active assignments.
- Successful assignment immediately updates the card to show the table code and removes the action.
- Existing WhatsApp, WeChat, system-share, copy-link, and public H5 content render the assigned table code through configured templates containing `{{tableCode}}`; default and seeded templates contain it.
- Unassigned Reservations still show the current pending-table wording.
- Reservation remains `confirmed`; no arrival, queue, Seating, occupancy, or cleaning side effect occurs.
- Existing unrelated behavior remains unchanged.
