# Platform Tenant API Contract

## Scope
Platform tenant management is owned by the platform back office and is guarded by the `platform_admin` role plus `platform.tenant.manage`.

## Tenant Onboarding

### `POST /api/v1/platform/tenants`

The tenant create request accepts `onboardingMode`.

Rules:
- Omitted `onboardingMode` defaults to `single_store` for backward compatibility.
- `single_store` creates the tenant, a default operating entity, a default store, the tenant admin account, and the tenant admin's default store authorization.
- `group_multi_store` creates the tenant, a default operating entity, and the tenant admin account, but does not create a store. Platform admins continue in the tenant structure panel and add branches under the default operating entity.
- `V038__tenant_default_operating_entity_backfill.sql` backfills the same default operating entity for existing non-deleted tenants that have no non-deleted operating entity. It does not create stores.
- New tenant creation persists the tenant code as a `tenant_host_aliases` row with `alias_type = tenant`, `default_store_id = null`, and status derived from the tenant status.
- `V039__tenant_host_alias_backfill.sql` backfills tenant-code host aliases for existing non-deleted tenants when no active alias already uses the same prefix.
- When `rpb.host-prefix.base-host` is configured, active tenant and store host aliases also persist a `public_host_bindings` row with the full hostname and `tls_status = pending`. Inactive, archived, or deleted aliases archive their public host binding.
- `V040__public_host_bindings.sql` creates the public hostname / TLS coverage status table used by the SAN certificate automation. The application stores status only; certificate files and private keys remain outside the business database.

## Operating Entities

Operating entities model separate business operators inside one management tenant. They are platform-managed; tenant admins cannot create or edit them.

### `GET /api/v1/platform/tenants/{tenantId}/operating-entities`

Returns active, non-deleted operating entities in the tenant.

Response:

```json
{
  "success": true,
  "operatingEntities": [
    {
      "id": "50000000-0000-0000-0000-000000000983",
      "tenantId": "10000000-0000-0000-0000-000000000983",
      "entityCode": "lsc106-entity",
      "displayName": "LSC106 经营主体",
      "status": "active",
      "defaultLocale": "en-SG",
      "contactPhone": "+6590000106",
      "address": "106 Orchard Road",
      "principalName": "LSC106 Manager",
      "deleted": false
    }
  ]
}
```

### `POST /api/v1/platform/tenants/{tenantId}/operating-entities`
### `PATCH /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}`

Request:

```json
{
  "entityCode": "lsc106-entity",
  "displayName": "LSC106 经营主体",
  "status": "active",
  "defaultLocale": "en-SG",
  "contactPhone": "+6590000106",
  "address": "106 Orchard Road",
  "principalName": "LSC106 Manager"
}
```

Rules:
- `entityCode` and `displayName` are required on create.
- First slice allows `status = active|inactive`; archive is not exposed as an API command.
- `entityCode` is unique per tenant among non-deleted operating entities.
- Invalid tenant ids return `TENANT_NOT_FOUND`; invalid request payloads return `REQUEST_INVALID`; duplicate codes return `OPERATING_ENTITY_CODE_CONFLICT`.

## Tenant Stores

Stores remain tenant-scoped operational units. New stores created through the platform API must point to an active operating entity in the same tenant.

### `GET /api/v1/platform/tenants/{tenantId}/stores`

Returns non-deleted tenant stores with their operating entity summary when assigned.

### `POST /api/v1/platform/tenants/{tenantId}/stores`
### `PATCH /api/v1/platform/tenants/{tenantId}/stores/{storeId}`

Request:

```json
{
  "operatingEntityId": "50000000-0000-0000-0000-000000000983",
  "storeCode": "lsc106",
  "storeName": "LSC106 门店",
  "status": "active",
  "timezone": "Asia/Singapore",
  "locale": "en-SG",
  "dateFormat": "DD-MM-YYYY",
  "timeFormat": "HH:mm",
  "currency": "SGD"
}
```

Rules:
- `operatingEntityId`, `storeCode`, and `storeName` are required on create.
- First slice allows `status = created|active|inactive`; store archive is intentionally not exposed.
- `operatingEntityId` must reference an active, non-deleted operating entity in the same tenant.
- `storeCode` is unique per tenant among non-deleted stores.
- Invalid tenant ids return `TENANT_NOT_FOUND`; invalid store ids return `STORE_NOT_FOUND`; invalid operating entity ids return `REQUEST_INVALID`; duplicate store codes return `STORE_CODE_CONFLICT`.

### `DELETE /api/v1/platform/tenants/{tenantId}/stores/{storeId}`

Soft-deletes a tenant store and disables its active operational entry points.

Response:

```json
{
  "success": true,
  "store": {
    "id": "20000000-0000-0000-0000-000000000983",
    "tenantId": "10000000-0000-0000-0000-000000000983",
    "storeCode": "lsc106",
    "storeName": "LSC106 门店",
    "status": "inactive",
    "deleted": true
  }
}
```

Rules:
- This is a soft delete. The store row remains for historical references, but `stores.deleted_at` is set and `status` becomes `inactive`.
- The store is removed from platform store lists and tenant-admin authorisation options because those APIs return only non-deleted stores.
- Active `tenant_host_aliases` and `public_host_bindings` for the store are archived.
- Active `auth_account_store_access` rows for the store are soft-deleted.
- Store manager accounts created for that store are disabled and soft-deleted.
- If any active account used the deleted store as `default_store_id`, its default is moved to the first remaining active store access in the same tenant, or cleared when none remains.
- Active or suspended store-scoped subscription items for the store are marked `cancelled` so the store is no longer available for single-store renewal.
- The parent subscription amount and period are recomputed from remaining active store-scoped subscription items.
- Invalid tenant ids return `TENANT_NOT_FOUND`; invalid, deleted, or cross-tenant store ids return `STORE_NOT_FOUND`.

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
Existing callers that do not send `onboardingMode` keep the previous single-store bootstrap behavior. Existing callers that do not send `adminStoreIds` or `defaultAdminStoreId` keep the previous tenant update behavior. Existing store create, update, and list endpoints are unchanged. Store deletion is additive and uses existing soft-delete/status fields, so it requires no database migration. `V038` and `V039` are data-only migrations for operating entity and tenant-code host alias backfills. `V040` is additive and does not change existing tenant, store, or login API responses.
