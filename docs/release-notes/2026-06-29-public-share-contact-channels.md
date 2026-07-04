# Release Notes

## Version / Date

public-share-contact-channels / 2026-06-29

## New

- Reservation public share responses now include tenant-maintained public email and WhatsApp business phone.
- Reservation public share page shows email and WhatsApp contact actions when configured.

## Changed

- Tenant admin share profile supports maintaining a public email address.
- Singapore 8-digit local WhatsApp numbers are normalized to `+65` E.164 form before saving.

## Fixed

- Tenant-maintained public email and WhatsApp values are no longer hidden from the public reservation share surface.
- Local WhatsApp phone input such as `68681234` can be saved as `+6568681234`.

## Migration

- Adds `V021__store_share_email.sql`, introducing nullable `stores.share_email` with a database check constraint.
- Existing `whatsapp_business_phone_e164` remains owned by the earlier WhatsApp migration.

## Permission

- No App Gate or permission changes.

## Risk

- Low API compatibility risk: new response fields are nullable additions.
- Low tenant-data risk: the new email column is nullable and existing tenants are unaffected until configured.

## Rollback Notes

- Revert the code changes and remove the new migration from deployments that have not applied it.
- If already applied, rollback requires dropping `stores.share_email` and `ck_stores_share_email` after confirming no tenant depends on the public email channel.
