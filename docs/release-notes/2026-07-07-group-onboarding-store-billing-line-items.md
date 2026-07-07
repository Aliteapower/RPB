# Release Notes

## Version / Date

2026-07-07 - Group tenant onboarding and store billing line items

## New

- Added a platform tenant onboarding mode for group multi-store tenants. Platform admins can now create a management tenant with a default operating entity and without bootstrapping a default store, then add branches in the tenant structure panel.
- Added separate platform tenant list entry buttons for group onboarding and single-store onboarding.
- Added default operating-entity creation for the existing single-store onboarding path so newly created single-store tenants are immediately compatible with the operating-entity submodel.
- Added per-store product subscription billing line items. Each active billable store receives a persisted subscription item with store, operating entity, amount, currency, billing cycle, period, status, idempotency key, and source action metadata.
- Added platform billing UI display for billable store count, per-store period amount, total amount, and subscription store item details.

## Changed

- `POST /api/v1/platform/tenants` accepts optional `onboardingMode` with `single_store` as the compatibility default and `group_multi_store` for group tenants.
- Platform tenant list copy is now "Group / tenant management" and routes `Add group` / `Add single store` into the matching onboarding mode.
- Product subscription quote responses now include `storeCount` and `storeUnitAmount`.
- Tenant product subscription list and mutation responses now include `subscription.items`.
- Manual product subscription purchase, renew, and legacy conversion now price by active tenant stores instead of assuming one tenant equals one billable store.
- Purchase, renew, and legacy conversion actions now require at least one active billable store.

## Fixed

- Closed the workflow gap where a group customer had to be created as a store tenant first before platform users could add operating entities and branches.
- Closed the billing visibility gap where platform users could see only the tenant-level amount but not the store-level billing detail.

## Migration

- Adds Flyway migration `V036__tenant_subscription_store_billing_items.sql`.
- Creates `tenant_product_subscription_items` with uniqueness on active store-scoped rows per subscription and foreign keys to subscriptions, tenants, stores, and operating entities.
- Backfills one store-scoped line item per existing subscription using the tenant default active store when available.
- Migration is idempotent for replay validation, but production rollback should be handled through a controlled rollback migration or database restore.

## Permission

- No new permission codes.
- Group onboarding and tenant structure configuration remain under existing `platform.tenant.manage`.
- Tenant-side staff/store access still depends on explicit `auth_account_store_access`; platform admins remain in platform routes.

## Risk

- Medium database risk because a new billing detail table and backfill are introduced.
- Billing totals now multiply the product-line price by active store count. Tenants with multiple active stores will produce higher subscription totals than the previous tenant-level assumption.
- Group tenant creation intentionally leaves `defaultStoreId` null until platform users configure stores; downstream flows and billing actions that require a store must use the tenant structure panel first. The default operating entity is still created so the next business action is adding branches.
- Existing API clients remain compatible if they ignore the new response fields and omit `onboardingMode`.

## Rollback Notes

- Roll back backend, frontend, and Flyway together if store billing line items cause issues.
- If only the frontend is rolled back, backend billing totals and API responses will still include store line items but users lose visibility into the detail.
- If only the backend is rolled back, the frontend may request onboarding and billing fields that old APIs do not understand.
- Do not create group multi-store tenants in production unless V035, V036, backend, and frontend are all deployed.

## Validation

- `mvn -q "-Dtest=PlatformGroupTenantOnboardingUiValidationTest,PlatformProductLineBillingUiImplementationValidationTest" test`
- `mvn -q "-Dtest=ProductSubscriptionServiceTest,SubscriptionQuoteServiceTest,PlatformTenantProductSubscriptionControllerTest,PlatformBillingMigrationTest,PlatformTenantApiIntegrationTest" test`
- `mvn -q "-Dtest=PlatformBillingBoundaryValidationTest" test`
- `npm run build`

## Frontend Redeployment

- Redeployed frontend bundle from commit `411f02e2` to `booking.yumstone.sg` on 2026-07-07.
- Production frontend backup: `/opt/rpb/backups/20260707-121200-411f02e2-frontend`.
- Live entry asset after redeploy: `/assets/index-DLal4Rx6.js`.
- Live i18n asset contains the group onboarding copy for `新增集团` / `新增单店`.
- Smoke checks: `/platform/tenants` returned `200`, `/login` returned `200`, `platform.booking.yumstone.sg/login` returned `200`, `20000000.booking.yumstone.sg/login` returned `200`, `20000000.booking.yumstone.sg/book` returned `200`, and unauthenticated `/api/v1/auth/me` returned `401`.
- Backend jar and Flyway were not redeployed in this frontend-only pass.
