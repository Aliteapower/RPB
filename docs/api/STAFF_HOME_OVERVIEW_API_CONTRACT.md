# Staff Home Overview API Contract

## Purpose

The staff home page is an operational today overview. It helps store staff judge the current business day quickly. It is not a weekly/monthly BI report.

## Endpoint

`GET /api/v1/stores/{storeId}/staff-home/overview?businessDate=YYYY-MM-DD`

## App Gate

- App key: `reservation_queue`
- Permission: `reservation.today_view`
- No new permission is introduced.

## Response

```json
{
  "success": true,
  "storeId": "20000000-0000-0000-0000-000000000983",
  "businessDate": "2026-06-25",
  "storeTimezone": "Asia/Singapore",
  "reservation": {
    "totalReservations": 6,
    "totalPartySize": 24,
    "arrivedReservations": 2,
    "arrivedPartySize": 8,
    "seatedReservations": 1,
    "seatedPartySize": 4,
    "cancelledReservations": 1
  },
  "queue": {
    "waitingTickets": 3,
    "waitingPartySize": 7,
    "calledTickets": 1,
    "calledPartySize": 8,
    "seatedTickets": 2,
    "skippedTickets": 1,
    "cancelledTickets": 0,
    "expiredTickets": 0
  },
  "tables": {
    "totalTables": 8,
    "availableTables": 4,
    "reservedTables": 1,
    "occupiedTables": 2,
    "cleaningTables": 1,
    "temporaryGroups": 1
  },
  "partySizeGroups": [
    { "label": "1-2", "groups": 2, "partySize": 4 },
    { "label": "3-4", "groups": 1, "partySize": 3 },
    { "label": "5-6", "groups": 0, "partySize": 0 },
    { "label": "7+", "groups": 1, "partySize": 8 }
  ]
}
```

## Aggregation Rules

- `reservation` is based on the existing today reservation read model with `status=all`.
- `queue` is based on persisted queue tickets for the same business date, grouped by ticket status.
- `partySizeGroups` is based on active queue pressure only: `waiting` and `called`.
- `tables` is based on the existing table resource read model for the business date, including temporary table groups.
- Historical trends remain out of scope. Add a separate report page if weekly/monthly trend analysis is needed later.
