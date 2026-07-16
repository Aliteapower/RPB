# Tenant Staff Workbench Dynamic Brand Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each authorized store's configured sharing display name and its tenant logo in every tenant employee workbench top bar, replacing the fixed `食刻 · 管理` identity.

**Architecture:** Enrich the existing authenticated authorized-store read model instead of exposing tenant-admin management APIs or adding a second brand-metadata request. Keep the media asset UUID in the Java application model and construct an authenticated authorized-store Logo URL in the API mapper. On the frontend, resolve brand fallback rules in one pure module, render them in one focused component, and let `StaffHomeTopBar` compose the current authorized store by an explicit `storeId` prop.

> Implementation review correction: the initially referenced platform tenant-logo URL requires a platform-admin actor. The completed implementation therefore serves the same validated asset through `GET /api/v1/me/stores/{storeId}/logo/media/{assetId}`, which authenticates the current session and rejects stores outside the account's authorized catalog before loading media bytes. No management or queue-display permission is added.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, JdbcTemplate, PostgreSQL, JUnit 5, MockMvc, AssertJ, Vue 3 Composition API, TypeScript, Pinia, Vue I18n, scoped CSS, Maven, Vite.

## Global Constraints

- Title precedence is trimmed `shareDisplayName` → trimmed backend `storeName` → trimmed page `storeLabel` → localized `门店管理` / `Store management`.
- Never append the fixed `· 管理` suffix and never use the fixed `食` mark.
- Preserve the localized `门店员工` / `Store staff` kicker.
- Use the tenant-level logo and fall back to the resolved display name's first Unicode character when the URL is absent or the image fails.
- Apply the behavior to all eight pages that render `StaffHomeTopBar`.
- Preserve all existing workflows, permissions, App Gate behavior, routes, store switching, and phone/tablet responsive behavior.
- Do not add a database migration, management permission, dependency, or runtime configuration change.
- Any local backend or browser runtime must use the PostgreSQL pointer in `target/local-postgres-current.txt`.

---

## File Structure

- `src/main/java/com/rpb/reservation/auth/application/AuthStoreAccess.java`: authorized-store application read model; carries nullable sharing name and tenant logo media asset UUID.
- `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java`: reads the two brand fields inside the existing tenant/store-scoped authorized-store query.
- `src/main/java/com/rpb/reservation/auth/api/AuthStoreAccessResponse.java`: additive JSON fields and tenant-logo URL mapping.
- `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`: API shape, null fallback source data, and authorized-store isolation evidence.
- `src/types/auth.ts`: frontend authorized-store contract.
- `src/components/store/StoreSwitcher.vue`: keeps its legacy synthesized authorized-store fallback compatible with the additive TypeScript fields.
- `src/components/staff-home/staffBrandIdentity.ts`: pure display name, URL, and Unicode mark resolution.
- `src/components/staff-home/StaffBrandIdentity.vue`: logo/title presentation and failed-image fallback.
- `src/components/staff-home/StaffHomeTopBar.vue`: selects the current authorized store by explicit `storeId` and composes the brand component.
- Eight `src/pages/*Page.vue` files: pass their existing `storeId` into the shared top bar.
- `src/i18n/locales/zh-CN.ts` and `src/i18n/locales/en-SG.ts`: localized generic fallback and logo alternative text.
- `src/test/java/com/rpb/reservation/appgate/ui/StaffWorkbenchBrandIdentityUiValidationTest.java`: focused frontend source contract.
- `docs/release-notes/2026-07-16-staff-workbench-dynamic-brand-identity.md`: release, verification, and rollback evidence.

### Task 1: Define the Failing Authorized-Store Brand API Contract

**Files:**
- Modify: `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`
- Test: `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`

**Interfaces:**
- Consumes: existing `GET /api/v1/me/stores` authenticated session contract.
- Produces: failing integration assertions for nullable `shareDisplayName` and `tenantLogoMediaUrl`, while retaining the existing active authorized-store filtering.

- [ ] **Step 1: Add a stable tenant-logo fixture identifier**

