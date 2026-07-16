# One-Click Store Entry Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route tenant users directly to the current host's authorised store, or their account default store, after one successful login submission.

**Architecture:** Carry the store resolved from a `store` host alias as a separate nullable `entryStoreId` on the login response without changing the account principal or stored default. The login page validates that entry store against the returned authorised store IDs, applies deterministic fallbacks, and routes directly to the staff or tenant-admin workbench while retaining the existing in-workbench switcher.

**Tech Stack:** Java 21, Spring Boot, JDBC/PostgreSQL, JUnit 5/MockMvc, Vue 3, Pinia, TypeScript, vue-router, vue-i18n.

## Global Constraints

- Do not add or change database schema, migrations, dependencies, session identity, or account `default_store_id`.
- Keep explicit router redirects and platform-admin routing at their existing precedence.
- `entryStoreId` is additive on `POST /api/v1/auth/login` only; do not add it to `AuthUserResponse` or `GET /api/v1/auth/me`.
- Accept `entryStoreId` in the frontend only when it exists in `user.storeIds`.
- Fall back in order to an authorised account `defaultStoreId`, then the first authorised store.
- Preserve the existing missing-store logout/error flow and store-alias access denial.
- Route tenant administrators to `/stores/{storeId}/admin/profile` and staff to `/stores/{storeId}/staff`.
- Keep the authenticated staff and tenant-admin store switchers unchanged.
- Remove the login page's post-authentication store dropdown and second entry button.
- Keep Chinese and English login copy aligned.
- Deploy the additive backend response before or together with the frontend.

---

## File Structure

- `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java`: return the authenticated account together with the nullable store resolved from login context.
- `src/main/java/com/rpb/reservation/auth/application/AuthApplicationService.java`: preserve the resolved entry store through login without changing the principal.
- `src/main/java/com/rpb/reservation/auth/application/AuthLoginResult.java`: carry the nullable entry store to the controller.
- `src/main/java/com/rpb/reservation/auth/api/AuthLoginResponse.java`: expose the additive login-only `entryStoreId` response field.
- `src/main/java/com/rpb/reservation/auth/api/AuthController.java`: map the resolved UUID to the response string.
- `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`: prove store-alias response semantics and existing access denial.
- `src/types/auth.ts`: type the additive response field and the Pinia action result.
- `src/stores/authSession.ts`: retain the authenticated user and return login entry context to the page.
- `src/pages/LoginPage.vue`: remove the second step and route directly using the approved fallback order.
- `src/i18n/locales/zh-CN.ts`: describe direct entry and remove unused selection copy.
- `src/i18n/locales/en-SG.ts`: keep the English copy aligned.
- `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`: enforce direct-entry source structure and removal of the old second step.
- `docs/release-notes/2026-07-16-one-click-store-entry-login.md`: document behaviour, compatibility, validation, and rollback.

---

### Task 1: Add Authoritative Entry Store To The Login Response

**Files:**
- Modify: `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`
- Modify: `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java`
- Modify: `src/main/java/com/rpb/reservation/auth/application/AuthApplicationService.java`
- Modify: `src/main/java/com/rpb/reservation/auth/application/AuthLoginResult.java`
- Modify: `src/main/java/com/rpb/reservation/auth/api/AuthLoginResponse.java`
- Modify: `src/main/java/com/rpb/reservation/auth/api/AuthController.java`

**Interfaces:**
- Consumes: existing `HostPrefixContext`, `tenant_host_aliases.default_store_id`, `AuthAccountRecord`, and store-alias authorisation checks.
- Produces: `AuthLoginResponse.entryStoreId: String | null`; internal `AuthLoginAccountRecord(AuthAccountRecord account, UUID entryStoreId)`.

- [ ] **Step 1: Write the failing store-alias response test**

Add this static import:

```java
import static org.hamcrest.Matchers.nullValue;
```

Extend `storeHostAliasResolvesToOwningTenantForLogin` so its account default remains `VALIDATION_STORE_ID` while the store alias points to `AUTH_SECONDARY_STORE_ID`, then assert the two values remain distinct:

