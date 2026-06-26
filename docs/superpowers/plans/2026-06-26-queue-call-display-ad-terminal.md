# Queue Display / Call Screen Implementation Plan

Date: 2026-06-26

Status: Phase 1 text display plus restored media ads

## Scope

Implement queue display and call screen ad configuration:

- Full-screen call terminal.
- Text ad rotation when no ticket is being called.
- Four platform seed text slides.
- Tenant-owned editable text slide copies.
- Tenant admin configuration page.
- Platform admin seed page.
- `queue.display.view` App Gate permission.
- Image/video media ad groups restored from the earlier media implementation.
- Platform media seed template.
- Tenant media upload and editable media carousel.

## Architecture

Create a `queuedisplay` backend slice with explicit layers:

- API controllers map HTTP requests and stable error codes.
- Application services enforce text/media rules, optimistic version checks, and tenant/store scope.
- Repositories hide SQL and table names from controllers and services.
- Migration V007 owns text ad schema; V009 owns media asset and media slide schema.
- Vue pages use typed API clients and never inspect tenant IDs directly.

This preserves OOD boundaries: terminal display, tenant admin text configuration, and platform seed maintenance are separate use cases with shared text slide value objects where useful.

## Phase 1 Steps

1. Add backend application records for queue display state, text ads, settings, ad sets, and platform seed slides.
2. Add `QueueDisplayController` with `GET /api/v1/stores/{storeId}/queue-display/state`.
3. Add `CallScreenAdminController` for store-scoped tenant text settings and text ad set management.
4. Add `PlatformCallScreenSeedController` for platform text seed get/update.
5. Add V007 migration with text seed, tenant text slides, tenant text ad sets, and store settings.
6. Add V009 migration with media assets, platform media seed slides, tenant media slides, and tenant-scope triggers.
7. Seed four platform text slides and `queue.display.view` where validation accounts exist.
8. Add frontend typed API clients and response validation.
9. Add `QueueDisplayPage.vue`, `TenantAdminCallScreenPage.vue`, and `PlatformCallScreenSeedPage.vue`.
10. Add router/nav entries for terminal, tenant admin call screen, and platform seed maintenance.
11. Add migration, application, API, App Gate, and UI validation tests.

## Database Plan

V007 covers the text slice.

Tables:

- `platform_call_screen_ad_seed_sets`
- `platform_call_screen_ad_seed_slides`
- `tenant_call_screen_ad_sets`
- `tenant_call_screen_text_slides`
- `store_call_screen_settings`

Constraints:

- `ad_type = 'text'` for platform seed sets.
- `ad_type = 'text'` for tenant ad sets.
- `ad_mode = 'text'` for store settings.
- Tenant slide foreign keys include tenant scope.
- Store settings active ad set foreign key includes tenant scope.
- Active text slide sort order is unique per tenant ad set.

V009 covers restored media ads:

- `call_screen_media_assets`
- `platform_call_screen_media_seed_slides`
- `tenant_call_screen_media_slides`

## API Plan

Queue display:

- `GET /api/v1/stores/{storeId}/queue-display/state`

Tenant admin:

- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/settings`
- `PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/settings`
- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets`
- `POST /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets`
- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}`
- `PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}`
- `POST /api/v1/stores/{storeId}/tenant-admin/call-screen/media`
- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/media/{assetId}`
- `GET /api/v1/stores/{storeId}/queue-display/media/{assetId}`

Platform:

- `GET /api/v1/platform/call-screen/text-seed`
- `PATCH /api/v1/platform/call-screen/text-seed`
- `GET /api/v1/platform/call-screen/media-seed`
- `PATCH /api/v1/platform/call-screen/media-seed`
- `POST /api/v1/platform/call-screen/media`
- `GET /api/v1/platform/call-screen/media/{assetId}`

Text payloads use `title`, `subtitle`, `tagline`, `sortOrder`, `status`, and optional `version`. Media payloads use `mediaAssetId`, `mediaKind`, `mediaUrl`, `title`, `altText`, `sortOrder`, `status`, and optional `version`.

## Frontend Plan

Terminal page:

- Load state on mount.
- Poll using `statePollSeconds`.
- Render loading, calling, advertising, and error states.
- Rotate text or media slides using `slideDurationSeconds`.
- Avoid built-in hardcoded slide content.

Tenant admin page:

- Load settings and ad sets.
- Create and edit text/media ad sets.
- Edit title, subtitle, tagline, sort order, and status.
- Save settings and ad sets with clear saving/error states.
- Upload image/video assets and show text/media previews.

Platform seed page:

- Load platform text seed.
- Edit four or more text slides.
- Save with optimistic version.
- Maintain platform text and media seed previews.

## Test Plan

Migration:

- V007 creates text tables.
- V009 creates media asset and slide tables.
- V007 seeds four text slides.
- V007 grants `queue.display.view` to validation accounts.
- V007/V009 enforce compatible `ad_type` and `ad_mode`.
- V007 remains replay-safe.

Backend:

- Queue display state returns current call, text ads, or media ads.
- Store scope and permission failures return stable errors.
- Tenant text/media set clone, create, update, upload, and settings activation work.
- Platform text seed update validates sort order and version.

Frontend:

- API clients point to approved endpoints.
- Pages are route/nav reachable.
- Loading, error, saving, and preview code paths exist.

## Remaining Later Boundary

Later work should cover production-grade object storage, security scanning, media lifecycle cleanup, CDN delivery, device activation tokens, analytics, and richer creative management.
