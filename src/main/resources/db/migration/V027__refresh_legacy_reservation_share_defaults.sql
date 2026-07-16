-- Refresh stores that were populated with known legacy default templates after V018 ran.
-- Custom tenant templates are intentionally left untouched.
with old_default_templates(template_md5) as (
    values
        ('0330ca8282c4ce3c608ebf3fa23c9c4a'),
        ('470d74c4357fd9e699ad6556e125d7a7')
),
active_seed as (
    select template_text
    from platform_reservation_share_template_seeds
    where seed_key = 'restaurant_reservation_confirmation_v1'
      and status = 'active'
      and deleted_at is null
)
update stores
set reservation_share_template = active_seed.template_text,
    updated_at = now(),
    version = stores.version + 1
from active_seed
where stores.deleted_at is null
  and md5(convert_to(replace(replace(btrim(stores.reservation_share_template), chr(13) || chr(10), chr(10)), chr(13), chr(10)), 'UTF8'))
      in (select template_md5 from old_default_templates)
  and stores.reservation_share_template is distinct from active_seed.template_text;
