# Temporary Table Group API Contract

## Purpose

Allow store staff to save a named temporary table group from the table page and later dissolve it back into single tables.

This supports the staff workflow:

- enter a temporary group name;
- select two or more combinable tables;
- save the group for the selected reservation business date;
- dissolve a still-created temporary group when it is no longer needed.

## App Gate

```text
appKey: reservation_queue
permission: table.switch
```

Both endpoints are tenant and store scoped. Requests must be denied when the actor cannot access the requested store.

## Save Temporary Group

```text
POST /api/v1/stores/{storeId}/tables/temporary-groups
```

Request:

```json
{
  "groupName": "A区临组1",
  "businessDate": "2026-06-25",
  "tableIds": [
    "70000000-0000-0000-0000-000000000981",
    "70000000-0000-0000-0000-000000000982"
  ]
}
```

Success status: `201 Created`

Response:

```json
{
  "success": true,
  "tableGroupId": "71000000-0000-0000-0000-000000000991",
  "groupName": "A区临组1",
  "groupType": "temporary",
  "status": "created",
  "capacityMin": 4,
  "capacityMax": 8,
  "tableIds": [
    "70000000-0000-0000-0000-000000000981",
    "70000000-0000-0000-0000-000000000982"
  ]
}
```

Validation:

- `groupName` is required and must be unique among active groups in the store.
- At least two distinct `tableIds` are required.
- Each member table must belong to the same tenant and store and be combinable.
- For the live business date, each member table must also be available, unlocked, and unoccupied.
- For a non-live reservation business date, the member table's current live occupancy, lock, cleaning, or reserved state does not block planning, but inactive tables remain unavailable.
- Active reservation preassignments and active temporary group membership on the request `businessDate` block temporary group creation.
- Saved temporary groups are valid only for the request `businessDate`; they must not appear on another business date.

## Dissolve Temporary Group

```text
DELETE /api/v1/stores/{storeId}/tables/temporary-groups/{tableGroupId}
```

Success status: `200 OK`

Response shape matches the save response and contains the dissolved group and member table ids.

Only `temporary` groups in `created` status can be dissolved. Occupied, locked, fixed, deleted, or missing groups must not be dissolved.

## Error Response

```json
{
  "success": false,
  "error": {
    "code": "MEMBER_UNAVAILABLE",
    "messageKey": "table.temporary_group.member_unavailable",
    "details": {}
  }
}
```

Stable error codes:

```text
FORBIDDEN
STORE_SCOPE_MISMATCH
INVALID_BUSINESS_DATE
GROUP_NAME_REQUIRED
GROUP_NAME_CONFLICT
GROUP_NOT_FOUND
GROUP_NOT_TEMPORARY
GROUP_NOT_DISSOLVABLE
MEMBER_REQUIRED
MEMBER_DUPLICATE
MEMBER_UNAVAILABLE
CAPACITY_INSUFFICIENT
LOCK_CONFLICT
PREASSIGNMENT_CONFLICT
PERSISTENCE_ERROR
```

## Persistence And Migration

This API reuses the existing `table_groups` and `table_group_members` tables:

- saved groups use `group_type = temporary`;
- saved groups use `status = created`;
- saved groups use `active_from_at` and `active_until_at` as the business-date window;
- dissolve soft-deletes the group and member rows through `deleted_at`.

Migration changed: No
Production database touched: No