Add beside `AUTH_SECONDARY_STORE_ID`:

```java
private static final UUID AUTH_TENANT_LOGO_ASSET_ID = UUID.fromString("90000000-0000-0000-0000-000000000984");
private static final UUID AUTH_FOREIGN_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000985");
private static final UUID AUTH_FOREIGN_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000985");
```

- [ ] **Step 2: Reset the brand fixture in `setUp()`**

Add after the tenant-code reset and before tests create brand data:

```java
jdbc.update("update tenants set logo_media_asset_id = null where id = ?", VALIDATION_TENANT_ID);
jdbc.update("update stores set share_display_name = null where tenant_id = ?", VALIDATION_TENANT_ID);
jdbc.update("delete from call_screen_media_assets where id = ?", AUTH_TENANT_LOGO_ASSET_ID);
jdbc.update("delete from stores where id = ?", AUTH_FOREIGN_STORE_ID);
jdbc.update("delete from tenants where id = ?", AUTH_FOREIGN_TENANT_ID);
```

- [ ] **Step 3: Configure the current-store test with sharing name and logo**

At the start of `currentUserStoresReturnsAuthorizedActiveStoresWithDefaultFlag`, after creating/granting the secondary store, add:

```java
jdbc.update(
    """
    insert into call_screen_media_assets (
        id, owner_scope, tenant_id, media_kind, content_type,
        byte_size, original_filename, storage_key, status
    )
    values (?, 'tenant', ?, 'image', 'image/png',
            128, 'auth-brand-logo.png', 'tests/auth-brand-logo.png', 'active')
    """,
    AUTH_TENANT_LOGO_ASSET_ID,
    VALIDATION_TENANT_ID
);
jdbc.update(
    "update tenants set logo_media_asset_id = ? where id = ?",
    AUTH_TENANT_LOGO_ASSET_ID,
    VALIDATION_TENANT_ID
);
jdbc.update(
    "update stores set share_display_name = '认证分享门店' where id = ? and tenant_id = ?",
    VALIDATION_STORE_ID,
    VALIDATION_TENANT_ID
);
```

- [ ] **Step 4: Add additive-field assertions**

After the existing default-store assertions, add:

```java
assertThat(defaultStore.path("shareDisplayName").asText()).isEqualTo("认证分享门店");
assertThat(defaultStore.path("tenantLogoMediaUrl").asText()).isEqualTo(
    "/api/v1/platform/tenants/" + VALIDATION_TENANT_ID + "/logo/media/" + AUTH_TENANT_LOGO_ASSET_ID
);
```

After the existing secondary-store assertions, add:

```java
assertThat(secondaryStore.path("shareDisplayName").isNull()).isTrue();
assertThat(secondaryStore.path("tenantLogoMediaUrl").asText()).isEqualTo(
    "/api/v1/platform/tenants/" + VALIDATION_TENANT_ID + "/logo/media/" + AUTH_TENANT_LOGO_ASSET_ID
);
```

The two-store assertion continues to prove the response contains only stores explicitly granted to the logged-in account; `platformAdminCurrentStoresDoesNotExposeTenantStoreSwitchTargets` continues to prove the platform-admin response is empty.

- [ ] **Step 5: Add an explicit foreign-tenant branding isolation test**

Add:

```java
@Test
void currentUserStoresDoesNotExposeForeignTenantBranding() throws Exception {
    upsertForeignBrandStore();

    MvcResult login = login("1000", "393930")
        .andExpect(status().isOk())
        .andReturn();
    Cookie sessionCookie = login.getResponse().getCookie("RPB_SESSION");

    MvcResult result = mockMvc.perform(get("/api/v1/me/stores").cookie(sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andReturn();

    JsonNode stores = objectMapper.readTree(result.getResponse().getContentAsString()).path("stores");
    assertThat(stores).hasSize(1);
    stores.forEach(store -> {
        assertThat(store.path("tenantId").asText()).isEqualTo(VALIDATION_TENANT_ID.toString());
        assertThat(store.path("shareDisplayName").asText()).isNotEqualTo("外部租户品牌");
    });
}
```

