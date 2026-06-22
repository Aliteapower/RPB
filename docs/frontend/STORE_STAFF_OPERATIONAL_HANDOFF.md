# Store Staff Operational Handoff V1

## 1. Purpose

This handoff describes the current minimum store staff operating surface:

```text
WalkIn Direct Seating
-> table occupied
-> Start Cleaning
-> Complete Cleaning
-> table available
```

It also includes the currently validated Reservation Create entry:

```text
Create Reservation
-> confirmed Reservation
-> reservationCode returned
```

It is intended for local demo and operator validation only. It does not introduce new backend API, database schema, production authentication, or additional business modules.

## 2. Local Run Commands

Backend:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend:

```text
npm run dev -- --host 127.0.0.1 --port 5176
```

Frontend build validation:

```text
npm run build
```

Backend test validation:

```text
mvn test
```

## 3. Runtime Environment

Frontend URL:

```text
http://127.0.0.1:5176
```

Backend URL:

```text
http://127.0.0.1:8080
```

Vite proxy:

```text
/api -> VITE_API_PROXY_TARGET or http://127.0.0.1:8080
```

Runtime smoke may use a non-default backend port, for example:

```text
VITE_API_PROXY_TARGET=http://127.0.0.1:18082
```

Auth boundary:

- Local/test actor placeholder is used.
- No full JWT login system is implemented.
- Tenant, store, actor, role, and permissions come from the local backend runtime context.
- Fixed local validation tenant: `10000000-0000-0000-0000-000000000983`.
- Fixed local validation store: `20000000-0000-0000-0000-000000000983`.
- Fixed Staff Home URL: `http://127.0.0.1:5176/stores/20000000-0000-0000-0000-000000000983/staff`.
- Required current staff permissions:
  - `walkin.direct_seating.create`
  - `cleaning.start`
  - `cleaning.complete`
  - `reservation.create`
  - `reservation.check_in`
  - `reservation.queue`
  - `reservation.today_view`
  - `reservation.seat`
  - `queue.view`
  - `queue.call`
  - `queue.seat`

Database:

- Use local PostgreSQL or the local test database described by the backend runtime setup.
- Production database must not be touched.
- Production data must not be inserted.

## 4. Staff Home

Route:

```text
/stores/:storeId/staff
```

The staff home shows:

- current `storeId`
- closed loop status
- link to WalkIn Direct Seating
- link to Cleaning Complete
- link to Create Reservation
- short operation path

Allowed links only:

```text
/stores/:storeId/walk-ins/direct-seating
/stores/:storeId/cleaning
/stores/:storeId/reservations/create
```

## 5. WalkIn / Cleaning Demo Path

1. Open `/stores/:storeId/staff`.
2. Click `WalkIn Direct Seating`.
3. Input `partySize`.
4. Submit direct seating.
5. Click `开始清台`.
6. Start Cleaning with auto-filled `seatingId`.
7. Complete Cleaning with auto-filled `cleaningId`.
8. Confirm `tableStatus=available`.

## 6. Reservation Create Demo Path

1. Open `/stores/:storeId/staff`.
2. Click `Create Reservation`.
3. Input `partySize`.
4. Input `reservedStartAt`.
5. Leave `reservedEndAt` empty if using backend duration derivation.
6. Optionally input `customerName`, `customerNickname`, or `phoneE164`.
7. Submit.
8. Confirm `reservationCode`.
9. Confirm `status=confirmed`.
10. Confirm `reservedEndAt` and `holdUntilAt` returned.

## 7. Expected Result

Successful WalkIn / Cleaning closed loop should display:

- `walkInId`
- `seatingId`
- `cleaningId`
- `tableStatus=available`
- idempotency status `completed`

Expected data boundary:

- no Reservation created
- no QueueTicket created
- no Turnover created

Successful Reservation Create should display:

- `reservationId`
- `reservationCode`
- `status=confirmed`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `businessDate`
- idempotency status `completed`

Reservation Create expected data boundary:

- no QueueTicket created
- no Seating created
- no TableLock created
- no ReservationPreassignment created
- no CheckIn created

## 8. Known Limitations

- No real JWT login.
- Local/test actor placeholder only.
- No store switcher yet.
- No table selector.
- Reservation Create UI only; no Reservation CheckIn, Queue fallback, Seating from Reservation, No-show, Cancellation, Calendar, or List.
- No Queue UI.
- No Turnover BI.
- `seatingId` and `cleaningId` are still visible as fallback values.
- `customerName` / `customerNickname` persistence is still limited by the current Customer model.
- Reservation capacity limit currently uses V1 fallback unless StorePolicy later adds capacity configuration.
- Error display uses `error.code` and `error.messageKey`.
- No frontend test framework yet.
- Table availability still depends on the backend local runtime fixture or local database state.

## 9. Boundary

This handoff does not include:

- new backend API
- new migration
- SQL changes
- production auth
- production database access
- frontend test framework
- complex table map
- drag-and-drop table layout
- CheckIn
- Queue
- Seating from Reservation
- No-show
- Cancellation
- Reservation Calendar/List
