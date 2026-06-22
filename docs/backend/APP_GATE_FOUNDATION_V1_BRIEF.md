# Reservation Queue App Gate Foundation V1 Brief

## Source Of Truth

This brief is the development basis for Reservation Queue App Gate Foundation V1. Future implementation must use this document as the source of truth instead of relying on chat memory.

## Background

The current reservation queue platform already has several working business slices, including:

- Reservation creation
- Walk-in direct seating
- Seating-related persistence boundaries
- Cleaning start and complete

This round must not refactor the existing reservation, walk-in, seating, or cleaning state machines. It must not delete, rename, or reshape existing API paths. The gap is the missing platform-level app gate rules foundation around the already-working business flows.

## Selected Approach

Use approach B: create an independent `appgate` backend module with unified annotation and guard-based API interception.

Business endpoints declare their app line and required permission. App access evaluation is centralized in `AppGateService`.

## Scope

### 1. V002 Migration

Add a new migration that creates:

- `platform_apps`
- `tenant_app_entitlements`
- `store_app_settings`
- `app_gate_audit_logs`

Seed the platform app:

| Field | Value |
| --- | --- |
| `app_key` | `reservation_queue` |
| `app_name` | `订位排号系统` |
| `status` | `active` |
| `default_entry_route` | `/stores/:storeId/staff` |

Migration compatibility requirements:

- Insert default `reservation_queue = enabled` entitlement rows for all existing tenants.
- Insert default `reservation_queue is_enabled = true` setting rows for all existing stores.
- Existing reservation queue business flows must not suddenly return 403 after migration.

### 2. Appgate Backend Module

Add a focused `appgate` module containing:

- `AppGateService`
- `AppGateDecision`
- `AppGateDenyReason`
- JPA entities
- Spring Data repositories
- Persistence adapters
- A unified app gate error response mapper

The app gate decision order is fixed:

1. Platform app exists and is `active`.
2. Tenant entitlement is `enabled` or valid `trial`.
3. Tenant entitlement is not expired.
4. Store app setting is enabled.
5. Actor can access the store.
6. Actor has the required permission.

### 3. Unified Guard

Add a unified guard mechanism, such as an annotation plus interceptor/aspect:

```text
@RequireAppAccess(appKey = "reservation_queue", permission = "reservation.create")
```

Apply it to existing endpoints:

| Existing Capability | App Key | Permission |
| --- | --- | --- |
| Create reservation | `reservation_queue` | `reservation.create` |
| Walk-in direct seating | `reservation_queue` | `walkin.direct_seating.create` |
| Start cleaning | `reservation_queue` | `cleaning.start` |
| Complete cleaning | `reservation_queue` | `cleaning.complete` |

Keep the current permission keys in V1. Do not rename them to `seating.assign` or any future permission model in this round.

### 4. Error Response

App gate denial must return the existing error envelope shape:

```json
{
  "success": false,
  "error": {
    "code": "STORE_APP_NOT_ENABLED",
    "messageKey": "appgate.store_app_not_enabled",
    "details": {}
  }
}
```

Required deny reason codes:

- `APP_DISABLED`
- `TENANT_APP_NOT_ENABLED`
- `TENANT_APP_EXPIRED`
- `STORE_APP_NOT_ENABLED`
- `PERMISSION_DENIED`

All app gate denials should use HTTP 403 unless a later API contract explicitly separates unauthenticated access into HTTP 401.

### 5. Available Apps API

Add:

```text
GET /api/me/apps?storeId=xxx
```

The endpoint returns apps available to the current actor for the current store.

Rules:

- `entry_visible = false` hides the frontend entry only.
- `is_enabled = false` denies backend API access.
- Manual URL access must still be blocked by the backend guard.

The response must include enough data for the staff home page to render the reservation queue entry without hardcoding availability.

### 6. Frontend Staff Home

Update `StoreStaffHomePage.vue` to call `/api/me/apps?storeId=xxx`.

The page should render the `reservation_queue` entry only when the backend says it is visible and available for the current actor and store.

Frontend hiding is only a user experience layer. Backend guard enforcement is mandatory.

## Out Of Scope

This round must not:

- Refactor reservation, walk-in, seating, or cleaning state machines.
- Delete or rename existing API paths.
- Depend only on frontend menu hiding for access control.
- Introduce a complex rules engine.
- Implement wine storage, member wallet, ordering, payment, POS, marketing, or BI features.
- Implement a full RBAC administration UI.
- Replace the current permission keys.
- Break existing reservation queue flows after migration.

## Testing Focus

Add or update tests for:

- V002 migration structure and default backfill behavior.
- `AppGateService` decision order and deny reasons.
- Unified guard integration.
- Existing reservation, walk-in, and cleaning regression paths.
- Tenant app disabled returns 403 with a clear code.
- Store app disabled returns 403 with a clear code.
- Missing permission returns `PERMISSION_DENIED`.
- `/api/me/apps` returns the expected visible and available app entries.
- `StoreStaffHomePage.vue` renders entries from `/api/me/apps`.
- Existing hardcoded Controller and Vue boundary whitelist tests are updated intentionally.

## Acceptance Criteria

- Existing tenants and stores can still use `reservation_queue` after V002 migration.
- Disabling a tenant's `reservation_queue` entitlement blocks related APIs with HTTP 403 and a clear app gate code.
- Disabling a store's `reservation_queue` setting hides the staff entry and blocks direct API access.
- Manual URL entry cannot bypass backend gate checks.
- A user without `cleaning.complete` cannot complete cleaning.
- A user without `reservation.create` cannot create a reservation.
- `/api/me/apps?storeId=xxx` returns correct app availability for the current actor and store.
- App enable, disable, visibility, and setting changes are written to app gate audit logs.

## Implementation Principle

This is an app gate shell around existing business flows. It must be additive, explicit, and compatible with the current working reservation queue slices.
