# Tenant Table Management Sort Excel Design

## Context

Tenant table management currently stores areas in `store_areas` and table numbers in `dining_tables`. `store_areas.sort_order` already exists, but `dining_tables` has no child ordering field. The tenant admin UI lists and edits table numbers through `/api/v1/stores/{storeId}/tenant-admin/tables`.

The requested change is to optimize tenant table management so area categories and child table numbers can both be sorted, the frontend visibly uses the enabled state, and table data can be imported/exported as Excel for this module and later reusable modules.

## Scope

- Add persisted child table ordering with `dining_tables.sort_order`.
- Expose area and table sort order in tenant admin table APIs.
- Let tenant admins edit sort order in the table form and see sorted results in the list.
- Add Excel export for table setup.
- Add Excel import where rows in the current store overwrite existing `tableCode` rows and create missing rows.
- Create a reusable backend Excel helper package instead of hardcoding workbook logic inside tenant admin services.

Out of scope:

- Drag-and-drop floor plans.
- Seating, queue, reservation, cleaning, or table status state-machine changes.
- New permission model; existing `tenant.admin.manage` remains the gate.
- Cross-store or cross-tenant import.

## Architecture

The table management boundary remains Store scoped. `TenantAdminController` keeps authentication, role, permission, and store-scope checks. Application services own normalization and overwrite policy. Repository methods own SQL with both `tenant_id` and `store_id` filters.

Excel support is introduced under `com.rpb.reservation.common.excel` as reusable read/write primitives. Tenant table import/export maps those primitives to a table-specific row model and policy. This keeps workbook mechanics separate from business rules.

## Data Design

`store_areas.sort_order` remains the area category order.

`dining_tables.sort_order integer not null default 0` becomes the child table order inside an area. List ordering becomes:

```text
area.sort_order,
area.display_name,
table.sort_order,
table.table_code
```

The migration adds an index supporting store-scoped sorted lookups:

```text
(tenant_id, store_id, area_id, sort_order, table_code)
```

Existing rows default to `0`, preserving old behavior via table code fallback.

## API Design

Existing table list/create/get/update responses include:

- `areaId`
- `areaSortOrder`
- `tableSortOrder`

Create/update requests accept optional:

- `areaSortOrder`
- `tableSortOrder`

New endpoints:

```text
GET  /api/v1/stores/{storeId}/tenant-admin/tables/export
POST /api/v1/stores/{storeId}/tenant-admin/tables/import
```

Export returns an `.xlsx` attachment.

Import consumes multipart form field `file` and returns:

```json
{
  "success": true,
  "imported": {
    "totalRows": 3,
    "created": 1,
    "updated": 2
  }
}
```

Stable error mapping uses existing `REQUEST_INVALID`, `TABLE_CODE_CONFLICT`, `STORE_SCOPE_MISMATCH`, `FORBIDDEN`, `UNAUTHENTICATED`, and `PERSISTENCE_ERROR`.

## Excel Contract

Sheet name: `tables`

Columns:

```text
大类排序
桌号排序
分区组
桌号
人数
启用
```

Import behavior:

- `桌号` is the store-local identity key.
- Existing table numbers are overwritten in the current store.
- Missing table numbers are created.
- Area is found or created by normalized area name.
- Area sort order is updated when the row includes it.
- Table sort order is updated from `桌号排序`.
- `启用` accepts `true`, `false`, `是`, `否`, `启用`, `停用`, `1`, and `0`.
- Blank required fields or invalid capacity fail the import with `REQUEST_INVALID`.

## Frontend Design

The table list shows area order and table order columns and remains sorted by backend order. Export and import actions live beside the create action. Import refreshes the list after success and shows a concise result summary.

The table form adds numeric inputs for `大类排序` and `桌号排序`. The enabled checkbox continues to map to backend `available` or `inactive`, so disabled rows are not just visual state.

## Testing

Backend tests:

- API integration proves sorted table list order uses area and child table ordering.
- API integration proves export returns an Excel attachment.
- API integration proves import overwrites existing table code and creates missing table code.
- Scope and permission behavior continue through existing tenant admin tests.

Frontend checks:

- TypeScript build verifies new API types and page integration.
- Existing UI validation style checks confirm table page includes import/export and sort fields.

## Review Notes

Architecture review: The change stays inside Area & Table Management and Tenant Admin. It does not alter Reservation, Queue, Seating, or Cleaning workflows.

Database review: New column is store-scoped operational configuration, not a tenant-level field. Tenant and store isolation remains enforced by existing keys and repository predicates.

API review: New endpoints stay under `/api/v1`, return stable DTOs, and reuse the existing permission and error envelope.
