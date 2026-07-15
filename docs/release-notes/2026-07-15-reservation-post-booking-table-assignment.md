# Release Notes

## Version / Date
2026-07-15 - Reservation post-booking table assignment

## New
- Tenant employees can assign a dining table to a confirmed, currently unassigned reservation from the today reservation workbench.
- The assignment dialog lists only active dining tables that can hold the party and are free for the reservation's exact half-open time range.
- Successful assignment writes the `reservation.table_assigned` business event and the `reservation.table_assign` audit operation.

## Changed
- Public-booking and staff-created reservations follow the same post-booking assignment rules and use the same modular application service and API.
- The today reservation card refreshes after assignment and displays the assigned table code.
- Existing employee and public sharing channels immediately reuse the assigned table code through the existing `tableCode` template variable.
- Assignment is idempotent: replaying the same completed command returns the original result, while reusing the key with a different payload is rejected.

## Fixed
- Confirmed reservations that initially had no table can now be assigned before arrival without creating a queue or seating record and without changing the reservation status from `confirmed`.
- Boundary-touching reservations no longer block each other; only true time overlaps make a table unavailable.

## Migration
- Flyway migration `V045__reservation_active_preassignment_uniqueness.sql` adds a tenant/store/reservation-scoped partial unique index for active, non-deleted preassignments.
- Local validation confirmed there were no conflicting active preassignments before applying the index.

## Permission
- No new permission is introduced.
- Listing candidate tables reuses `table.view`; assigning a table requires `reservation.create`. Existing tenant-admin, store-manager, and store-staff scope checks remain in force.

## Risk
- Concurrent assignment requests are serialized with tenant/store-scoped reservation and table row locks, then revalidated against the exact reservation interval.
- Event, audit, idempotency completion, and preassignment persistence share one transaction; any write failure rolls back the assignment.
- Arrival, queue, seating, cancellation, and other reservation workflows are unchanged.

## Rollback Notes
- Revert the application changes to remove the assignment API, dialog, and button. Existing reservation preassignments remain readable by older code.
- Prefer leaving the uniqueness index in place because it protects existing data without changing read behavior. If removal is explicitly required during an approved maintenance window, execute `DROP INDEX IF EXISTS ux_reservation_preassignments_one_active_reservation;` after confirming no dependent rollout is active.
- Do not down-migrate or delete assigned reservation data as part of application rollback.
