# Table Resource List API Contract

## Purpose

Expose a read-only table resource list for staff table selection.

This contract supports Staff UI V1.2:

- bottom navigation `桌台` tab;
- lightweight table status page;
- table picker for existing seating forms.

Table resources are configured upstream in backend/admin table setup or
synchronized from approved POS/integration adapters. This API only reads the
normalized RPB table resource model:

| Staff UI concept | Backend source | Submitted field |
| --- | --- | --- |
| 桌号 / 单桌 | `dining_tables` | `tableId` |
| 分组 / 桌组 / 组合桌 | `table_groups` and `table_group_members` | `tableGroupId` |

## Endpoint

```text
GET /api/v1/stores/{storeId}/tables
```

## App Gate

```text
appKey: reservation_queue
permission: table.view
```

The endpoint is store-scoped and must be denied when the actor cannot access
the requested store.

## Query Parameters

| Name | Required | Description |
| --- | --- | --- |
| `status` | No | Optional status filter such as `available`, `occupied`, `cleaning`, `locked`, `reserved`, or `inactive`. |
| `partySize` | No | Optional positive integer. When present, only resources whose capacity range includes the party size are returned. |
| `includeGroups` | No | Optional boolean. Defaults to `true`. When `false`, table groups are omitted. |
| `businessDate` | No | Optional `yyyy-MM-dd` business date. When present, reservation preassignments and temporary table groups for that date are overlaid on the resource list. Temporary groups scoped to other business dates must not be returned. |

## Success Response

Single table response item:

```json
{
  "success": true,
  "resources": [
    {
      "resourceType": "dining_table",
      "groupType": null,
      "resourceId": "70000000-0000-0000-0000-000000000001",
      "code": "A01",
      "displayName": "A01",
      "areaName": "大厅",
      "capacityMin": 1,
      "capacityMax": 4,
      "status": "available",
      "selectable": true,
      "selectionDisabledReason": null,
      "memberTableCodes": []
    }
  ]
}
```

Group response item:

```json
{
  "resourceType": "table_group",
  "groupType": "fixed",
  "resourceId": "71000000-0000-0000-0000-000000000001",
  "code": "VIP-1",
  "displayName": "VIP-1",
  "areaName": "包间",
  "capacityMin": 8,
  "capacityMax": 12,
  "status": "active",
  "selectable": true,
  "selectionDisabledReason": null,
  "memberTableCodes": ["V01", "V02"]
}
```

When the store has no configured table numbers or groups, the API returns:

```json
{
  "success": true,
  "resources": []
}
```

The frontend should display:

```text
暂无桌台，请先在后台配置桌台。
```

## Selectable Rules

| Resource | Selectable |
| --- | --- |
| Dining table `available` | `true` |
| Dining table `occupied`, `cleaning`, `locked`, `reserved`, `inactive` | `false` |
| Dining table already in an active temporary group | `false` |
| Table group `active` | `true` |
| Temporary table group `created` | `true` |
| Table group `locked`, `occupied`, `released`, `inactive`, `deleted`, `ended` | `false` |

`selectionDisabledReason` should be a stable short code when `selectable` is
false, for example:

```text
status_unavailable
temporary_group_member
```

Temporary table groups are scoped to a single business date. A temporary group
created for `2026-06-25` must not be returned or block member tables when the
resource list is requested for `2026-06-26`.

## Error Response

```json
{
  "success": false,
  "error": {
    "code": "INVALID_PARTY_SIZE",
    "messageKey": "table.resources.invalid_party_size",
    "details": {}
  }
}
```

Stable error codes:

```text
FORBIDDEN
STORE_SCOPE_MISMATCH
INVALID_STATUS
INVALID_PARTY_SIZE
PERSISTENCE_ERROR
```

## Non-Scope

This endpoint must not:

- create table numbers or groups;
- replace backend/admin table setup;
- call external POS systems directly;
- expose POS-specific table payloads to Staff UI;
- mutate table status;
- create seating or seating resources;
- start or complete cleaning;
- create temporary table groups;
- perform auto assignment;
- expose persistence entities;
- return fake table data.

## Boundary Statement

Table resource list API contract created: Yes
Backend API implemented: Yes
Router changed: Yes
Permission metadata changed: Yes
Migration changed: No
Production database touched: No