```java
login("lsc83.booking.yumstone.sg", null, "tenant_admin", "lsc83", "20000000", "393930")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.user.username").value("20000000"))
    .andExpect(jsonPath("$.user.roles[0]").value("tenant_admin"))
    .andExpect(jsonPath("$.user.storeIds").value(hasItem(AUTH_SECONDARY_STORE_ID.toString())))
    .andExpect(jsonPath("$.user.defaultStoreId").value(VALIDATION_STORE_ID.toString()))
    .andExpect(jsonPath("$.entryStoreId").value(AUTH_SECONDARY_STORE_ID.toString()));
```

In the successful request inside `alphanumericTenantHostUsesHostPrefixAsAuthoritativeTenantContext`, prove that a tenant-level host does not override the account default:

```java
login("lsc106.booking.yumstone.sg", null, "staff", null, "1000", "393930")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.user.username").value("1000"))
    .andExpect(jsonPath("$.user.roles[0]").value("store_staff"))
    .andExpect(jsonPath("$.entryStoreId").value(nullValue()));
```

Keep `storeHostAliasLoginRequiresAccountAccessToAliasedStore` unchanged so it continues proving that resolving an alias does not grant access.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -q "-Dtest=AuthApiIntegrationTest#storeHostAliasResolvesToOwningTenantForLogin" test
```

Expected: FAIL because `$.entryStoreId` does not exist.

- [ ] **Step 3: Return account and entry store separately from persistence**

Change `findActiveTenantAccountByLoginCodeAndUsername` to return `Optional<AuthLoginAccountRecord>`, select the resolved store as a separate column, and map it without changing the account default:

```java
public Optional<AuthLoginAccountRecord> findActiveTenantAccountByLoginCodeAndUsername(
    String loginCode,
    String actorType,
    String username
) {
    return jdbc.query(
        """
        with resolved_login_context as (
            select tenant.id as tenant_id,
                   null::uuid as default_store_id
            from tenants tenant
            where lower(tenant.tenant_code) = lower(?)
              and tenant.deleted_at is null
              and tenant.status = 'active'
            union
            select alias.tenant_id,
                   alias.default_store_id
            from tenant_host_aliases alias
            join tenants tenant
              on tenant.id = alias.tenant_id
             and tenant.deleted_at is null
             and tenant.status = 'active'
            left join stores store
              on store.id = alias.default_store_id
             and store.tenant_id = alias.tenant_id
            where lower(alias.alias_code) = lower(?)
              and alias.status = 'active'
              and alias.deleted_at is null
              and (
                  alias.alias_type = 'tenant'
                  or (
                      alias.alias_type = 'store'
                      and store.status = 'active'
                      and store.deleted_at is null
                  )
              )
        )
        select account.id, account.tenant_id, account.username, account.display_name,
               account.actor_type, account.status, account.password_hash, account.default_store_id,
               login_context.default_store_id as entry_store_id
        from auth_accounts account
        join resolved_login_context login_context on login_context.tenant_id = account.tenant_id
        where lower(account.username) = lower(?)
          and account.actor_type <> 'platform_admin'
          and account.deleted_at is null
          and (
              (
                  ? = 'tenant_admin'
                  and exists (
                      select 1
                      from auth_account_roles role
                      where role.account_id = account.id
                        and role.role_code = 'tenant_admin'
                        and role.deleted_at is null
                  )
                  and exists (
                      select 1
                      from auth_account_permissions permission
                      where permission.account_id = account.id
                        and permission.permission_code = 'tenant.admin.manage'
                        and permission.deleted_at is null
                  )
              )
              or (
                  ? = 'staff'
                  and exists (
                      select 1
                      from auth_account_roles role
                      where role.account_id = account.id
                        and role.role_code in ('tenant_admin', 'store_manager', 'store_staff')
                        and role.deleted_at is null
                  )
                  and exists (
                      select 1
                      from auth_account_store_access access
                      join stores store
                        on store.id = access.store_id
                       and store.tenant_id = access.tenant_id
                       and store.status = 'active'
                       and store.deleted_at is null
                      where access.account_id = account.id
                        and access.tenant_id = account.tenant_id
                        and access.deleted_at is null
                  )
              )
          )
          and (
              login_context.default_store_id is null
              or exists (
                  select 1
                  from auth_account_store_access access
                  where access.account_id = account.id
                    and access.tenant_id = account.tenant_id
                    and access.store_id = login_context.default_store_id
                    and access.deleted_at is null
              )
          )
        order by account.created_at, account.id
        """,
        (rs, rowNum) -> loginAccount(rs),
        loginCode,
        loginCode,
        username,
        actorType,
        actorType
    ).stream().findFirst();
}

