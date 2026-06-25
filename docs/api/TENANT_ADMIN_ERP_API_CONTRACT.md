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
| `PATCH` | `/api/v1/stores/{storeId}/tenant-admin/tables/{tableId}` | Update table area, code, capacity, enabled state. |
| `GET` | `/api/v1/stores/{storeId}/tenant-admin/settings` | Read store basics and active policy. |
| `PATCH` | `/api/v1/stores/{storeId}/tenant-admin/settings` | Update store basics and active policy. |

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
