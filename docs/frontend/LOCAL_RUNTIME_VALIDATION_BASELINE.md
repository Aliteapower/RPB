# Local Runtime Validation Baseline

## Purpose

Use one tenant/store/actor set for local browser validation so Staff Home, App Gate, API calls, and local seed data are easy to compare.

## Fixed IDs

- Tenant: `10000000-0000-0000-0000-000000000983`
- Store: `20000000-0000-0000-0000-000000000983`
- Actor: `30000000-0000-0000-0000-000000000983`

## Staff Home

```text
http://127.0.0.1:5176/stores/20000000-0000-0000-0000-000000000983/staff
```

The frontend fallback store also uses `20000000-0000-0000-0000-000000000983` when `VITE_DEFAULT_STORE_ID` is not set.

## Backend Local Auth

Backend local validation should use:

```text
--spring.profiles.active=local
--rpb.local-auth.enabled=true
--rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983
--rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000983
--rpb.local-auth.actor-type=staff
--rpb.local-auth.roles[0]=store_staff
--rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000983
```

Recommended permissions:

```text
reservation.create
reservation.check_in
reservation.queue
reservation.today_view
reservation.seat
queue.view
queue.call
queue.seat
walkin.direct_seating.create
cleaning.start
cleaning.complete
```

## Frontend Local Server

```text
npm run dev -- --host 127.0.0.1 --port 5176
```

## Data Guideline

New local browser fixtures should prefer the `...0983` tenant/store suffix. Historical validation reports can keep their original IDs because they describe evidence from earlier runs.
