# Queue Display / Call Screen Ad Terminal Design

Date: 2026-06-26

Status: Phase 1 text display plus restored media ads

## 1. Goal

The queue display slice adds a tenant-isolated call screen for the reservation queue product line. The screen shows the current called ticket when one exists. When no ticket is being called, it rotates the active text or image/video advertising carousel.

The text release introduced:

- Queue display terminal state.
- Text advertising carousel.
- Four platform seed text slides.
- Tenant-owned editable copies of the text slides.
- Tenant admin entry for call screen text configuration.
- Platform admin entry for seed text maintenance.
- App Gate permission `queue.display.view`.

The restored media release adds:

- Tenant-owned image/video media uploads.
- Media ad sets and media slide ordering.
- Platform-owned media seed template.
- Terminal playback for API-provided image/video slides.

## 2. Explicit Non-Goals

This slice does not include rich creative editors, payment, analytics, websocket streaming, device registration, public terminal tokens, CDN/object storage integration, transcoding, media security scanning, or media lifecycle cleanup automation.

## 3. Product Behavior

The terminal is a staff-facing full-screen page opened from the queue management page. It polls the state API. If `currentCall` is present, it renders the called number, customer display name, party size, and waiting preview. If `currentCall` is null, it renders text slides or media slides from the active tenant call screen configuration.

The default tenant experience is bootstrapped from the platform seed:

1. 欢迎光临
2. 今日推荐
3. 特惠活动
4. 会员专享

Tenant admins can edit their own copy. Editing tenant copy never mutates the platform seed. Tenant admins can also upload tenant-owned image/video assets and build a media carousel. Platform admins can maintain a platform-owned media seed template, but platform media assets are not tenant-owned files.

## 4. OOD / Module Boundaries

The feature is a focused `queuedisplay` slice:

- API layer owns HTTP mapping, actor permission checks, and stable error response codes.
- Application layer owns use cases, text/media normalization, version checks, and tenant-scope rules.
- Persistence layer owns SQL projections and V007/V009 schema access.
- Frontend API clients own wire format validation.
- Vue pages own presentation, loading/error/saving/uploading states, and text/media editing.

No reservation, queue call, skip, seating, or cleaning use case should contain advertising rules. Business APIs keep relying on App Gate; the queue display screen only consumes an already-authorized read projection.

## 5. Backend Contracts

Terminal:

- `GET /api/v1/stores/{storeId}/queue-display/state`
- Required App Gate: `reservation_queue` + `queue.display.view`
- Allowed staff roles: tenant admin, store manager, store staff
- Store scope must include `{storeId}`

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

Platform seed:

- `GET /api/v1/platform/call-screen/text-seed`
- `PATCH /api/v1/platform/call-screen/text-seed`
- `GET /api/v1/platform/call-screen/media-seed`
- `PATCH /api/v1/platform/call-screen/media-seed`
- `POST /api/v1/platform/call-screen/media`
- `GET /api/v1/platform/call-screen/media/{assetId}`

Ad set payloads support `adType`/`adMode` of `text` and `media`. The legacy reserved `image` value is normalized to `media`.

## 6. Database Boundary

The text slice owns:

- `V007__queue_display_ad_config.sql`

Allowed tables:

- `platform_call_screen_ad_seed_sets`
- `platform_call_screen_ad_seed_slides`
- `tenant_call_screen_ad_sets`
- `tenant_call_screen_text_slides`
- `store_call_screen_settings`

The restored media slice owns:

- `V009__call_screen_media_carousel.sql`

Media tables:

- `call_screen_media_assets`
- `platform_call_screen_media_seed_slides`
- `tenant_call_screen_media_slides`

Important constraints:

- Platform seed `ad_type` supports `text` and `media`
- Tenant ad set `ad_type` supports `text` and `media`
- Store setting `ad_mode` supports `text` and `media`
- Tenant slide scope is `(tenant_id, ad_set_id)`
- Store setting active set is scoped by `(active_ad_set_id, tenant_id)`
- Active text slides have unique sort order per tenant ad set

## 7. Frontend Boundary

Pages:

- `QueueDisplayPage.vue`
- `TenantAdminCallScreenPage.vue`
- `PlatformCallScreenSeedPage.vue`

Frontend clients:

- `queueDisplayApi.ts`
- `callScreenAdminApi.ts`
- `platformCallScreenSeedApi.ts`

Frontend types:

- `queueDisplay.ts`
- `callScreenAdmin.ts`
- `platformCallScreenSeed.ts`

The UI must include loading, error, saving, uploading, empty, and preview states. Media controls must use the real upload APIs and must not expose raw storage keys.

## 8. Permission Model

`queue.display.view` is required for the terminal state API and should be granted to validation staff accounts by migration seed data where applicable.

Platform seed maintenance accepts `platform.call_screen_ad.manage`; `platform.tenant.manage` is temporarily accepted for compatibility with the existing platform admin model.

Tenant call screen configuration uses existing tenant admin management permission boundaries.

## 9. Test Coverage

Required tests:

- V007 table, index, constraint, seed, and permission migration behavior.
- V009 media asset, platform seed, tenant slide, and tenant isolation constraints.
- Queue display API permission and store-scope rejection.
- Queue display fallback to default text slides and active media slide projection.
- Tenant seed clone and editable copy behavior.
- Tenant text/media ad set create/update/settings activation.
- Platform text/media seed get/update/version validation.
- UI implementation validation for terminal, tenant admin page, and platform seed page.
- App Gate required permission coverage for `queue.display.view`.

## 10. Remaining Later Boundary

Later work should define production storage, security scanning, lifecycle cleanup, CDN delivery, device activation tokens, analytics, and richer creative management. These are not part of the restored local media carousel slice.
