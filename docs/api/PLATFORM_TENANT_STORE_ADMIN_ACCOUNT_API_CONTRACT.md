# Platform Tenant Store Admin Account API Contract V1

## Purpose

Allow platform administrators to maintain a tenant group administrator password separately from each branch store administrator password.

This contract extends the existing platform tenant store APIs. It does not add new paths, migrations, product line behavior, public host behavior, or tenant admin self-service behavior.

## Endpoints

```http
POST /api/v1/platform/tenants/{tenantId}/stores
PATCH /api/v1/platform/tenants/{tenantId}/stores/{storeId}
```

Both endpoints keep the existing platform administrator requirement:

```text
role: platform_admin
permission: platform.tenant.manage
```

## Request Fields

The existing `PlatformStoreMutationRequest` gains two optional fields:

```json
{
  "operatingEntityId": "uuid",
  "storeCode": "codex-lsc106",
  "storeName": "LSC106 分店",
  "status": "active",
  "timezone": "Asia/Singapore",
  "locale": "zh-CN",
  "dateFormat": "DD-MM-YYYY",
  "timeFormat": "HH:mm",
  "currency": "SGD",
  "adminUsername": "codex-lsc106-admin",
  "adminPassword": "DEF456"
}
```

Rules:

| Field | Create | Update | Rule |
| --- | --- | --- | --- |
| `adminUsername` | optional | optional | Trimmed login username for the branch administrator. On create, if omitted while `adminPassword` is present, the backend uses `storeCode`. On update, blank keeps the existing branch administrator username. |
| `adminPassword` | optional | optional | Six letters or digits, normalized with the existing lowercase bcrypt flow. Blank means no password change. |

Compatibility:

- Existing API clients that omit both fields keep the current store-only behavior.
- Adding branch admin fields must not change the tenant group administrator account or password.
- Raw password values must never be returned in responses or written to audit metadata.

## Persistence Behavior

When a branch admin password is provided, the backend creates or updates one branch administrator account:

| Column / relation | Value |
| --- | --- |
| `auth_accounts.actor_type` | `staff` |
| `auth_account_roles.role_code` | `store_manager` |
| `auth_accounts.default_store_id` | created or updated store id |
| `auth_account_store_access` | only the target tenant/store pair |
| permissions | same operational permissions used by tenant-created store staff |

The tenant group administrator remains the existing tenant account:

| Column / relation | Value |
| --- | --- |
| `auth_accounts.actor_type` | `tenant_admin` |
| username | tenant code |
| password source | tenant `initialPassword` on create, tenant `password` on update |

## Error Behavior

| Condition | HTTP | API code |
| --- | ---: | --- |
| Invalid admin password shape | 400 | `REQUEST_INVALID` |
| Missing tenant | 404 | `TENANT_NOT_FOUND` |
| Missing store | 404 | `STORE_NOT_FOUND` |
| Store code conflict | 409 | `STORE_CODE_CONFLICT` |
| Branch admin username conflict | 409 | `STORE_CODE_CONFLICT` |

`STORE_CODE_CONFLICT` is retained for username conflicts because the current controller maps platform structure conflicts through the existing platform tenant error envelope.

## Test Contract

Required backend coverage:

- Creating a group tenant with `initialPassword` keeps the group administrator login on that password.
- Creating a branch store with `adminUsername` and `adminPassword` creates a separate `staff` account with `store_manager` role.
- The branch administrator can log in with the branch password.
- The branch administrator cannot log in with the group administrator password.
- Invalid branch admin password returns `REQUEST_INVALID`.

Required frontend coverage:

- The group tenant form distinguishes group administrator password from branch administrator password.
- The branch store form exposes branch admin username and password fields.
- Create payload sends `adminUsername` and `adminPassword`.
- Update payload sends `adminUsername` and optional `adminPassword`.
