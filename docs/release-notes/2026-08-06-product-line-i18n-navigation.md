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
