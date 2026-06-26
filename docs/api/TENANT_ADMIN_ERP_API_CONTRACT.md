# Tenant Admin ERP API Contract

Scope: tenant admin pages under `/stores/:storeId/admin/*`.

Authorization:
- User must be authenticated.
- User must have role `tenant_admin` and permission `tenant.admin.manage`.
- `storeId` must be in the authenticated account store-access list.
- All persistence queries include both `tenant_id` and `store_id`.

Password policy:
- Password is exactly 6 ASCII letters or digits.
- Login is case-insensitive because passwords are stored as lowercase BCrypt input.

Endpoints:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/stores/{storeId}/tenant-admin/staff` | Staff list, supports `keyword`, `limit`, `offset`. |
| `POST` | `/api/v1/stores/{storeId}/tenant-admin/staff` | Create staff account. |
| `PATCH` | `/api/v1/stores/{storeId}/tenant-admin/staff/{staffId}` | Update staff profile, status, optional password reset. |
| `GET` | `/api/v1/stores/{storeId}/tenant-admin/tables` | Dining table list, supports `keyword`, `limit`, `offset`. |
| `POST` | `/api/v1/stores/{storeId}/tenant-admin/tables` | Create dining table and area if needed. |
| `GET` | `/api/v1/stores/{storeId}/tenant-admin/tables/export` | Export dining table setup as `.xlsx`. |
| `POST` | `/api/v1/stores/{storeId}/tenant-admin/tables/import` | Import `.xlsx`; existing table codes in the current store are overwritten, missing codes are created. |
| `PATCH` | `/api/v1/stores/{storeId}/tenant-admin/tables/{tableId}` | Update table area, code, capacity, enabled state. |
| `GET` | `/api/v1/stores/{storeId}/tenant-admin/settings` | Read store basics and active policy. |
| `PATCH` | `/api/v1/stores/{storeId}/tenant-admin/settings` | Update store basics and active policy. |

Table response fields:

| Field | Meaning |
|---|---|
| `areaId` | Store-scoped area identifier. |
| `areaName` | Area display name. |
| `areaSortOrder` | Area category display order. |
| `tableCode` | Store-local table number. |
| `tableSortOrder` | Child table display order inside its area. |
| `capacity` | Maximum party size for the table. |
| `enabled` | `true` maps to `available`; `false` maps to `inactive`. |

Table create/update accepts `areaSortOrder` and `tableSortOrder`. Missing sort values default to `0` for backward compatibility.

Excel import/export contract:

| Column | Required | Meaning |
|---|---:|---|
| `大类排序` | Yes | Area category order. |
| `桌号排序` | Yes | Child table order. |
| `分区组` | Yes | Area display name; area is found or created in the current store. |
| `桌号` | Yes | Store-local table code; this is the import overwrite key. |
| `人数` | Yes | Table capacity, 1 to 999. |
| `启用` | Yes | Accepts `true`, `false`, `是`, `否`, `启用`, `停用`, `1`, `0`. |

Import overwrite rule:

- Match existing rows by current `storeId` + `tableCode`.
- Existing rows are updated when the table is maintainable (`available` or `inactive`).
- Missing rows are created.
- Import never writes another tenant or store because the endpoint resolves tenant/store from the authenticated tenant admin actor.

Stable errors:
- `UNAUTHENTICATED` -> 401
- `FORBIDDEN` -> 403
- `STORE_SCOPE_MISMATCH` -> 403
- `REQUEST_INVALID` -> 400
- `STAFF_NOT_FOUND` -> 404
- `TABLE_NOT_FOUND` -> 404
- `STAFF_CODE_CONFLICT` -> 409
- `TABLE_CODE_CONFLICT` -> 409
- `TABLE_IN_USE` -> 409
- `PERSISTENCE_ERROR` -> 500
