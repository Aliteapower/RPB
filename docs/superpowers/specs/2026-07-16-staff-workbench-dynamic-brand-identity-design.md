# Tenant Staff Workbench Dynamic Brand Identity Design

**Date:** 2026-07-16
**Status:** Accepted for implementation planning
**Scope:** All tenant employee pages that render the shared staff workbench top bar

## Goal

Replace the hard-coded `食刻 · 管理` / `Shike Ops` brand identity in the tenant employee workbench with the current authorized store's configured sharing display name and the tenant's configured logo. Preserve all existing staff workflows and phone, tablet portrait, and tablet landscape behavior.

## Confirmed Product Decisions

- The top-bar title displays the current store's `分享显示名称` (`stores.share_display_name`).
- A blank sharing display name falls back to the store's backend display name (`stores.display_name`).
- The title does not append `· 管理` or another fixed suffix.
- The existing `门店员工` / `Store staff` kicker remains.
- The brand image uses the tenant-level logo (`tenants.logo_media_asset_id`).
- A missing or failed logo falls back to the first Unicode character of the resolved display name.
- The behavior applies to every page using `StaffHomeTopBar`, including the four primary workbench pages and the walk-in, seating, and reservation-to-queue workflow pages.
- No reservation, queue, seating, table, authentication, or store-switching behavior changes.

## Existing State

`StaffHomeTopBar.vue` currently reads a fixed mark and title from `staffHome.topbar.brandMark` and `staffHome.topbar.title`. The same component is rendered by eight employee pages, so this fixed content appears for every tenant and store.

The required source data already exists:

- `stores.share_display_name` stores the store-level sharing name;
- `stores.display_name` stores the backend store name;
- `tenants.logo_media_asset_id` stores the shared tenant logo;
- `GET /api/v1/me/stores` already returns the current account's authorized store read models and is cached by the frontend auth session store;
- `CallScreenMediaService.tenantLogoMediaUrl` already creates the tenant logo media URL, and the corresponding GET media route is readable by the browser.

The existing tenant-admin profile and sharing-profile APIs are intentionally protected by `tenant.admin.manage`; regular store employees must not depend on or gain access to those management contracts.

## Approaches Considered

### A. Enrich the Authorized Store Read Model — Selected

Extend `GET /api/v1/me/stores` with the current store's sharing display name and tenant logo URL. The shared top bar consumes this cached read model.

Benefits:

- adds no extra request when the authorized store catalog is already loaded;
- preserves tenant-admin write and read permission boundaries;
- naturally follows the current route/store switch;
- makes the branding data available only for stores already authorized for the account;
- keeps one reusable frontend identity path for every employee page.

### B. Add a Dedicated Staff Branding Endpoint

Create a store-scoped read endpoint for brand identity. This is independently explicit but adds a request, cache, error path, and permission contract to every employee entry.

### C. Allow Employees to Read Tenant-Admin Profile APIs

Reuse the existing management endpoints. This has smaller surface-level code changes but incorrectly couples staff presentation to tenant-admin permissions and expands an administrative contract to non-admin actors.

## Backend Design

### Authorized Store Application Read Model

Extend `AuthStoreAccess` with:

- `shareDisplayName` as nullable text;
- `tenantLogoMediaAssetId` as a nullable UUID.

`AuthRepository.authorizedStores` selects both fields in the same store-access query. The existing joins and predicates remain authoritative:

- account and access rows must be active;
- tenant and store must be active and not deleted;
- store access must match both `account.id` and `account.tenant_id`;
- the returned store belongs to the same tenant as the access row.

No independent store identifier supplied by the client participates in this query, so the API cannot disclose another tenant's or another account's stores.

### API Contract

Each item in `GET /api/v1/me/stores` adds:

- `shareDisplayName: string | null`;
- `tenantLogoMediaUrl: string | null`.

The API response mapper converts `tenantLogoMediaAssetId` to a URL with `CallScreenMediaService.tenantLogoMediaUrl(tenantId, assetId)`. Keeping the asset ID in the application model and URL construction in the API layer prevents HTTP concerns from leaking into persistence or application logic.

The additions are backward-compatible JSON fields. Existing authentication, roles, permissions, session behavior, and store-access filtering remain unchanged.

## Frontend Component and OOD Boundaries

### Brand Resolution Module

Add a small shared brand identity resolver under `src/components/staff-home`. It accepts the authorized store item and the existing page-provided store label, and returns:

- resolved display name;
- logo URL;
- fallback mark.

Display-name precedence is:

1. trimmed `shareDisplayName`;
2. trimmed `storeName`;
3. trimmed existing `storeLabel` prop;
4. localized generic `门店管理` / `Store management`.

The fallback mark is the first Unicode character of the resolved display name, avoiding a tenant-independent fixed `食` mark.

### Brand Presentation Component

Add a focused brand presentation component under `src/components/staff-home` that owns only:

- kicker, title, and logo markup;
- image load-error state;
- switching from a failed image to the fallback mark;
- truncation and accessible image text.

It does not fetch data, inspect the route, or own store-switching behavior.