private static AuthLoginAccountRecord loginAccount(ResultSet rs) throws SQLException {
    return new AuthLoginAccountRecord(
        account(rs),
        rs.getObject("entry_store_id", UUID.class)
    );
}

public record AuthLoginAccountRecord(
    AuthAccountRecord account,
    UUID entryStoreId
) {
}
```

- [ ] **Step 4: Propagate entry context through the application result**

Import `AuthLoginAccountRecord`, resolve it before extracting the account, and preserve the existing password/session/principal flow:

```java
AuthLoginAccountRecord loginAccount = resolveLoginAccount(request, hostPrefixContext, username)
    .orElseThrow(() -> new AuthApiException(AuthApiErrorCode.INVALID_CREDENTIALS));
AuthAccountRecord account = loginAccount.account();
```

Return the separate store value:

```java
return new AuthLoginResult(
    repository.principalFor(account),
    loginAccount.entryStoreId(),
    sessionToken,
    expiresAt
);
```

Change `resolveLoginAccount` to the following implementation so only an authoritative tenant host preserves the resolved entry store:

```java
private Optional<AuthLoginAccountRecord> resolveLoginAccount(
    LoginRequest request,
    HostPrefixContext hostPrefixContext,
    String username
) {
    HostPrefixContext context = hostPrefixContext == null ? HostPrefixContext.none() : hostPrefixContext;
    String requestedEntry = normalizedEntry(request.loginEntry());
    String requestedTenantCode = trimToNull(request.tenantCode());

    if (context.isPlatform()) {
        if (requestedEntry != null && !ENTRY_PLATFORM_ADMIN.equals(requestedEntry)) {
            return Optional.empty();
        }
        return repository.findActivePlatformAccountByUsername(username)
            .map(AuthApplicationService::withoutEntryStore);
    }

    if (context.isTenant()) {
        if (requestedTenantCode != null && !requestedTenantCode.equals(context.tenantCode())) {
            return Optional.empty();
        }
        String entry = requestedEntry == null ? ENTRY_TENANT_ADMIN : requestedEntry;
        if (!ENTRY_TENANT_ADMIN.equals(entry) && !ENTRY_STAFF.equals(entry)) {
            return Optional.empty();
        }
        return repository.findActiveTenantAccountByLoginCodeAndUsername(context.tenantCode(), entry, username);
    }

    if (requestedEntry == null) {
        return repository.findActiveAccountByUsername(username)
            .map(AuthApplicationService::withoutEntryStore);
    }
    if (ENTRY_PLATFORM_ADMIN.equals(requestedEntry)) {
        return repository.findActivePlatformAccountByUsername(username)
            .map(AuthApplicationService::withoutEntryStore);
    }
    if (ENTRY_TENANT_ADMIN.equals(requestedEntry) || ENTRY_STAFF.equals(requestedEntry)) {
        if (requestedTenantCode == null) {
            return Optional.empty();
        }
        return repository.findActiveTenantAccountByLoginCodeAndUsername(requestedTenantCode, requestedEntry, username)
            .map(loginAccount -> withoutEntryStore(loginAccount.account()));
    }
    return Optional.empty();
}

