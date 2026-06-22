# Store Staff Reservation Create Handoff V1

## 1. Purpose

This handoff describes the current store-staff Reservation Create capability for local demo, review, and repeatable smoke validation.

Current staff-facing capabilities:

```text
WalkIn Direct Seating
Cleaning Complete
Reservation Create
```

Reservation Create is limited to:

```text
Store staff creates a confirmed Reservation
-> reservation holds Store + business date + time range + party-size capacity
-> reservationCode is returned
-> idempotency is completed
```

This handoff does not introduce new frontend behavior, backend API, database schema, migration, seed data, production authentication, or additional reservation lifecycle flows.

## 2. Local Routes

Staff home:

```text
/stores/:storeId/staff
```

Reservation Create:

```text
/stores/:storeId/reservations/create
```

WalkIn Direct Seating:

```text
/stores/:storeId/walk-ins/direct-seating
```

Cleaning:

```text
/stores/:storeId/cleaning
```

## 3. Reservation Create Demo Path

1. Open `/stores/:storeId/staff`.
2. Click `Create Reservation`.
3. Input `partySize`.
4. Input `reservedStartAt` using the shared three-part date and two-part time picker.
5. Leave `reservedEndAt` empty if using backend duration derivation.
6. Optionally input `customerName`, `customerNickname`, or `phoneE164`.
7. Submit.
8. Confirm `reservationCode`.
9. Confirm `status=confirmed`.
10. Confirm `reservedEndAt` and `holdUntilAt` returned.

## 4. Expected Result

Successful Reservation Create should display:

- `reservationId`
- `reservationCode`
- `status = confirmed`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `businessDate`
- customer projection when present
- `events = reservation.created, reservation.confirmed`
- `idempotency.status = completed`
- `idempotency.replayed` when replayed

Expected data boundary:

- no QueueTicket created
- no Seating created
- no TableLock created
- no ReservationPreassignment created
- no CheckIn created
- no No-show created
- no Cancellation created

## 5. Known Limitations

- No CheckIn yet.
- No Queue yet.
- No Seating from Reservation yet.
- No cancellation yet.
- No no-show yet.
- No Reservation calendar/list yet.
- No table assignment yet.
- No table selector yet.
- No production JWT/login yet.
- Local/test actor placeholder only.
- `customerName` / `customerNickname` persistence is still limited by the current Customer model; API response can project request hints.
- Capacity limit currently uses V1 fallback unless StorePolicy later adds capacity configuration.
- Error display uses `error.code` and `error.messageKey`.
- No frontend test framework yet.

## 6. Local Runtime Notes

Frontend local URL:

```text
http://127.0.0.1:5173
```

Backend local URL:

```text
http://127.0.0.1:18082
```

Default backend URL in older local handoff material may be `http://127.0.0.1:8080`; Reservation UI local validation used the Vite proxy against the local backend runtime on port `18082`.

Local PostgreSQL:

```text
127.0.0.1:<temporary-test-port>
```

The Reservation UI validation report used a local temporary PostgreSQL database and did not touch production database. The exact port may vary by runtime setup.

Local/test actor placeholder:

- no full JWT login
- no production auth system
- tenant scope comes from local backend runtime context
- store scope comes from local backend runtime context and route path

Required staff permissions:

```text
walkin.direct_seating.create
cleaning.start
cleaning.complete
reservation.create
```

Production database touched:

```text
No
```

## 7. Smoke Review Checklist

Route and navigation:

- Staff Home route opens: `/stores/:storeId/staff`.
- Reservation Create route opens: `/stores/:storeId/reservations/create`.
- Staff Home links include `Create Reservation`.
- Staff Home links include `WalkIn Direct Seating`.
- Staff Home links include `Cleaning`.

Reservation form:

- `partySize` is present.
- `reservedStartAt` is present.
- `reservedEndAt` is optional.
- customer fields are optional.
- `note` is optional.
- request body excludes `tenantId`.
- request body excludes table, queue, seating, check-in, no-show, and cancellation fields.

Success display:

- `reservationCode` is highlighted.
- `status=confirmed` is shown.
- `reservedEndAt` is shown.
- `holdUntilAt` is shown.
- `idempotency` is shown.

Error display:

- `error.code` is shown.
- `error.messageKey` is shown.
- UI does not replace `messageKey` with hardcoded business copy.

Forbidden entries:

- no CheckIn entry
- no Queue entry
- no Seating entry
- no No-show entry
- no Cancellation entry
- no Reservation Calendar/List entry
- no Table assignment UI

## 8. Boundary

This handoff does not include:

- CheckIn implementation
- Queue implementation
- Seating from Reservation
- No-show implementation
- Cancellation implementation
- Reservation Calendar/List
- Table assignment
- Table selector
- Customer search
- backend API changes
- migration changes
- SQL changes
- production database access
- seed data
- full production JWT/login