Add the fixture helper beside `upsertAuthSecondaryStore`:

```java
private void upsertForeignBrandStore() {
    jdbc.update(
        """
        insert into tenants (id, tenant_code, display_name, status, default_locale)
        values (?, 'auth-foreign', '认证外部租户', 'active', 'zh-CN')
        """,
        AUTH_FOREIGN_TENANT_ID
    );
    jdbc.update(
        """
        insert into stores (
            id, tenant_id, store_code, display_name, share_display_name, status,
            timezone, locale, date_format, time_format, currency
        )
        values (?, ?, 'auth-foreign-store', '认证外部门店', '外部租户品牌', 'active',
                'Asia/Singapore', 'zh-CN', 'DD-MM-YYYY', 'HH:mm', 'SGD')
        """,
        AUTH_FOREIGN_STORE_ID,
        AUTH_FOREIGN_TENANT_ID
    );
}
```

- [ ] **Step 6: Run the API test and verify RED**

Run:

```powershell
mvn "-Dtest=AuthApiIntegrationTest#currentUserStoresReturnsAuthorizedActiveStoresWithDefaultFlag+currentUserStoresDoesNotExposeForeignTenantBranding" test
```

Expected: FAIL because `shareDisplayName` and `tenantLogoMediaUrl` are missing from authorized-store JSON.

- [ ] **Step 7: Commit the failing contract**

```powershell
git add -- src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java
git commit -m "test: define authorized store brand metadata"
```

### Task 2: Implement the Authorized-Store Brand Read Model

**Files:**
- Modify: `src/main/java/com/rpb/reservation/auth/application/AuthStoreAccess.java`
- Modify: `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java`
- Modify: `src/main/java/com/rpb/reservation/auth/api/AuthStoreAccessResponse.java`
- Test: `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`

**Interfaces:**
- Consumes: `stores.share_display_name`, `tenants.logo_media_asset_id`, the existing account/store access predicates, and `CallScreenMediaService.tenantLogoMediaUrl(UUID tenantId, UUID assetId)`.
- Produces: application accessors `shareDisplayName()` and `tenantLogoMediaAssetId()` plus JSON fields `shareDisplayName` and `tenantLogoMediaUrl`.

- [ ] **Step 1: Extend `AuthStoreAccess` without HTTP concerns**

Insert the two fields after `storeName`:

```java
String storeName,
String shareDisplayName,
UUID tenantLogoMediaAssetId,
String status,
```

- [ ] **Step 2: Select and map the brand data in the existing scoped query**

In `AuthRepository.authorizedStores`, add to the select list after `store.display_name`:

```sql
store.share_display_name,
tenant.logo_media_asset_id,
```

Add to the `AuthStoreAccess` constructor after `rs.getString("display_name")`:

```java
rs.getString("share_display_name"),
rs.getObject("logo_media_asset_id", UUID.class),
```

Do not alter joins, tenant predicates, active/deleted filters, ordering, or account arguments.

- [ ] **Step 3: Add explicit response fields and construct the media URL in the API layer**

Import:

```java
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
```

Add response record fields after `storeName`:

```java
String shareDisplayName,
String tenantLogoMediaUrl,
```

Add mapper arguments after `store.storeName()`:

```java
store.shareDisplayName(),
tenantLogoMediaUrl(store),
```

Add inside `AuthStoreAccessItemResponse`:

```java
private static String tenantLogoMediaUrl(AuthStoreAccess store) {
    return store.tenantLogoMediaAssetId() == null
        ? null
        : CallScreenMediaService.tenantLogoMediaUrl(store.tenantId(), store.tenantLogoMediaAssetId());
}
```

- [ ] **Step 4: Run the focused API test and verify GREEN**

```powershell
mvn "-Dtest=AuthApiIntegrationTest#currentUserStoresReturnsAuthorizedActiveStoresWithDefaultFlag" test
```

Expected: PASS; configured sharing name and the same tenant-logo URL appear on both authorized stores, while the secondary sharing name is JSON null.

