# Troubleshooting Report

## Symptom

Tenant admin reservation share profile maintenance rejected an 8-digit Singapore local WhatsApp business phone such as `68681234`, even though the expected stored value is the E.164 number `+6568681234`.

## Evidence

- `PATCH /api/v1/stores/{storeId}/tenant-admin/share-profile` with `whatsappBusinessPhoneE164: "68681234"` returned HTTP 400 with `REQUEST_INVALID`.
- Platform and tenant ordinary contact phone fields store free-form contact values such as `021-393930` and are not the failing validation path.
- `stores.whatsapp_business_phone_e164` has a database check requiring E.164 when present, so local numbers must be normalized before persistence.

## Root Cause

`TenantAdminShareProfileService.optionalE164` only accepted values already formatted as E.164. It did not handle the product's Singapore local 8-digit input convention used elsewhere in staff phone entry.

## Affected Files

- `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminShareProfileService.java`
- `src/test/java/com/rpb/reservation/auth/integration/TenantAdminApiIntegrationTest.java`

## Fix Plan

Normalize exactly 8 digits in `whatsappBusinessPhoneE164` by prefixing `+65` before applying existing E.164 persistence. Keep already valid E.164 values unchanged and keep invalid non-E.164 values rejected.

## Verification

- Red test reproduced the failure: expected HTTP 200 but got HTTP 400 for `68681234`.
- Focused regression passed after the fix.
- Full relevant verification passed:
  - `mvn '-Dtest=TenantAdminApiIntegrationTest,StoreWhatsappShareProfileMigrationTest' test`

## Remaining Risk

The normalization intentionally only accepts exactly 8 digits as Singapore local input. Values with spaces, hyphens, or other country-specific local formats still require explicit E.164 input.
