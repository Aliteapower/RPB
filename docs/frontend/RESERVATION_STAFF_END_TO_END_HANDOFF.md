# Reservation Staff End-to-End Handoff V1

## 1. Purpose

This handoff documents the current V1 store-staff Reservation closed loop:

```text
Create Reservation
-> Check In Reservation
-> Seat Arrived Reservation
-> Cleaning Complete
```

The business loop now supports:

```text
Create a confirmed reservation
-> register customer arrival
-> seat the arrived reservation directly
-> start and complete cleaning
-> release the table back to available
```

This handoff does not introduce Queue, auto assignment, Reservation list/calendar/search, table map, No-show, Cancellation, Turnover analytics, migration changes, production configuration changes, or production data changes.

## 2. Local Routes

Store staff entry:

```text
/stores/:storeId/staff
```

Reservation closed-loop routes:

```text
/stores/:storeId/reservations/create
/stores/:storeId/reservations/check-in
/stores/:storeId/reservations/seating/direct
/stores/:storeId/cleaning
```

Related existing staff route:

```text
/stores/:storeId/walk-ins/direct-seating
```

## 3. Permissions

All current staff operations belong to:

```text
app_key = reservation_queue
```

Current permission keys:

```text
reservation.create
reservation.check_in
reservation.seat
cleaning.start
cleaning.complete
walkin.direct_seating.create
```

Frontend Staff Home uses:

```text
GET /api/me/apps?storeId={storeId}
```

The frontend uses the returned visible `reservation_queue` app and actor-owned permissions to show current approved entries. Backend App Gate remains the final authorization boundary for every command endpoint.

## 4. Staff Home Entries

Current Staff Home entries:

```text
WalkIn Direct Seating
Cleaning Complete
Create Reservation
Check In Reservation
Seat Arrived Reservation
```

No entries exist for:

```text
Queue
Auto assignment
Reservation list
Reservation calendar
Reservation search
Table map
No-show
Cancellation
Turnover analytics
```

## 5. Demo Path

1. Open `/stores/:storeId/staff`.
2. Confirm the five current staff entries are visible for an actor with the required permissions.
3. Open `Create Reservation`.
4. Input reservation details and submit.
5. Copy `reservationId`.
6. Open `Check In Reservation`.
7. Input `reservationId` and submit.
8. Confirm `status=arrived`.
9. Open `Seat Arrived Reservation`.
10. Input `reservationId` and exactly one resource target, such as `tableId`.
11. Submit and confirm `reservationStatus=seated`.
12. Copy returned `seatingId`.
13. Open `Cleaning Complete` with `?seatingId={seatingId}` or paste the `seatingId`.
14. Start cleaning.
15. Confirm returned `cleaningId`, `cleaningStatus=cleaning`, and `tableStatus=cleaning`.
16. Complete cleaning.
17. Confirm `cleaningStatus=released` and `tableStatus=available`.

## 6. Expected DB Evidence

### Create Reservation

```text
reservations.status = confirmed
business_events contains reservation.created
business_events contains reservation.confirmed
state_transition_logs contains none -> confirmed
audit_logs contains reservation.create
idempotency_records contains create_reservation completed
```

### CheckIn

```text
reservations.status = arrived
business_events contains reservation.arrived
state_transition_logs contains confirmed -> arrived
audit_logs contains reservation.check_in
idempotency_records contains check_in_reservation completed
```

### Direct Seating

```text
reservations.status = seated
seatings created with reservation_id
seating_resources created
table.status = occupied
business_events contains reservation.seated
business_events contains seating.created
business_events contains table.occupied
state_transition_logs contains arrived -> seated
state_transition_logs contains planned -> occupied for seating
state_transition_logs contains table available -> occupied
audit_logs contains reservation.seat
idempotency_records contains seat_arrived_reservation completed
```

### Cleaning

```text
cleanings created
cleanings.status = released after completion
table.status = available after completion
business_events contains cleaning.started
business_events contains table.cleaning
business_events contains cleaning.completed
business_events contains table.available
state_transition_logs contains cleaning pending -> cleaning
state_transition_logs contains table occupied -> cleaning
state_transition_logs contains cleaning cleaning -> released
state_transition_logs contains table cleaning -> available
audit_logs contains cleaning.start.completed
audit_logs contains cleaning.complete.completed
idempotency_records contains start_cleaning completed
idempotency_records contains complete_cleaning completed
```

## 7. Local Runtime Notes

Current validated local runtime:

```text
Frontend: http://127.0.0.1:5173
Backend: http://127.0.0.1:8080
PostgreSQL: 127.0.0.1:63822/reservation_platform
Store: 20000000-0000-0000-0000-000000000701
Tenant: 10000000-0000-0000-0000-000000000701
Actor: 30000000-0000-0000-0000-000000000701
```

The local actor must include:

```text
reservation.create
reservation.check_in
reservation.seat
cleaning.start
cleaning.complete
walkin.direct_seating.create
```

## 8. Known Limitations

- No Queue yet.
- No Reservation list/calendar/search yet.
- No table map yet.
- No auto assignment yet.
- No no-show yet.
- No cancellation yet.
- No production JWT/login yet.
- No reporting or turnover analytics yet.
- Current UI copy is mixed Chinese/English and should be unified to Chinese before a later i18n extraction round.
- Operators still copy IDs manually between screens; selectors/lists are intentionally out of scope for V1 smoke.

## 9. Boundary

This handoff does not include:

- Queue API/UI.
- Auto assignment.
- Reservation list/calendar/search.
- Table map.
- No-show API/UI.
- Cancellation API/UI.
- Turnover API/UI.
- New App Gate app key.
- New permission model.
- Flyway migration.
- SQL schema changes.
- Production config.
- Production database access.
- Production seed data.
