# Release Notes

## Version / Date

2026-07-06 host-prefix login and tenant public booking entry.

## New

- `platform.<deployment-domain>/login` is now the platform admin login entry.
- `<tenantCode>.<deployment-domain>/login` is now the tenant-context login entry for tenant admins and staff.
- `<tenantCode>.<deployment-domain>/book` resolves the tenant's single enabled public booking store and reuses the existing public booking flow.
- Login requests now include `loginEntry` and optional `tenantCode`.

## Changed

- Backend login scope is resolved from `Host` / `X-Forwarded-Host`; request tenant code is not authoritative on tenant subdomains.
- Platform-prefixed hosts only allow `platform_admin`.
- Tenant-prefixed hosts only allow `tenant_admin` and `staff` for that tenant.
- Root domain and localhost retain the legacy login tabs and `/book/:storeId` public booking route.
- The login page now supports remembering the account per host and entry. Passwords remain handled by the browser password manager.
- Tenant admin public booking QR/copy URL now prefers the tenant subdomain `/book` entry, with local/IP deployments falling back to `/book/:storeId`.
- Tenant-prefixed login hosts now support DNS-safe alphanumeric prefixes such as `lsc106`, while the root deployment host remains the legacy compatibility entry.
- Tenant-prefixed login pages hide the platform entry, hide tenant-code inputs and hints, and do not prefill seed/demo account values.
- The platform reservation confirmation seed template now defaults to English (`en-SG`) on public deployments; migrations only refresh records still matching known historical system defaults.
- The platform reservation confirmation seed page now loads and saves template content by the active frontend locale, so `中文` and `EN` switch between `zh-CN` and `en-SG` platform templates.
- The platform seed API keeps the old `GET /api/v1/platform/reservation/share-template-seed` behavior and adds optional `?locale=zh-CN|en-SG` for locale-specific editing.
- Public booking Google login now renders the official Google Identity Services button and avoids repeated One Tap `prompt()` initialization on customer browsers.
- Public booking phone entry now defaults to Singapore `+65`, accepts an 8-digit local customer number, and still submits `phoneE164` to the reservation API.

## Migration

- Added `V030__auth_account_scoped_username.sql`.
- Added `V031__english_reservation_share_default_template.sql`.
- Added `V032__catch_up_english_reservation_share_seed.sql`.
- Added `V033__ensure_chinese_reservation_share_platform_template.sql`.
- Replaced the old global active username unique index with:
  - `ux_auth_accounts_platform_username_active`
  - `ux_auth_accounts_tenant_username_active`
- Different tenants can now reuse employee account numbers such as `1000`.
- `V033` backfills only the missing platform-scope `zh-CN` reservation confirmation i18n catalog message and does not overwrite existing Chinese templates or store overrides.

## Deployment

- Requires wildcard DNS and TLS for `*.booking.yumstone.sg` or the deployment-specific equivalent.
- Nginx should use `server_name booking.yumstone.sg *.booking.yumstone.sg` and pass both `Host` and `X-Forwarded-Host`.
- The production domain remains deployment configuration; it is not hard-coded in business code.
- Optional backend config `RPB_HOST_PREFIX_BASE_HOST=<deployment-domain>` can be set when the deployment root is not distinguishable by label depth.
- Optional frontend config `VITE_RPB_HOST_PREFIX_BASE_HOST=<deployment-domain>` or runtime `window.__RPB_HOST_PREFIX_BASE_HOST__` is supported for the same deployment-root case.

## Verification

- `mvn "-Dtest=AuthApiIntegrationTest,AuthMigrationTest,PublicBookingApplicationServiceTest,AuthLoginUiValidationTest,PublicBookingUiValidationTest" test`
- `mvn "-Dtest=AuthLoginUiValidationTest,AuthApiIntegrationTest" test`
- `mvn "-Dtest=AuthLoginUiValidationTest,HostPrefixContextResolverTest" test`
- `mvn "-Dtest=PlatformReservationShareTemplateSeedServiceTest,PlatformReservationShareTemplateSeedControllerTest,PlatformReservationShareTemplateSeedUiImplementationValidationTest" test`
- `mvn "-Dtest=ReservationShareTemplateDefaultMigrationTest" test`
- `mvn "-Dtest=PlatformReservationShareTemplateSeedLocalRuntimeSecurityTest" test`
- `mvn "-Dtest=AuthMigrationTest" test`
- `mvn -Dtest=PublicBookingUiValidationTest test`
- `npm run build`

## Risk

- Medium auth-routing risk because login account lookup changed from global username lookup to host-scoped lookup.
- Public booking `/book` intentionally fails when multiple stores are enabled for one tenant, until a default-store or store-selection rule is defined.
- Low platform-template risk: bilingual seed editing reuses `i18n_message_catalog`; the legacy platform seed remains the compatibility fallback and English save target.
- Low phone-entry risk: the customer-facing public booking form now targets Singapore local numbers only; the API contract remains unchanged.

## Rollback Notes

- Roll back by redeploying the previous backend jar and frontend bundle.
- Database rollback would require recreating the old global username unique index only if the release is reverted before any cross-tenant duplicate usernames are created.
- `V033` is additive. If rollback is needed, leave the inserted Chinese i18n message in place or soft-delete that one platform-scope `zh-CN` catalog row after confirming no operators edited it.
