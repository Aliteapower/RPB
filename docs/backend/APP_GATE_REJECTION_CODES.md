# App Gate Rejection Codes

This document defines the stable operational rejection-code catalog for App Gate.

V1 should prefer HTTP `403 Forbidden` for most business denials to avoid exposing entitlement internals. Use the reject code to distinguish the operational reason.

## Current V1 Runtime Codes

The current implementation returns these enum names from `AppGateDenyReason`:

```text
APP_DISABLED
TENANT_APP_NOT_ENABLED
TENANT_APP_EXPIRED
STORE_APP_NOT_ENABLED
STORE_ACCESS_DENIED
PERMISSION_DENIED
```

These are preserved for existing runtime compatibility. Future work should map or evolve them toward the canonical codes below only in an approved code-change round.

## Canonical Code Catalog

| Code | Meaning | HTTP status | Audit | Frontend exposure | Message key | Allowed details |
|---|---|---:|---|---|---|---|
| `APP_NOT_FOUND` | The requested platform app key does not exist. V1 may intentionally return this as a generic app denial to prevent app enumeration. | `403` preferred; `404` optional in admin-only contexts | Yes, when tenant/store context is known | Yes, as a generic unavailable app message | `appgate.app_not_found` | `appKey` only if safe |
| `APP_INACTIVE` | The platform app exists but is not active. | `403` | Yes | Yes | `appgate.app_inactive` | `appKey` |
| `TENANT_APP_NOT_ENTITLED` | The tenant has no active entitlement for the app, or entitlement is missing/disabled/expired where a generic tenant denial is preferred. | `403` | Yes | Yes | `appgate.tenant_app_not_entitled` | `appKey`, `storeId` |
| `TENANT_APP_SUSPENDED` | The tenant entitlement exists but is suspended, expired, or otherwise administratively blocked. | `403` | Yes | Yes, without internal commercial details | `appgate.tenant_app_suspended` | `appKey`, `storeId` |
| `STORE_APP_DISABLED` | The store app setting exists but is disabled or hidden for operational use. | `403` | Yes | Yes | `appgate.store_app_disabled` | `appKey`, `storeId` |
| `STORE_APP_NOT_CONFIGURED` | The store app setting does not exist for the tenant/store/app scope. | `403` | Yes | Yes | `appgate.store_app_not_configured` | `appKey`, `storeId` |
| `APP_PERMISSION_DENIED` | The actor does not have the required permission key. | `403` | Yes | Yes | `appgate.permission_denied` | `appKey`, `permission`, `storeId` |
| `APP_GATE_SCOPE_MISMATCH` | The actor, tenant, or store scope does not match the target request scope. | `403` | Yes | Yes, as access denied | `appgate.scope_mismatch` | `appKey`, `permission`, `storeId` |
| `APP_GATE_ACTOR_MISSING` | The request has no authenticated actor or server-side actor context. | `401` | Yes, when tenant/store context is known | Yes | `appgate.actor_missing` | None or `storeId` |
| `APP_GATE_STORE_MISSING` | The protected endpoint requires store scope but no trusted store id was provided or it was malformed. | `400` | Yes, when actor/tenant context is known | Yes | `appgate.store_missing` | `appKey`, `permission` |
| `APP_GATE_UNKNOWN_DENIAL` | Unexpected App Gate failure. Business request must not be allowed. | `500` | Best effort; do not expose internals if audit also fails | Yes, as generic failure | `appgate.unknown_denial` | No sensitive details |

## Current-to-Canonical Mapping

| Current V1 code | Canonical meaning |
|---|---|
| `APP_DISABLED` | `APP_INACTIVE`; if app row is missing, operationally equivalent to `APP_NOT_FOUND` |
| `TENANT_APP_NOT_ENABLED` | `TENANT_APP_NOT_ENTITLED` |
| `TENANT_APP_EXPIRED` | `TENANT_APP_SUSPENDED` |
| `STORE_APP_NOT_ENABLED` | `STORE_APP_DISABLED` or `STORE_APP_NOT_CONFIGURED` depending on whether the row exists |
| `STORE_ACCESS_DENIED` | `APP_GATE_SCOPE_MISMATCH` |
| `PERMISSION_DENIED` | `APP_PERMISSION_DENIED`; when actor or store scope is missing in current V1, this may also represent `APP_GATE_ACTOR_MISSING` or `APP_GATE_STORE_MISSING` |

## Detail Safety Rules

Allowed `details` fields:

```text
appKey
permission
storeId
```

Use these only when they help the frontend or support staff and do not expose sensitive internals.

Do not expose:

- internal entitlement configuration.
- pricing or commercial status details.
- tenant secrets.
- raw request body.
- stack traces.
- database identifiers unrelated to the current actor scope.
- production configuration.

## Audit Rules

Every denial should write `app_gate_audit_logs` with:

```text
action = APP_GATE_DENIED
```

The audit row should include tenant scope, store scope when available, actor information when available, app key, permission, reject code, target handler or API, request method, request path, occurrence time, and non-sensitive metadata.

Current V1 stores the runtime denial snapshot in `after_json` and uses `created_at` as occurrence time.

## HTTP Status Guidance

- `403 Forbidden`: entitlement, app inactive, store disabled, scope mismatch, or permission denied.
- `404 Not Found`: optional for app not found in tightly controlled admin contexts; V1 business APIs should prefer `403`.
- `400 Bad Request`: missing or malformed store scope or invalid App Gate scope parameter.
- `401 Unauthorized`: missing or unauthenticated actor.
- `500 Internal Server Error`: unknown denial or unexpected App Gate failure.