- [ ] **Step 5: Run the full auth integration class**

```powershell
mvn "-Dtest=AuthApiIntegrationTest" test
```

Expected: all `AuthApiIntegrationTest` tests pass, including inactive-store filtering and the empty platform-admin store catalog.

- [ ] **Step 6: Commit the backend implementation**

```powershell
git add -- src/main/java/com/rpb/reservation/auth/application/AuthStoreAccess.java src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java src/main/java/com/rpb/reservation/auth/api/AuthStoreAccessResponse.java
git commit -m "feat: expose authorized store brand metadata"
```

### Task 3: Define the Failing Shared Staff Brand UI Contract

**Files:**
- Create: `src/test/java/com/rpb/reservation/appgate/ui/StaffWorkbenchBrandIdentityUiValidationTest.java`
- Read: `src/types/auth.ts`
- Read: `src/components/staff-home/StaffHomeTopBar.vue`
- Read: all eight employee pages that render `StaffHomeTopBar`

**Interfaces:**
- Consumes: additive `AuthStoreAccess.shareDisplayName` and `AuthStoreAccess.tenantLogoMediaUrl`.
- Produces: a failing source contract for pure fallback resolution, explicit store scope, reusable presentation, failed-image fallback, bilingual fallback copy, responsive truncation, and complete page wiring.

- [ ] **Step 1: Create the focused source-validation class**

```java
package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffWorkbenchBrandIdentityUiValidationTest {
    private static final List<Path> TOP_BAR_PAGES = List.of(
        Path.of("src", "pages", "StoreStaffHomePage.vue"),
        Path.of("src", "pages", "ReservationTodayViewPage.vue"),
        Path.of("src", "pages", "QueueTicketListPage.vue"),
        Path.of("src", "pages", "TableResourceListPage.vue"),
        Path.of("src", "pages", "WalkInQueuePage.vue"),
        Path.of("src", "pages", "WalkInDirectSeatingPage.vue"),
        Path.of("src", "pages", "ReservationArrivedToQueuePage.vue"),
        Path.of("src", "pages", "SeatingFromCalledQueuePage.vue")
    );

    @Test
    void authorizedStoreContractAndResolverProvideDynamicBrandFallbacks() throws Exception {
        String authTypes = FrontendSourceSupport.readString(Path.of("src", "types", "auth.ts"));
        String resolver = FrontendSourceSupport.readString(Path.of(
            "src", "components", "staff-home", "staffBrandIdentity.ts"
        ));

        assertThat(authTypes)
            .contains("shareDisplayName: string | null")
            .contains("tenantLogoMediaUrl: string | null");
        assertThat(resolver)
            .contains("export function resolveStaffBrandIdentity")
            .contains("store?.shareDisplayName")
            .contains("store?.storeName")
            .contains("fallbackLabel")
            .contains("genericLabel")
            .contains("Array.from(displayName)[0]")
            .contains("store?.tenantLogoMediaUrl");
    }

    @Test
    void topBarComposesReusableBrandForAnExplicitStoreScope() throws Exception {
        String topBar = FrontendSourceSupport.readString(Path.of(
            "src", "components", "staff-home", "StaffHomeTopBar.vue"
        ));
        String brand = FrontendSourceSupport.readString(Path.of(
            "src", "components", "staff-home", "StaffBrandIdentity.vue"
        ));

        assertThat(topBar)
            .contains("storeId: string")
            .contains("useAuthSessionStore")
            .contains("auth.ensureAuthorizedStores()")
            .contains("store.storeId === props.storeId")
            .contains("resolveStaffBrandIdentity")
            .contains("<StaffBrandIdentity")
            .doesNotContain("t('staffHome.topbar.brandMark')")
            .doesNotContain("t('staffHome.topbar.title')");
        assertThat(brand)
            .contains("@error=\"handleLogoError\"")
            .contains("watch(() => props.logoMediaUrl")
            .contains("object-fit: contain;")
            .contains("text-overflow: ellipsis;")
            .contains("{{ fallbackMark }}");
    }

    @Test
    void everyStaffTopBarPassesItsExistingStoreId() throws Exception {
        for (Path page : TOP_BAR_PAGES) {
            assertThat(FrontendSourceSupport.readString(page))
                .as(page.toString())
                .contains("<StaffHomeTopBar")
                .contains(":store-id=\"storeId\"");
        }
    }

    @Test
    void genericFallbackAndLogoTextAreLocalizedWithoutFixedProductBrand() throws Exception {
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(zh)
            .contains("fallbackTitle: '门店管理'")
            .contains("logoAlt: '{name} 品牌标志'")
            .doesNotContain("title: '食刻 · 管理'");
        assertThat(en)
            .contains("fallbackTitle: 'Store management'")
            .contains("logoAlt: '{name} brand logo'")
            .doesNotContain("title: 'Shike Ops'");
    }
}
```

