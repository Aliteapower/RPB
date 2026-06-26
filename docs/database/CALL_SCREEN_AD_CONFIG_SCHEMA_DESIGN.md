# Call Screen Ad Config Schema Design

Status: Phase 1 text-only

## Migration

Phase 1 schema is owned by:

- `src/main/resources/db/migration/V007__queue_display_ad_config.sql`

No later migration is part of Phase 1.

## Tables

`platform_call_screen_ad_seed_sets`

- One platform seed set per seed key.
- `seed_key = restaurant_default`
- `ad_type = text`
- Soft delete and optimistic version fields.

`platform_call_screen_ad_seed_slides`

- Text seed slides owned by a platform seed set.
- Stores `title`, `subtitle`, `tagline`, `sort_order`, `status`.

`tenant_call_screen_ad_sets`

- Tenant-owned text ad set.
- Optional `source_seed_set_id` points to platform seed origin.
- `ad_type = text`

`tenant_call_screen_text_slides`

- Tenant-owned editable text slide copy.
- Foreign key includes `(ad_set_id, tenant_id)`.
- Active slides have unique `sort_order` per tenant ad set.

`store_call_screen_settings`

- Store-level active text ad set and timing settings.
- Store foreign key includes `(store_id, tenant_id)`.
- Active ad set foreign key includes `(active_ad_set_id, tenant_id)`.
- `ad_mode = text`

## Seed Data

V007 inserts or updates the `restaurant_default` seed with four text slides:

1. 欢迎光临
2. 今日推荐
3. 特惠活动
4. 会员专享

V007 also grants `queue.display.view` to validation accounts when the expected tenant, store, accounts, and store-access rows exist.

## Constraints

- Platform seed status is `active` or `disabled`.
- Platform seed type is `text`.
- Tenant ad set status is `active` or `disabled`.
- Tenant ad set type is `text`.
- Store setting mode is `text`.
- Store setting status is `active` or `disabled`.
- Slide duration is 3 to 60 seconds.
- State poll interval is 2 to 30 seconds.
- Tenant text slide sort order is positive.

## Phase 2 Not Implemented

Image/video carousel groups and asset storage require a separate schema design. Phase 1 schema must remain text-only.
