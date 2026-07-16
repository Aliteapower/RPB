# Release Notes

## Version / Date

Tenant admin share phone normalization / 2026-06-29

## New

None.

## Changed

Tenant admin reservation share profile now accepts an 8-digit Singapore local WhatsApp business phone and stores it as E.164 with the `+65` prefix.

## Fixed

- `68681234` is normalized to `+6568681234` instead of returning `REQUEST_INVALID`.
- Existing `+6568681234` style values continue to pass unchanged.
- Existing non-E.164 values such as `6588880000` continue to be rejected.

## Migration

No database migration. The stored value remains compliant with the existing `stores.whatsapp_business_phone_e164` E.164 check constraint.

## Permission

No permission or App Gate changes.

## Risk

Low. The change is scoped to tenant admin reservation share profile phone normalization and does not alter platform tenant ordinary contact phone behavior.

## Rollback Notes

Revert `TenantAdminShareProfileService` normalization and the regression test if local Singapore input must again be rejected. Existing normalized `+65` data remains valid under the current database constraint.