- [ ] **Step 2: Run the UI contract and verify RED**

```powershell
mvn "-Dtest=StaffWorkbenchBrandIdentityUiValidationTest" test
```

Expected: FAIL because the new resolver/component and fields do not yet exist, the top bar is still fixed, and pages do not pass `storeId`.

- [ ] **Step 3: Commit the failing UI contract**

```powershell
git add -- src/test/java/com/rpb/reservation/appgate/ui/StaffWorkbenchBrandIdentityUiValidationTest.java
git commit -m "test: define dynamic staff brand identity"
```

### Task 4: Implement Shared Brand Resolution and Presentation

**Files:**
- Modify: `src/types/auth.ts`
- Create: `src/components/staff-home/staffBrandIdentity.ts`
- Create: `src/components/staff-home/StaffBrandIdentity.vue`
- Modify: `src/components/staff-home/StaffHomeTopBar.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Modify: `src/pages/StoreStaffHomePage.vue`
- Modify: `src/pages/ReservationTodayViewPage.vue`
- Modify: `src/pages/QueueTicketListPage.vue`
- Modify: `src/pages/TableResourceListPage.vue`
- Modify: `src/pages/WalkInQueuePage.vue`
- Modify: `src/pages/WalkInDirectSeatingPage.vue`
- Modify: `src/pages/ReservationArrivedToQueuePage.vue`
- Modify: `src/pages/SeatingFromCalledQueuePage.vue`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/StaffWorkbenchBrandIdentityUiValidationTest.java`

**Interfaces:**
- Consumes: `AuthStoreAccess`, `useAuthSessionStore().authorizedStores`, `ensureAuthorizedStores()`, explicit `storeId`, current `storeLabel`, and i18n.
- Produces: `resolveStaffBrandIdentity(store, fallbackLabel, genericLabel): StaffBrandIdentity`, plus a reusable `StaffBrandIdentity.vue` accepting `kicker`, `displayName`, `logoMediaUrl`, `fallbackMark`, and `logoAlt`.

- [ ] **Step 1: Extend the TypeScript API contract**

Add after `storeName` in `AuthStoreAccess`:

```ts
shareDisplayName: string | null
tenantLogoMediaUrl: string | null
```

Update `StoreSwitcher.vue`'s legacy fallback item creation to include:

```ts
shareDisplayName: null,
tenantLogoMediaUrl: null,
```

- [ ] **Step 2: Add the pure brand resolver**

Create `staffBrandIdentity.ts`:

```ts
import type { AuthStoreAccess } from '../../types/auth'

export interface StaffBrandIdentity {
  displayName: string
  logoMediaUrl: string
  fallbackMark: string
}

export function resolveStaffBrandIdentity(
  store: AuthStoreAccess | undefined,
  fallbackLabel: string,
  genericLabel: string
): StaffBrandIdentity {
  const displayName = firstText(
    store?.shareDisplayName,
    store?.storeName,
    fallbackLabel,
    genericLabel
  )

  return {
    displayName,
    logoMediaUrl: clean(store?.tenantLogoMediaUrl),
    fallbackMark: Array.from(displayName)[0] || Array.from(genericLabel.trim())[0] || ''
  }
}

function firstText(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    const normalized = clean(value)
    if (normalized) {
      return normalized
    }
  }
  return ''
}

function clean(value: string | null | undefined): string {
  return value?.trim() || ''
}
```

