# Release Notes

## Version / Date

tenant-customer-management / 2026-07-06

## New

- Tenant admin ERP now has a Customer management page for list/search, create, edit, and archive.
- Customer maintenance supports name, `nickname` salutation, optional E.164 phone, and optional email.
- Reservation creation and public booking creation now pass customer profile fields into the shared Customer profile resolver.

## Changed

- Customer profile enrichment is owned by the Customer module and reused by reservation and public booking flows.
- Public booking contact details now include optional customer name, salutation, and email fields in addition to phone.

## Fixed

- Reservation-created and public-booking-created customers can now retain optional email and salutation information instead of only phone/name.

## Migration

- No new database migration. This uses the existing nullable `customers.email`, `display_name`, `nickname`, and `phone_e164` fields.

## Permission

- No new App Gate permission. The Customer page is under the existing tenant admin backend scope.

## Risk

- Medium tenant-data risk: reservation and public booking flows now update existing Customer profile fields when matching by customer ID or phone.
- Low API compatibility risk: new request/response fields are nullable additions.

## Rollback Notes

- Revert the Customer management API/UI and the reservation/public booking profile-field additions.
- Existing customer rows remain compatible because no schema changes are introduced.
