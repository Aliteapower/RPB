# Release Notes

## Version / Date

2026-07-16 - Reservation preassignment and seating consistency

## New

- Successful direct Seating now consumes the Reservation's matching active Table preassignment and records the release details in the existing `reservation.seat` audit entry.
- PostgreSQL/API regression coverage now proves the complete public-booking flow: assign Table, keep Reservation `confirmed`, check in, seat to the assigned Table, release the preassignment, and replay the command idempotently without creating a second Seating.

## Changed

- Assignable-table results now include only physically `available` Tables with sufficient capacity, no active Table lock, no active Seating occupancy, and no overlapping Reservation preassignment for the exact half-open Reservation interval.
- The assignment command repeats those authoritative checks after locking the scoped Reservation and Table, so a stale employee dialog cannot assign a Table that became unavailable.
- Direct Seating treats a matching active preassignment as ownership by that Reservation. It may seat the matching `available` Table, or a matching `reserved` Table when no physical blocker exists.
- Public-booking and employee-created Reservations continue to use the same assignment and Seating services, rules, error codes, audit flow, and idempotency behavior.

## Fixed

- Employees can no longer preassign Tables whose current physical state is `locked`, `reserved`, `occupied`, `cleaning`, or `inactive`.
- An assigned Reservation no longer conflicts with its own Table preassignment during direct Seating.
- A matching preassignment never overrides a real physical blocker: active Seating occupancy, Table lock, cleaning, inactive state, insufficient capacity, or another Reservation's ownership still returns the existing stable availability error.

## Migration

- No new database migration is required. The existing V045 active-preassignment uniqueness index remains unchanged.
- No data rewrite is performed. Existing active Seating and cleaning lifecycles must be completed normally before a physically blocked preassigned Table can be seated.

## Permission

- No App Gate permission or role mapping changes are required.
- Candidate listing continues to require `table.view`; assignment continues to require `reservation.create`; check-in and direct Seating retain their existing permissions.

## Risk

- Tenant, Store, Reservation, preassignment, and resource identity are all included in the conditional release update; a stale or foreign preassignment cannot be released.
- Seating, Table status, Reservation status, preassignment release, events, audit, and idempotency completion share the existing Seating transaction. A release write conflict fails with the existing repository error and rolls back the Seating mutation.
- Candidate listing performs lock and active-Seating checks per currently available Table. This adds bounded database reads proportional to the Store's visible Table count; no API payload or contract changes are introduced.
- Previously misassigned Reservations are not force-seated and existing active Seating is not auto-completed. Operations must finish the current Seating/cleaning workflow before retrying.

## Verification

- Focused backend regression: 14 test classes, 103 tests, 0 failures, 0 errors, 0 skipped.
- Pointer-backed PostgreSQL/API test used `target/local-postgres-current.txt` and verified physical blockers, `confirmed` status after assignment, `arrived -> seated`, conditional preassignment release, audit metadata, and idempotent replay.
- Frontend production build: `npm run build` completed successfully; no frontend source change is included in this fix.
- The legacy integration classes that create their own random-port PostgreSQL runtime were intentionally excluded to comply with the repository's pointer-database rule.

## Deployment

- Deploy the backend artifact and restart `rpb-backend`. No Flyway action, environment variable, permission seed, or frontend publication is required for this backend-only fix.
- After restart, verify with a fresh public Reservation and a physically available Table: assign, confirm the Reservation remains `confirmed`, check in, seat, and confirm the active preassignment becomes `released`.

## Rollback Notes

- Restore the previous backend artifact and restart `rpb-backend`; there is no schema or configuration rollback.
- Preassignments already released by successful Seating remain released and must not be reactivated automatically.
- Rolling back reintroduces the original assignment/Seating inconsistency, so rollback is for emergency service recovery only.