- [ ] **Step 3: Add the focused brand presentation component**

Create `StaffBrandIdentity.vue`:

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  kicker: string
  displayName: string
  logoMediaUrl: string
  fallbackMark: string
  logoAlt: string
}>()

const logoFailed = ref(false)

watch(() => props.logoMediaUrl, () => {
  logoFailed.value = false
})

function handleLogoError(): void {
  logoFailed.value = true
}
</script>

<template>
  <div class="brand-block">
    <span v-if="!logoMediaUrl || logoFailed" class="brand-mark" aria-hidden="true">
      {{ fallbackMark }}
    </span>
    <span v-else class="brand-mark brand-mark--logo">
      <img :src="logoMediaUrl" :alt="logoAlt" @error="handleLogoError" />
    </span>
    <div class="brand-copy">
      <p class="brand-kicker">{{ kicker }}</p>
      <h1 :title="displayName">{{ displayName }}</h1>
    </div>
  </div>
</template>

<style scoped>
.brand-block {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
}

.brand-mark {
  align-items: center;
  background: #fff7ed;
  border-radius: 999px;
  color: #f97316;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 0.92rem;
  font-weight: 900;
  height: 30px;
  justify-content: center;
  overflow: hidden;
  width: 30px;
}

.brand-mark--logo {
  background: #ffffff;
  border: 1px solid #fed7aa;
}

.brand-mark img {
  height: 100%;
  object-fit: contain;
  width: 100%;
}

.brand-copy {
  min-width: 0;
}

.brand-kicker,
h1 {
  margin: 0;
}

.brand-kicker {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 800;
}

h1 {
  color: #0f172a;
  font-size: 1.08rem;
  letter-spacing: 0;
  line-height: 1.18;
  max-width: min(42vw, 420px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 420px) {
  h1 {
    max-width: 31vw;
  }
}
</style>
```

- [ ] **Step 4: Compose the current authorized store in `StaffHomeTopBar`**

Replace the script imports and brand setup with:

```ts
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAuthSessionStore } from '../../stores/authSession'
import StaffBrandIdentity from './StaffBrandIdentity.vue'
import { resolveStaffBrandIdentity } from './staffBrandIdentity'
import StoreSwitcher from '../store/StoreSwitcher.vue'

const props = defineProps<{
  appStatusLabel: string
  businessDate?: string | null
  currentTimeText: string
  storeId: string
  storeLabel: string
}>()

const auth = useAuthSessionStore()
const { t } = useI18n()
const currentStore = computed(() => auth.authorizedStores.find(store => store.storeId === props.storeId))
const brandIdentity = computed(() => resolveStaffBrandIdentity(
  currentStore.value,
  props.storeLabel,
  t('staffHome.topbar.fallbackTitle')
))
const displayAppStatus = computed(() => {
  const status = props.appStatusLabel.trim()
  return status === t('staffHome.appStatus.available') ? '' : status
})

onMounted(() => {
  if (!auth.storesLoaded && !auth.storesLoading) {
    void auth.ensureAuthorizedStores()
  }
})
```

Replace the current `.brand-block` markup with:

```vue
<StaffBrandIdentity
  :kicker="t('staffHome.topbar.kicker')"
  :display-name="brandIdentity.displayName"
  :logo-media-url="brandIdentity.logoMediaUrl"
  :fallback-mark="brandIdentity.fallbackMark"
  :logo-alt="t('staffHome.topbar.logoAlt', { name: brandIdentity.displayName })"
