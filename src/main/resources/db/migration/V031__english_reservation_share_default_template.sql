-- Switch the platform reservation share seed to English without overwriting custom templates.
-- template_md5 values are md5(UTF8(normalized known default templates)).
with known_platform_defaults(template_md5) as (
    values
        ('0330ca8282c4ce3c608ebf3fa23c9c4a'),
        ('470d74c4357fd9e699ad6556e125d7a7'),
        ('2a5f65d472c69f8fb8eb60a64c6b4e1c')
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
  and (
      seed.display_name is distinct from 'Restaurant reservation confirmation template V1'
      or seed.locale is distinct from 'en-SG'
      or seed.template_text is distinct from english_default.template_text
  );

with known_platform_defaults(template_md5) as (
    values
        ('0330ca8282c4ce3c608ebf3fa23c9c4a'),
        ('470d74c4357fd9e699ad6556e125d7a7'),
        ('2a5f65d472c69f8fb8eb60a64c6b4e1c')
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

with known_i18n_english_defaults(template_md5) as (
    values
        ('cca07e5143a0d5e1a59040c6bd0aa77a')
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
update i18n_message_catalog catalog
set message = english_default.template_text,
    updated_at = now(),
    version = catalog.version + 1
from english_default
where catalog.tenant_id is null
  and catalog.store_id is null
  and catalog.i18n_key = 'reservation.share.restaurant_reservation_confirmation_v1'
  and catalog.locale = 'en-SG'
  and catalog.deleted_at is null
  and md5(convert_to(replace(replace(btrim(catalog.message), chr(13) || chr(10), chr(10)), chr(13), chr(10)), 'UTF8'))
      in (select template_md5 from known_i18n_english_defaults)
  and catalog.message is distinct from english_default.template_text;

with english_default(template_text) as (
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
insert into i18n_message_catalog (i18n_key, locale, message, status)
select 'reservation.share.restaurant_reservation_confirmation_v1', 'en-SG', english_default.template_text, 'active'
from english_default
where not exists (
    select 1
    from i18n_message_catalog existing
    where existing.tenant_id is null
      and existing.store_id is null
      and existing.i18n_key = 'reservation.share.restaurant_reservation_confirmation_v1'
      and existing.locale = 'en-SG'
      and existing.deleted_at is null
);
