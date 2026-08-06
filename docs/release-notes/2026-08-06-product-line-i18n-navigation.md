# Release Notes

## Version / Date
- 2026-08-06
- Scope: tenant admin product-line internationalization menu boundaries.

## Changed
- PayNow now has its own tenant admin internationalization entry under the PayNow product-line menu.
- Reservation Queue now has its own tenant admin internationalization entry under the Reservation Queue product-line menu.
- Tenant admin navigation no longer uses the shared `/admin/i18n-catalog` menu item.
- Tenant admin i18n catalog API accepts a `productLine` scope so each product-line entry only loads and updates its own editable namespaces.
- PayNow has a dedicated `payment` i18n namespace and `quick_pay` category for product-line-owned business copy.

## Compatibility
- The legacy `/stores/:storeId/admin/i18n-catalog` route remains available and redirects to the Reservation Queue internationalization entry.

## Migration
- Added Flyway `V051__payment_i18n_catalog_product_line_scope.sql`.
- V051 adds tenant-editable PayNow catalog keys and platform default messages. It does not overwrite tenant or store overrides.
- Existing unscoped tenant i18n API behavior remains compatible; product-line pages now pass `productLine=payment` or `productLine=reservation_queue`.

## Verification
- `mvn "-Dtest=I18nCatalogServiceTest,I18nCatalogApiImplementationValidationTest,I18nCatalogMigrationSourceValidationTest,I18nCatalogAdminUiValidationTest,PayNowPaymentUiAcceptanceValidationTest" test` passed with 15 tests.
- `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest,I18nCatalogAdminUiValidationTest" test` passed.
- `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest,I18nCatalogAdminUiValidationTest,AdminResponsiveUiValidationTest,TenantAdminCustomerManagementUiValidationTest" test` passed with 7 tests.
- `npm run build` passed.

## Production Deployment
- Deployment date: 2026-08-06.
- Deployed commit: `879cd423`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend JAR SHA-256: `A34B05C175E6A385B8445E9D1DBA7B0104BF43F12BFB8A333A56FF2F7B9A5C01`.
- Backend backup: `/opt/rpb/backups/20260806-214228-879cd423`.
- Frontend backup: `/opt/rpb/backups/20260806-214316-879cd423-frontend`.
- Flyway latest: `051|payment i18n catalog product line scope|true`.
- PayNow active `payment` / `quick_pay` i18n keys: `3`.
- `rpb-backend`: `active / running`, PID `3631429`.
- Recent backend `ERROR` count after deployment: `0`.
- Public smoke:
  - `https://booking.yumstone.sg/login` returned `200` and loaded `/assets/index-BHUX6SCA.js`.
  - `https://booking.yumstone.sg/api/v1/auth/me` returned `401`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments` returned `200`.