/>
```

Remove `.brand-block`, `.brand-mark`, `.brand-kicker`, and top-bar `h1` rules now owned by `StaffBrandIdentity.vue`. Keep the top-bar shell, status, store switcher, slots, and media query unchanged.

- [ ] **Step 5: Replace fixed locale values with generic fallback copy**

Chinese `staffHome.topbar`:

```ts
fallbackTitle: '门店管理',
kicker: '门店员工',
logoAlt: '{name} 品牌标志'
```

English `staffHome.topbar`:

```ts
fallbackTitle: 'Store management',
kicker: 'Store staff',
logoAlt: '{name} brand logo'
```

Remove `brandMark` and `title` from both locale objects.

- [ ] **Step 6: Pass the explicit store scope from all eight pages**

Add this prop to every existing `<StaffHomeTopBar` invocation:

```vue
:store-id="storeId"
```

The exact files are:

```text
src/pages/StoreStaffHomePage.vue
src/pages/ReservationTodayViewPage.vue
src/pages/QueueTicketListPage.vue
src/pages/TableResourceListPage.vue
src/pages/WalkInQueuePage.vue
src/pages/WalkInDirectSeatingPage.vue
src/pages/ReservationArrivedToQueuePage.vue
src/pages/SeatingFromCalledQueuePage.vue
```

- [ ] **Step 7: Run the focused UI contract and verify GREEN**

```powershell
mvn "-Dtest=StaffWorkbenchBrandIdentityUiValidationTest" test
```

Expected: 4 tests pass with no failures or errors.

- [ ] **Step 8: Run the TypeScript and production build**

```powershell
npm run build
```

Expected: `vue-tsc --noEmit` and Vite production build both succeed.

- [ ] **Step 9: Commit the frontend implementation**

```powershell
git add -- src/types/auth.ts src/components/store/StoreSwitcher.vue src/components/staff-home/staffBrandIdentity.ts src/components/staff-home/StaffBrandIdentity.vue src/components/staff-home/StaffHomeTopBar.vue src/i18n/locales/zh-CN.ts src/i18n/locales/en-SG.ts src/pages/StoreStaffHomePage.vue src/pages/ReservationTodayViewPage.vue src/pages/QueueTicketListPage.vue src/pages/TableResourceListPage.vue src/pages/WalkInQueuePage.vue src/pages/WalkInDirectSeatingPage.vue src/pages/ReservationArrivedToQueuePage.vue src/pages/SeatingFromCalledQueuePage.vue
git commit -m "feat: show store branding in staff workbench"
```

### Task 5: Regression, Reviews, Release Evidence, and Push

**Files:**
- Create: `docs/release-notes/2026-07-16-staff-workbench-dynamic-brand-identity.md`
- Verify: all files changed in Tasks 1–4

**Interfaces:**
- Consumes: completed additive API contract and shared frontend brand identity.
- Produces: API/TDD/UI/code review evidence, release/rollback documentation, a clean `master`, and a pushed branch.

- [ ] **Step 1: Run focused backend and frontend regression tests**

```powershell
mvn "-Dtest=AuthApiIntegrationTest,StaffWorkbenchBrandIdentityUiValidationTest,AuthLoginUiValidationTest,FrontendI18nFoundationValidationTest,StaffPrimaryWorkbenchTabletUiValidationTest,StaffReceptionClosedLoopUiValidationTest" test
```

Expected: all selected tests pass. This covers the authorized-store response, login/store selector compatibility, i18n migration constraints, primary workbench responsiveness, and shared staff workflows.

- [ ] **Step 2: Run the frontend production build and static checks**

```powershell
npm run build
rg -n "食刻 · 管理|Shike Ops|staffHome\.topbar\.brandMark|staffHome\.topbar\.title" src
git diff --check
```

Expected: build passes; the fixed staff brand strings/keys are absent from active source; `git diff --check` reports no errors.

- [ ] **Step 3: Perform API and TDD review checks**

Record:

```markdown
# API Review Report
## Endpoint
GET /api/v1/me/stores
## Contract Check
Two nullable additive fields; no request or error-contract change.
## Permission Check
Existing authenticated account/access/tenant/store predicates are unchanged; no tenant-admin permission is exposed.
## Error Mapping
Unchanged read-query behavior and status mapping.
## Replay / Idempotency
Not applicable to a GET read model.
## Compatibility
Existing fields remain unchanged; Vue fallbacks handle null and failed loading.
## Missing Items
None.

