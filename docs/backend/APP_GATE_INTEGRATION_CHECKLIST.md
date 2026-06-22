# App Gate Integration Checklist

Use this checklist before adding or reviewing any new business endpoint in the Reservation, WalkIn, Queue, Seating, Cleaning, or Turnover loop.

The default app key for this business line is:

```text
reservation_queue
```

## Checklist

- [ ] 1. Which `app_key` owns this endpoint?
- [ ] 2. Does an existing `app_key` already cover this business line? Do not create a duplicate app.
- [ ] 3. Does this endpoint need a new permission key?
- [ ] 4. Does the permission key follow lowercase dot hierarchy with business object first and action second?
- [ ] 5. Does the Controller endpoint declare `@RequireAppGate(appKey = "...", permission = "...")`?
- [ ] 6. Does `storeId` come from path, query, or trusted server context?
- [ ] 7. Does `tenantId` come only from actor or server-side security context?
- [ ] 8. Is `body.tenantId` ignored for App Gate scope?
- [ ] 9. Is there an app disabled test?
- [ ] 10. Is there a tenant entitlement missing or inactive test?
- [ ] 11. Is there a store disabled or not configured test?
- [ ] 12. Is there a permission denied test?
- [ ] 13. Is there a success path regression test?
- [ ] 14. Does every deny write `app_gate_audit_logs` with `APP_GATE_DENIED`?
- [ ] 15. Does the staff homepage need to display an app entry for this capability?
- [ ] 16. Does `/api/me/apps?storeId=xxx` cover app-entry visibility? If button-level capability visibility is needed, is there a separately approved capability-level response contract?
- [ ] 17. Is the business state machine unchanged by App Gate?
- [ ] 18. Are existing API paths unchanged?
- [ ] 19. Is there no unnecessary migration?
- [ ] 20. Are the operational handoff and implementation report updated?

## Required Endpoint Evidence

For each protected endpoint, record:

| Item | Value |
|---|---|
| Method | |
| Path | |
| App key | |
| Permission key | |
| Tenant scope source | |
| Store scope source | |
| Actor source | |
| Expected deny codes | |
| Audit action | `APP_GATE_DENIED` |
| Business regression test | |

## Permission Naming Review

Acceptable examples:

```text
reservation.create
reservation.check_in
reservation.cancel
reservation.no_show
reservation.seat
queue.ticket.create
queue.call
queue.skip
queue.rejoin
seating.create
seating.change_table
cleaning.start
cleaning.complete
walkin.direct_seating.create
```

Reject examples:

```text
ReservationController.create
/api/v1/stores/{storeId}/reservations
staff.home.create.button
walkin_create
qingta_complete
temp.permission.1
```

## Boundary Review

Before merging any App Gate integration:

- Confirm it does not create `reservation_app`, `queue_app`, `walkin_app`, `cleaning_app`, or `seating_app`.
- Confirm it does not modify Reservation, QueueTicket, Table, Seating, WalkIn, or Cleaning state machines.
- Confirm App Gate denial happens before the business handler.
- Confirm deny paths do not write business state changes.
- Confirm the frontend app entry comes from `/api/me/apps`, not from hardcoded entitlement assumptions.
