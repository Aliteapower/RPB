# Release Notes

## Version / Date

public-booking-contact-channels / 2026-06-29

## New

- Public booking context now returns tenant-maintained store contact channels: Google Map URL, contact phone, public email, and WhatsApp business phone.
- Public booking page shows available contact actions directly in the booking header.

## Changed

- Tenant admin share profile now supports maintaining a public email address.
- Singapore 8-digit local WhatsApp numbers are normalized to `+65` E.164 form before saving.

## Fixed

- Public booking no longer hides tenant-maintained email and WhatsApp contact configuration.
- WhatsApp local number input such as `68681234` can be saved as `+6568681234`.

## Migration

- Adds `V023__store_share_email.sql`, introducing nullable `stores.share_email` with a database check constraint.
- Existing `whatsapp_business_phone_e164` remains owned by the earlier WhatsApp migration.

## Permission

- No App Gate or permission changes.

## Risk

- Low API compatibility risk: new response fields are nullable additions.
- Low tenant-data risk: the new email column is nullable and existing tenants are unaffected until configured.

## Rollback Notes

- Revert the code changes and remove the new migration from deployments that have not applied it.
- If already applied, rollback requires dropping `stores.share_email` and `ck_stores_share_email` after confirming no tenant depends on the public email channel.