# TDD Review Report
| Scenario | Test Exists | Result | Notes |
|---|---|---|---|
| Configured share name | Yes | Pass | Authorized-store integration |
| Missing share name | Yes | Pass | JSON null and frontend fallback |
| Configured tenant logo URL | Yes | Pass | Same tenant logo across stores |
| Unauthorized/cross-scope stores | Yes | Pass | Existing access filtering retained |
| Missing/failed logo | Yes | Pass | Shared presentation fallback contract |
| All top-bar pages | Yes | Pass | Eight explicit store-id assertions |
| Loading/API failure | Yes | Pass | Existing storeLabel fallback retained |
```

- [ ] **Step 4: Perform responsive UI verification**

With one store configured with a long sharing name and logo, and another store with no sharing name/logo, verify at 390x844, 768x1024, and 1024x768:

- configured logo and sharing name appear;
- switching stores updates the title and logo;
- missing logo uses the resolved name's first character;
- long titles truncate without covering the time, store selector, status, or action buttons;
- Home, Reservations, Queue, Tables, and the four secondary workflow pages retain their existing controls and order;
- no page-level horizontal overflow occurs.

If local browser verification is used, read `target/local-postgres-current.txt` first and use its exact PostgreSQL port.

- [ ] **Step 5: Write the release note**

Create `docs/release-notes/2026-07-16-staff-workbench-dynamic-brand-identity.md`:

```markdown
# Staff Workbench Dynamic Brand Identity

## User-visible change

Tenant employee pages now show each store's configured sharing display name and the tenant logo in the shared top bar. A blank sharing name falls back to the backend store name, and a missing or failed logo falls back to the resolved name's first character.

## Scope and compatibility

- Applies to all employee pages using the shared top bar on phone, tablet portrait, and tablet landscape.
- `GET /api/v1/me/stores` adds nullable `shareDisplayName` and `tenantLogoMediaUrl` fields.
- Tenant-admin management permissions, database schema, staff workflows, routes, and store switching are unchanged.
- Authorized-store tenant isolation remains enforced by the existing account/access query.

## Verification

- Authorized-store API integration tests passed for configured and missing brand metadata.
- Dynamic brand UI contract and all eight page-wiring assertions passed.
- Related auth, i18n, workbench, and staff workflow tests passed.
- Frontend production build passed.
- Phone and tablet portrait/landscape checks confirmed truncation and no overlap.

## Rollback

Remove the two additive authorized-store fields, shared brand resolver/presentation component, and `storeId` top-bar wiring, then restore the fixed top-bar identity. No data, migration, permission, or media rollback is required.
```

- [ ] **Step 6: Apply code-review and UI-review checklists**

Confirm:

- Java application, persistence, and API responsibilities remain separated;
- no tenant/store predicate changed;
- no duplicate brand request or page-level brand logic exists;
- Vue renders external text by interpolation and the logo through a normal `img` source;
- image failure resets when `logoMediaUrl` changes;
- bilingual text and long names do not clip operational controls;
- loading and API failure keep a usable fallback identity;
- no unrelated files or user changes are included.

- [ ] **Step 7: Commit release evidence**

```powershell
git add -- docs/release-notes/2026-07-16-staff-workbench-dynamic-brand-identity.md
git commit -m "docs: record dynamic staff brand identity"
```

- [ ] **Step 8: Run final verification and push `master`**

```powershell
mvn "-Dtest=AuthApiIntegrationTest,StaffWorkbenchBrandIdentityUiValidationTest,AuthLoginUiValidationTest,FrontendI18nFoundationValidationTest,StaffPrimaryWorkbenchTabletUiValidationTest,StaffReceptionClosedLoopUiValidationTest" test
npm run build
git status --short
git push origin master
```

Expected: selected tests and build pass, `git status --short` is empty, and `master` pushes successfully. Production deployment is a separate explicitly authorized step.
