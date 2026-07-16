# Call Screen Ad Config Schema Design

## Tables

```text
platform_call_screen_ad_seed_sets
platform_call_screen_ad_seed_slides
tenant_call_screen_ad_sets
tenant_call_screen_text_slides
store_call_screen_settings
```

## Scope Rules

Platform seed rows have no `tenant_id` and no `store_id`. They are templates only.

Tenant ad sets and slides require `tenant_id` and do not include `store_id`. Tenant rows must never be shared across tenants.

Store settings require both `tenant_id` and `store_id`. `store_call_screen_settings` references `stores(id, tenant_id)` and is unique by active `(tenant_id, store_id)`.

## Isolation Constraints

`tenant_call_screen_ad_sets` exposes `unique (id, tenant_id)` so dependent rows can enforce same-tenant references.

`tenant_call_screen_text_slides` references `(ad_set_id, tenant_id)` to prevent cross-tenant slide attachment.

`store_call_screen_settings.active_ad_set_id` references `(id, tenant_id)` on `tenant_call_screen_ad_sets`, so a store cannot activate another tenant's ad set.

PostgreSQL triggers enforce type consistency without exposing duplicate business columns:

- `tenant_call_screen_text_slides.ad_set_id` must reference a tenant ad set whose `ad_type` is `text`.
- `store_call_screen_settings.ad_mode` must match the active tenant ad set `ad_type` when `active_ad_set_id` is not null.

## Validation

Status values are constrained to `active` or `disabled`.

Ad type values are constrained to `text` or `image`.

Slide `sort_order` must be greater than 0. Active tenant text slides use a partial unique index by `(tenant_id, ad_set_id, sort_order)` where `status = 'active'` and `deleted_at is null`.

`slide_duration_seconds` is constrained to 3 through 60. `state_poll_seconds` is constrained to 2 through 30.

## Seed Data

Migration `V007__queue_display_ad_config.sql` seeds platform set `restaurant_default` with these active text slides:

| Sort | Title | Subtitle | Tagline |
|---:|---|---|---|
| 1 | 欢迎光临 | 食刻 · 餐厅 | 新鲜食材 · 匠心烹饪 · 极致服务 |
| 2 | 今日推荐 | 招牌炭烤牛排 | 精选澳洲谷饲牛肉 · 现点现烤 |
| 3 | 特惠活动 | 工作日午餐8折 | 周一至周五 11:00-14:00 全场8折 |
| 4 | 会员专享 | 充值满赠 | 充500送50 · 充1000送120 |

The same migration seeds `queue.display.view` into `auth_account_permissions` for local validation users `sysadmin`, `20000000`, and `1000` using a `not exists` guard.

The permission seed is scoped to the local validation tenant `10000000-0000-0000-0000-000000000983`, store `20000000-0000-0000-0000-000000000983`, and matching account store access. It must not grant `queue.display.view` to same-named accounts in other tenants or stores.

The migration also seeds `platform.call_screen_ad.manage` for platform admin accounts so the platform backend can maintain the platform-owned text seed template. This permission is platform-scoped and must not be granted to tenant admin or store staff accounts by the seed.

## Platform Seed Maintenance

Platform seed maintenance updates only `platform_call_screen_ad_seed_sets` and `platform_call_screen_ad_seed_slides`.

Existing tenant copies in `tenant_call_screen_ad_sets` and `tenant_call_screen_text_slides` remain tenant-owned and are not overwritten by later platform seed changes. Tenant default clone uses platform seed rows only at clone time.

## Text Schema Boundary

`tenant_call_screen_ad_sets.ad_type` and `store_call_screen_settings.ad_mode` keep a minimal `image` value boundary for the approved Phase 2 design. Phase 1 does not create image slide tables, image upload, media storage, image carousel UI, or image APIs.

## Media Schema Addendum

Migration `V009__call_screen_media_carousel.sql` expands ad mode/type compatibility to `media` while retaining the earlier reserved `image` value.

New tables:

```text
call_screen_media_assets
platform_call_screen_media_seed_slides
tenant_call_screen_media_slides
```

`call_screen_media_assets` stores metadata only. File bytes are written to the configured local media storage root by `CallScreenMediaStorage`. Assets have `owner_scope = platform | tenant`; tenant assets require `tenant_id`, while platform assets require `tenant_id is null`.

`tenant_call_screen_media_slides` references `(ad_set_id, tenant_id)` and has triggers that enforce:

- the target ad set is a media-compatible ad set;
- the media asset belongs to the same tenant;
- `media_kind` matches the referenced asset.

`platform_call_screen_media_seed_slides` references platform-owned media assets only. It is template data and does not grant cross-tenant access to platform media.