### Shared Top Bar

`StaffHomeTopBar` remains the composition owner. Add an explicit required `storeId` prop and use the auth session store's cached authorized stores to select the matching item. It ensures the authorized store catalog is loaded, resolves the identity, and passes presentation props to the brand component.

Every page rendering `StaffHomeTopBar` passes its existing `storeId`. This explicit dependency avoids deriving business scope indirectly from a route string and keeps secondary workflow pages aligned with the primary workbench.

`StoreSwitcher` remains responsible only for selecting and routing between authorized stores. It continues to show the backend store label; changing its copy to the public sharing name is outside this scope.

## Loading, Failure, and Store-Switch Behavior

- Before `/api/v1/me/stores` resolves, the title uses the existing page `storeLabel`; if that is absent, it uses the localized generic name.
- A catalog request failure does not block or replace the employee page; the same fallback remains visible.
- A null logo URL renders the fallback mark immediately.
- A logo request error hides the broken image and renders the fallback mark.
- A different `storeId` selects a different cached authorized-store item and updates both the title and tenant logo without page-specific brand logic.
- The logo error state resets when the logo URL changes.

## Layout and Accessibility Contract

- The current top-bar dimensions, sticky behavior, meta controls, store switcher, and action buttons remain unchanged.
- The configured logo fits inside the existing 30-pixel brand area with `object-fit: contain` and does not distort.
- Long Chinese or English display names truncate within the available brand column rather than overlapping the store and status controls.
- The title retains its semantic `h1` role.
- The logo has accessible text derived from the resolved store name; the text fallback is marked decorative because the adjacent title provides the same identity.
- Phone, tablet portrait, and tablet landscape layouts must have no horizontal overflow or control overlap.

## Localization

Retain the existing localized kicker. Replace the fixed fallback title/mark dependency with localized generic values for:

- store management fallback title;
- logo accessible label if a formatted label is needed.

The hard-coded product-specific `食刻 · 管理` / `Shike Ops` values are no longer used by the staff top bar.

## API, Database, and Security Impact

- API: additive fields on `GET /api/v1/me/stores`.
- Database: no schema or migration changes.
- Security: no permission expansion; only authorized store rows are enriched.
- Tenant isolation: enforced by the existing account, tenant, access, and store predicates.
- Media: reuse the existing tenant-logo media URL and ownership validation; no new upload or mutation route.
- Privacy: display name and tenant logo are non-customer brand metadata already used by the product's public and administrative surfaces.

## TDD and Verification Strategy

### Backend

1. Add a failing API/integration assertion that an authorized store includes its configured sharing display name and tenant logo media URL.
2. Add a missing-value assertion returning JSON nulls without changing store access.
3. Preserve or extend store-access isolation assertions so unauthorized and cross-tenant stores are absent.
4. Implement the minimal repository, application record, and API mapper changes.

### Frontend

1. Add focused failing validation for the additive auth-store fields and dynamic top-bar contract.
2. Test the name precedence, Unicode fallback mark, missing logo, and failed-image fallback.
3. Require every `StaffHomeTopBar` usage to pass `storeId`.
4. Confirm the fixed staff brand title and mark are no longer rendered.
5. Run the affected UI validation suites and the frontend production build.

### Visual Verification

Verify one configured and one fallback case at:

- phone width;
- tablet portrait width;
- tablet landscape width.

Confirm that the logo, long store title, time, store switcher, status, and action controls do not overlap and that switching stores changes the identity.

If authenticated local browser validation starts the backend, it must use the PostgreSQL runtime identified by `target/local-postgres-current.txt`.

## Expected Files

- Modify `src/main/java/com/rpb/reservation/auth/application/AuthStoreAccess.java`.
- Modify `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java`.
- Modify `src/main/java/com/rpb/reservation/auth/api/AuthStoreAccessResponse.java`.
- Modify `src/types/auth.ts`.
- Add a brand identity resolver and presentation component under `src/components/staff-home`.
- Modify `src/components/staff-home/StaffHomeTopBar.vue`.
- Modify all employee pages rendering `StaffHomeTopBar` to pass `storeId`.
- Modify Chinese and English staff-home locale messages.
- Add or update focused backend and UI tests.
- Add a release note after implementation.

## Acceptance Criteria

- Every employee top bar shows the current store's trimmed sharing display name when configured.
- A blank sharing name shows the backend store name, without a fixed suffix.
- A configured tenant logo is displayed for every authorized store under that tenant.
- Missing or failed logos show the resolved name's first Unicode character.
- Switching stores updates the displayed store identity.
- No employee page depends on `tenant.admin.manage` to obtain branding.
- Unauthorized stores and other tenants' branding are not returned.
- All eight employee pages retain their existing workflow and responsive behavior.
- Focused backend tests, focused UI tests, related auth tests, and the frontend production build pass.

## Rollback

Remove the two additive authorized-store response fields, restore the fixed top-bar brand rendering, remove the shared brand resolver/presentation component, and remove the `storeId` top-bar prop wiring. No data, migration, permission, or media rollback is required.
