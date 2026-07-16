# One-Click Store Entry Login Design

## Context

Tenant staff and tenant administrators can have access to more than one store. The current staff login flow authenticates the account, loads the authorised store list, and then leaves the user on the login page until they select a store and click a second "Enter store" button. The authenticated staff workbench and tenant administration shell already provide a store switcher, so the extra confirmation delays entry without adding a lasting capability.

Store host aliases already provide authoritative login context. A `tenant_host_aliases` row with `alias_type = store` maps a host prefix to both a tenant and a default store. Login already verifies that the account is authorised for that aliased store, but the login response does not expose the resolved entry store to the frontend.

## Goal

After one successful login submission, route the user directly to the store represented by the current host. When the host does not represent a store, route to the account's default store. Keep the existing in-application store switcher for later changes.

## Scope

This change covers tenant staff and tenant administrator login routing. Platform administrator routing, captcha validation, credential validation, remembered-account behaviour, session lifetime, store authorisation, and the authenticated store switcher remain unchanged.

No database schema or migration is required.

## Options Considered

### Add an explicit login entry store to the login response

Resolve the store alias on the backend and return its authorised store ID as a nullable login-response field. The frontend uses this value only for the immediate post-login route.

This is the selected approach because it preserves the distinction between the host-selected entry store and the account's persistent default store. It also supports aliases whose code is different from the store code.

### Override `AuthUser.defaultStoreId` for the login response

The backend could replace the account default with the alias store during login. This avoids a new response field but gives `defaultStoreId` two meanings and makes the login response disagree with a later `/api/v1/auth/me` response. This approach is rejected.

### Match the host prefix to store codes in the frontend

The frontend could fetch authorised stores and compare the host prefix with `storeCode`. This requires an extra request before routing and fails when a host alias differs from a store code. This approach is rejected.

## API Design

`POST /api/v1/auth/login` gains one additive nullable response field:

```json
{
  "success": true,
  "user": {
    "accountId": "...",
    "defaultStoreId": "...",
    "storeIds": ["..."]
  },
  "entryStoreId": "...",
  "expiresAt": "..."
}
```

`entryStoreId` is the active store referenced by the resolved `store` host alias. It is `null` for platform hosts, tenant-level hosts, tenant-code login, and root-domain legacy login.

The resolved entry store is login-request context, not session identity. It therefore belongs on `AuthLoginResponse`, not `AuthUserResponse` or `GET /api/v1/auth/me`.

The existing store-alias login query continues to require active account authorisation for the aliased store. Returning `entryStoreId` does not grant access.

## Backend Design

The auth repository returns the matched account together with the nullable store ID from the resolved login context. Platform, tenant-code, and legacy account resolution wrap the account with a `null` entry store. Store-alias resolution returns the alias's `default_store_id` after the existing active-store and account-access checks pass.

`AuthApplicationService.login` uses the account exactly as it does today for password validation, login auditing, session creation, and principal construction. It carries the separate entry store into `AuthLoginResult`. `AuthController` maps that value onto `AuthLoginResponse`.

Account `default_store_id` remains unchanged in persistence, the session principal, and `AuthUserResponse`.

## Frontend Routing Design

The frontend login API type adds `entryStoreId: string | null`. `authSession.loginWithPassword` returns both the authenticated user and the entry store ID to the login page.

Post-login routing keeps the existing precedence:

1. An explicit router redirect already supplied by the authentication guard.
2. Platform administrator home for platform administrators.
3. Missing-store handling when a non-platform account has no authorised stores.
4. For tenant administrators and staff, the authorised `entryStoreId` from the login response.
5. The account's authorised `defaultStoreId`.
6. The first authorised store ID.

Tenant administrators route to `/stores/{storeId}/admin/profile`. Staff route to `/stores/{storeId}/staff`.

The login page removes the post-authentication `pendingStoreSelection` state, store selection dropdown, and second "Enter store" button. The pre-login authorised-store explanatory card is updated to say that login enters the domain/default store directly and that users can switch inside the workbench.

## Error Handling And Security

- The frontend accepts `entryStoreId` only when it is present in `user.storeIds`; an inconsistent or missing value falls back to the authorised account default and then the first authorised store.
- Accounts without any authorised store retain the existing logout-and-error flow.
- Store-alias login for an account without access to the aliased store remains `INVALID_CREDENTIALS` and does not reveal the store mapping.
- Failed login and captcha refresh behaviour remains unchanged.
- No public store-resolution endpoint is added.

## Testing

Backend integration tests cover:

- authorised staff login through a store alias returns that alias store as `entryStoreId`, even when the account default is another authorised store;
- tenant-code or tenant-level login returns `entryStoreId: null`;
- an account without access to the aliased store still receives `INVALID_CREDENTIALS`.

Frontend validation covers:

- staff and tenant administrator routing prefer an authorised `entryStoreId`;
- account default and first authorised store remain ordered fallbacks;
- the pending store-selection state and second entry button are removed;
- existing workbench store switching remains available.

The frontend production build and focused backend tests must pass before completion.

## Release And Rollback

The backend response change is additive, so an older frontend ignores `entryStoreId`. Deploy the backend before or together with the frontend.

Rollback consists of restoring the previous frontend login routing and removing the additive response field and resolved-entry-store propagation. No data rollback is required.
