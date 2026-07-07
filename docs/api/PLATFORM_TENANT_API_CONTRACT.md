# Platform Tenant API Contract

## Scope
Platform tenant management is owned by the platform back office and is guarded by the `platform_admin` role plus `platform.tenant.manage`.

## Tenant Admin Store Access

### `GET /api/v1/platform/tenants/{tenantId}/admin-store-access`
Returns the tenant admin account's current store authorization state and the active tenant stores that can be granted.

Response:

```json
{
  "success": true,
  "stores": [
    {
      "storeId": "20000000-0000-0000-0000-000000000983",
      "storeCode": "local-validation-store",
      "storeName": "Local Validation Store",
      "status": "active",
      "locale": "zh-CN",
      "defaultStore": true
    }
  ],
  "storeIds": ["20000000-0000-0000-0000-000000000983"],
  "defaultStoreId": "20000000-0000-0000-0000-000000000983"
}
```

Rules:
- `stores` includes only stores in the target tenant where `status = active` and `deleted_at is null`.
- `storeIds` includes the tenant admin account's active rows in `auth_account_store_access`, filtered to active, undeleted stores.
- `defaultStoreId` mirrors `auth_accounts.default_store_id`.

### `PATCH /api/v1/platform/tenants/{tenantId}`
The existing tenant update request also accepts optional tenant-admin store authorization fields:

```json
{
  "tenantCode": "20000000",
  "displayName": "Tenant name",
  "status": "active",
  "defaultLocale": "zh-CN",
  "adminStoreIds": [
    "20000000-0000-0000-0000-000000000983",
    "20000000-0000-0000-0000-000000000984"
  ],
  "defaultAdminStoreId": "20000000-0000-0000-0000-000000000984"
}
```

Rules:
- When `adminStoreIds` is absent, existing tenant-admin store authorization is unchanged.
- When `adminStoreIds` is present, it must be non-empty and every store id must belong to the same tenant, be active, and be undeleted.
- `defaultAdminStoreId` is required when `adminStoreIds` is present and must be inside `adminStoreIds`.
- Saving replaces the tenant admin's active `auth_account_store_access` rows for that tenant and updates `auth_accounts.default_store_id`.
- Invalid, inactive, deleted, null, or cross-tenant store ids return `REQUEST_INVALID` with HTTP 400.

## Compatibility
Existing callers that do not send `adminStoreIds` or `defaultAdminStoreId` keep the previous tenant update behavior. No database migration is required.