private static AuthLoginAccountRecord withoutEntryStore(AuthAccountRecord account) {
    return new AuthLoginAccountRecord(account, null);
}
```

Update the application result:

```java
public record AuthLoginResult(
    AuthPrincipal principal,
    UUID entryStoreId,
    String sessionToken,
    Instant expiresAt
) {
}
```

- [ ] **Step 5: Expose the additive API field**

Update the response record:

```java
public record AuthLoginResponse(
    boolean success,
    AuthUserResponse user,
    String entryStoreId,
    Instant expiresAt
) {
}
```

Map the field in `AuthController.login`:

```java
return new AuthLoginResponse(
    true,
    AuthUserResponse.from(result.principal()),
    result.entryStoreId() == null ? null : result.entryStoreId().toString(),
    result.expiresAt()
);
```

- [ ] **Step 6: Run focused auth tests and verify GREEN**

Run:

```powershell
mvn -q "-Dtest=AuthApiIntegrationTest#storeHostAliasResolvesToOwningTenantForLogin+storeHostAliasLoginRequiresAccountAccessToAliasedStore+alphanumericTenantHostUsesHostPrefixAsAuthoritativeTenantContext" test
```

Expected: PASS; the alias response exposes its store, the unauthorized alias remains rejected, and tenant-host login remains valid.

- [ ] **Step 7: Commit the backend contract**

```powershell
git add -- src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java src/main/java/com/rpb/reservation/auth/application/AuthApplicationService.java src/main/java/com/rpb/reservation/auth/application/AuthLoginResult.java src/main/java/com/rpb/reservation/auth/api/AuthLoginResponse.java src/main/java/com/rpb/reservation/auth/api/AuthController.java src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java
git commit -m "feat: return host entry store after login"
```

---

### Task 2: Route Directly And Remove The Second Login Step

**Files:**
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`
- Modify: `src/types/auth.ts`
- Modify: `src/stores/authSession.ts`
- Modify: `src/pages/LoginPage.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`

**Interfaces:**
- Consumes: `AuthLoginResponse.entryStoreId`, `AuthUser.storeIds`, `AuthUser.defaultStoreId`, and existing staff/tenant-admin routes.
- Produces: `AuthLoginSession { user: AuthUser; entryStoreId: string | null }` and direct post-login routing.

- [ ] **Step 1: Replace old source assertions with failing direct-entry assertions**

Replace `loginPageSeparatesPlatformTenantAndStaffEntrancesWithFutureStoreSelection` with:

```java
@Test
void loginPageSeparatesEntrancesAndRoutesTenantUsersDirectlyToAStore() throws Exception {
    Path pagePath = Path.of("src", "pages", "LoginPage.vue");
    Path authTypePath = Path.of("src", "types", "auth.ts");
    Path hostContextPath = Path.of("src", "utils", "hostContext.ts");
    String pageSource = FrontendSourceSupport.readString(pagePath);
    String source = pageSource
        + FrontendSourceSupport.readString(authTypePath)
        + FrontendSourceSupport.readString(hostContextPath);

    assertThat(source)
        .contains("platform-admin")
        .contains("tenant-admin")
        .contains("tenant-staff")
        .contains("login.entries.platformAdmin.title")
        .contains("login.entries.tenantAdmin.title")
        .contains("login.entries.tenantStaff.title")
        .contains("tenantCode")
        .contains("employeeUsername")
        .contains("loginUsername: '1000'")
        .contains("20000000")
        .contains("1000")
        .contains("entryStoreId")
        .contains("preferredLoginStoreId")
        .contains("tenantAdminStoreRoute")
        .contains("login.store.authorized")
        .contains("loginPayloadUsername")
        .contains("export type AuthLoginEntry = 'platform_admin' | 'tenant_admin' | 'staff'")
        .contains("loginEntry?: AuthLoginEntry")
        .contains("tenantCode?: string | null")
        .contains("resolveLoginHostContext")
        .contains("availableLoginEntries")
        .contains("hostContext.kind === 'platform'")
        .contains("hostContext.kind === 'tenant'")
        .contains("tenantCodeVisible")
        .contains("authLoginEntry")
        .contains("rememberAccount")
        .contains("rememberAccountStorageKey")
        .contains("login.remember.account")
        .doesNotContain("pendingStoreSelection")
        .doesNotContain("selectedStoreId")
        .doesNotContain("selectStoreAndContinue")
        .doesNotContain("login.store.switch")
        .doesNotContain("presetPassword: '393930'")
        .doesNotContain("password = ref('393930')");
}
```

Replace `staffLoginStoreSelectionLoadsStoreNamesInsteadOfShowingRawStoreIds` with:

```java
@Test
void tenantLoginPrefersAuthorizedEntryStoreThenAccountDefaultThenFirstStore() throws Exception {
    Path pagePath = Path.of("src", "pages", "LoginPage.vue");
    Path storePath = Path.of("src", "stores", "authSession.ts");
    Path authTypePath = Path.of("src", "types", "auth.ts");
    String source = FrontendSourceSupport.readString(pagePath)
        + FrontendSourceSupport.readString(storePath)
        + FrontendSourceSupport.readString(authTypePath);

    assertThat(source)
        .contains("entryStoreId: string | null")
        .contains("user.storeIds.includes(entryStoreId)")
        .contains("user.defaultStoreId && user.storeIds.includes(user.defaultStoreId)")
        .contains("user.storeIds[0] ?? ''")
        .contains("`/stores/${storeId}/staff`")
        .contains("`/stores/${storeId}/admin/profile`")
        .doesNotContain("<option v-for=\"store in authorizedStoreOptions\"")
        .doesNotContain("login.store.enter");
}
```

- [ ] **Step 2: Run focused UI validation and verify RED**

Run:

```powershell
mvn -q "-Dtest=AuthLoginUiValidationTest#loginPageSeparatesEntrancesAndRoutesTenantUsersDirectlyToAStore+tenantLoginPrefersAuthorizedEntryStoreThenAccountDefaultThenFirstStore" test
```

Expected: FAIL because the page still has pending store-selection state and the login action does not expose `entryStoreId`.

- [ ] **Step 3: Type and return login entry context**

Add to `src/types/auth.ts`:

```ts
export interface AuthLoginSession {
  user: AuthUser
  entryStoreId: string | null
}
```

Add the field to the existing response:

```ts
export interface AuthLoginResponse {
  success: true
  user: AuthUser
  entryStoreId: string | null
  expiresAt: string
}
```

Import `AuthLoginSession` in `authSession.ts` and change the action to:

```ts
async loginWithPassword(request: LoginRequest): Promise<AuthLoginSession> {
  const response = await login(request)
  this.user = response.user
  this.loaded = true
  this.authorizedStores = []
  this.storesLoaded = false
  return {
    user: response.user,
    entryStoreId: response.entryStoreId
  }
}
```

- [ ] **Step 4: Route directly using authorised fallbacks**

In `LoginPage.vue`, remove the `AuthStoreAccess` import, `pendingStoreSelection`, `selectedStoreId`, `authorizedStoreOptions`, `canChooseStore`, reset lines, `selectStoreAndContinue`, `storeOptionLabel`, `fallbackStoreAccess`, and `fallbackStoreLabel`.

Capture both login values:

```ts
const loginSession = await auth.loginWithPassword({
  username: loginPayloadUsername.value,
  password: password.value,
  captchaId: captcha.value.challengeId,
  captchaX: Math.round(captchaX.value),
  loginEntry: authLoginEntry(selectedEntry.value),
  tenantCode: selectedEntry.value.id === 'platform-admin' ? null : (resolvedTenantCode.value || null)
})
persistRememberedAccount()
await continueAfterLogin(loginSession.user, loginSession.entryStoreId)
```

Replace post-login routing with:

```ts
async function continueAfterLogin(user: AuthUser, entryStoreId: string | null): Promise<void> {
  if (typeof route.query.redirect === 'string') {
    await router.replace(route.query.redirect)
    return
  }

  if (selectedEntry.value.id === 'platform-admin' && user.roles.includes('platform_admin')) {
    await router.replace(auth.platformHomeRoute)
    return
  }

  if (!auth.isPlatformAdmin && user.storeIds.length === 0) {
    await stopMissingStoreScopeLogin()
    return
  }

  const storeId = preferredLoginStoreId(user, entryStoreId)
  if (selectedEntry.value.id === 'tenant-admin' && auth.isTenantAdmin) {
    await router.replace(tenantAdminStoreRoute(storeId))
    return
  }

  if (isStaffEntry.value) {
    await router.replace(storeRoute(storeId))
    return
  }

  await router.replace(auth.defaultHomeRoute)
}

function preferredLoginStoreId(user: AuthUser, entryStoreId: string | null): string {
  if (entryStoreId && user.storeIds.includes(entryStoreId)) {
    return entryStoreId
  }
  return user.defaultStoreId && user.storeIds.includes(user.defaultStoreId)
    ? user.defaultStoreId
    : (user.storeIds[0] ?? '')
}

function storeRoute(storeId: string): string {
  return `/stores/${storeId}/staff`
}

function tenantAdminStoreRoute(storeId: string): string {
  return `/stores/${storeId}/admin/profile`
}
```

