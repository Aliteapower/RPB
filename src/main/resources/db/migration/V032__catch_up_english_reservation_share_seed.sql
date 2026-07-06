-- Catch up production seeds that still contain the original V014 default template.
-- V031 intentionally skipped unknown hashes; this hash was verified from the public deployment.
with known_platform_defaults(template_md5) as (
    values
        ('912f67b410744e46ca37fdff6bf47466')
),
english_default(template_text) as (
    values ($template$Dear {{contactName}} {{guestSalutation}},

Thank you for choosing {{storeName}}. We are pleased to confirm your booking details below:

Booking no.: {{reservationNo}}

Date: {{reservationDate}}

Time: {{reservationTime}}

Party size: {{partySize}} pax

Table: {{tableCode}} (reserved)

Hold time: To protect every guest's dining experience, we will hold your table for {{holdMinutes}} minutes. If you arrive after the hold time, the table may be released.

Arrival note: {{arrivalNote}}

Store address: {{storeAddress}}

Contact phone: {{storePhone}}

To change or cancel, please contact the store at least 2 hours ahead.

We look forward to serving you.

Best regards,
{{storeName}} Reservations$template$)
)
update platform_reservation_share_template_seeds seed
set display_name = 'Restaurant reservation confirmation template V1',
    locale = 'en-SG',
    template_text = english_default.template_text,
    updated_at = now(),
    version = seed.version + 1
from english_default
where seed.seed_key = 'restaurant_reservation_confirmation_v1'
  and seed.deleted_at is null
  and md5(convert_to(replace(replace(btrim(seed.template_text), chr(13) || chr(10), chr(10)), chr(13), chr(10)), 'UTF8'))
      in (select template_md5 from known_platform_defaults)
  and seed.template_text is distinct from english_default.template_text;

with known_platform_defaults(template_md5) as (
    values
        ('912f67b410744e46ca37fdff6bf47466')
),
english_default(template_text) as (
    values ($template$Dear {{contactName}} {{guestSalutation}},

Thank you for choosing {{storeName}}. We are pleased to confirm your booking details below:

Booking no.: {{reservationNo}}

Date: {{reservationDate}}

Time: {{reservationTime}}

Party size: {{partySize}} pax

Table: {{tableCode}} (reserved)

Hold time: To protect every guest's dining experience, we will hold your table for {{holdMinutes}} minutes. If you arrive after the hold time, the table may be released.

Arrival note: {{arrivalNote}}

Store address: {{storeAddress}}

Contact phone: {{storePhone}}

To change or cancel, please contact the store at least 2 hours ahead.

We look forward to serving you.

Best regards,
{{storeName}} Reservations$template$)
)
update stores
set reservation_share_template = english_default.template_text,
    updated_at = now(),
    version = stores.version + 1
from english_default
where stores.deleted_at is null
  and md5(convert_to(replace(replace(btrim(stores.reservation_share_template), chr(13) || chr(10), chr(10)), chr(13), chr(10)), 'UTF8'))
      in (select template_md5 from known_platform_defaults)
  and stores.reservation_share_template is distinct from english_default.template_text;
