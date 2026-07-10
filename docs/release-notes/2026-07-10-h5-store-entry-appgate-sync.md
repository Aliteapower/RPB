# Release Notes

## Version / Date
2026-07-10 - H5 store entry selection and App Gate billing sync

## New
- Staff H5 login now loads authorized store metadata before showing the post-login store picker.
- The store picker displays store name, store code, and operating entity instead of a raw UUID.

## Fixed
- Active store-scoped subscription items now sync into `store_app_settings` so subscribed stores can enter H5 staff functions.
- Existing disabled or hidden `store_app_settings` rows are reopened when the store subscription is active.
- V044 backfills missing App Gate store settings for existing active store subscriptions.

## Troubleshooting Report
### Symptom
Staff H5 login could authenticate, but the post-login store selector showed UUIDs and entering a store could hit `STORE_APP_NOT_ENABLED`.

### Evidence
- Production `lsc106` had active `tenant_product_subscription_items` rows for stores `lsc106` and `lsc83`.
- Both stores were missing `store_app_settings` for `reservation_queue`, so App Gate denied store feature APIs.
- The login page rendered options directly from `auth.user.storeIds`, which only contains IDs.

### Root Cause
The frontend did not load `/api/v1/me/stores` before rendering the H5 staff store picker. Separately, the platform billing entitlement sync inserted store App Gate settings only for missing rows from the raw `stores` table and did not update disabled or hidden rows. Historical active subscription items could therefore exist without enabled store App Gate settings.

### Fix Plan
- Use `auth.ensureAuthorizedStores(true)` after multi-store staff login and render `AuthStoreAccess` labels.
- Change billing entitlement sync to upsert from active store subscription items only.
- Add V044 to backfill existing production data.

## Migration
- Added `V044__sync_active_store_subscription_app_settings.sql`.
- The migration is data-only and idempotent.
- It only opens settings for active store items whose parent subscription is active, entitlement is enabled/trial, and store is active/not deleted.
- Suspended, cancelled, expired, inactive, or deleted stores are not opened.

## Permission
- No new role or permission.
- Existing App Gate rules remain enforced.

## Verification
- `mvn -Dtest=AuthLoginUiValidationTest#staffLoginStoreSelectionLoadsStoreNamesInsteadOfShowingRawStoreIds test`
- `mvn -Dtest=JdbcTenantProductEntitlementSyncGatewaySourceValidationTest test`
- `mvn -Dtest=PlatformBillingMigrationTest#createsManualBillingTablesBackfillsLegacyGrantsAndDoesNotExpireExistingEntitlements test`
- `mvn "-Dtest=AuthLoginUiValidationTest,JdbcTenantProductEntitlementSyncGatewaySourceValidationTest,PlatformBillingMigrationTest,ProductSubscriptionServiceTest" test`
- `npm run build`

## Deployment
- Pending.

## Risk
- Low to moderate: this affects H5 login routing and App Gate data sync.
- The migration intentionally avoids opening stores without active store-scoped subscriptions.

## Rollback Notes
- Roll back the backend jar and frontend bundle to the previous deployment backup.
- V044 is additive/idempotent. If a store was opened incorrectly, set the specific `store_app_settings.is_enabled=false` and `entry_visible=false`, or restore from the pre-deploy database backup.