Remove the complete `<section v-if="canChooseStore" class="store-selection">` block and remove `.store-selection` from the shared CSS selector plus its standalone rule.

- [ ] **Step 5: Align login copy**

Keep only these store keys in Chinese:

```ts
store: {
  authorized: '授权店面',
  authorizedDescription: '登录后直接进入当前域名或账号默认店面，可在工作台切换店面'
},
```

Keep only these store keys in English:

```ts
store: {
  authorized: 'Authorised stores',
  authorizedDescription: 'After sign-in, you enter the current domain store or your account default and can switch in the workbench.'
},
```

- [ ] **Step 6: Run focused UI validation and frontend build**

Run:

```powershell
mvn -q "-Dtest=AuthLoginUiValidationTest#loginPageSeparatesEntrancesAndRoutesTenantUsersDirectlyToAStore+tenantLoginPrefersAuthorizedEntryStoreThenAccountDefaultThenFirstStore" test
npm run build
```

Expected: both commands PASS with no TypeScript or Vue template errors.

- [ ] **Step 7: Commit the direct-entry frontend**

```powershell
git add -- src/types/auth.ts src/stores/authSession.ts src/pages/LoginPage.vue src/i18n/locales/zh-CN.ts src/i18n/locales/en-SG.ts src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java
git commit -m "feat: enter authorized store directly after login"
```

---

### Task 3: Release Note And Final Verification

**Files:**
- Create: `docs/release-notes/2026-07-16-one-click-store-entry-login.md`

**Interfaces:**
- Consumes: the completed backend and frontend behaviour from Tasks 1 and 2.
- Produces: operator-facing compatibility, validation, deployment, and rollback notes.

- [ ] **Step 1: Write the release note**

Create the following document:

```markdown
# One-Click Store Entry Login

## Version / Date

2026-07-16

## Changed

- Tenant staff and tenant administrators now enter the current store-domain workbench immediately after successful login.
- Tenant-level and legacy login fall back to the account default store, then the first authorised store.
- The login page no longer asks multi-store staff to select and confirm a store after authentication.
- Store switching remains available inside staff and tenant-admin workbenches.

## API

- `POST /api/v1/auth/login` adds nullable `entryStoreId`.
- The field is login context only and does not change `AuthUser.defaultStoreId` or `/api/v1/auth/me`.

## Permission And Security

- Store aliases still require existing `auth_account_store_access`.
- An unauthorised store-alias login remains `INVALID_CREDENTIALS`.

## Deployment

- Deploy the backend before or together with the frontend. The response change is additive and older frontends ignore it.

## Validation

- Focused auth API integration tests passed.
- Focused login UI validation tests passed.
- Frontend production build passed.
- Backend package build passed.

## Rollback Notes

- Restore the previous frontend login page and backend auth response implementation.
- No database or data rollback is required.
```

- [ ] **Step 2: Run full proportional verification**

Run:

```powershell
mvn -q "-Dtest=AuthApiIntegrationTest,AuthLoginUiValidationTest" test
npm run build
mvn -q -DskipTests package
git diff --check HEAD~2
git status --short
```

Expected: all test/build commands PASS, `git diff --check` prints nothing, and status lists only the new release note before its commit.

- [ ] **Step 3: Commit the release note**

```powershell
git add -- docs/release-notes/2026-07-16-one-click-store-entry-login.md
git commit -m "docs: release one-click store login entry"
```

- [ ] **Step 4: Confirm final repository state**

Run:

```powershell
git status --short
git log -4 --oneline
```

Expected: clean worktree and four latest commits consisting of the release note, frontend implementation, backend implementation, and approved design.
