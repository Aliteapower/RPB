# Queue Display / Call Screen Ad Terminal Design

Date: 2026-06-26

Status: Phase 1 text-only design

## 1. Goal

Phase 1 adds a tenant-isolated call screen for the reservation queue product line. The screen shows the current called ticket when one exists. When no ticket is being called, it rotates text advertising copy.

The first release is deliberately small:

- Queue display terminal state.
- Text advertising carousel.
- Four platform seed text slides.
- Tenant-owned editable copies of the text slides.
- Tenant admin entry for call screen text configuration.
- Platform admin entry for seed text maintenance.
- App Gate permission `queue.display.view`.

## 2. Explicit Non-Goals

Phase 1 does not include binary asset handling, image/video templates, first-party asset storage, external asset URLs, rich creative editors, payment, analytics, websocket streaming, or device registration.

Phase 2 not implemented: image/video carousel groups and asset management remain a later design gate. They must not appear in Phase 1 APIs, database tables, Vue forms, repository methods, or validation tests as implemented functionality.

## 3. Product Behavior

The terminal is a staff-facing full-screen page opened from the queue management page. It polls the state API. If `currentCall` is present, it renders the called number, customer display name, party size, and waiting preview. If `currentCall` is null, it renders text slides from the active tenant call screen configuration.

The default tenant experience is bootstrapped from the platform seed:

1. 欢迎光临
2. 今日推荐
3. 特惠活动
4. 会员专享

Tenant admins can edit their own copy. Editing tenant copy never mutates the platform seed.

## 4. OOD / Module Boundaries

The feature is a focused `queuedisplay` slice:

- API layer owns HTTP mapping, actor permission checks, and stable error response codes.
- Application layer owns use cases, text-only normalization, version checks, and tenant-scope rules.
- Persistence layer owns SQL projections and V007 schema access.
- Frontend API clients own wire format validation.
- Vue pages own presentation, loading/error/saving states, and text-only editing.

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

Platform seed:

- `GET /api/v1/platform/call-screen/text-seed`
- `PATCH /api/v1/platform/call-screen/text-seed`

All ad set payloads are text-only. `adType` and `adMode` are `text`.

## 6. Database Boundary

Phase 1 owns one migration:

- `V007__queue_display_ad_config.sql`

Allowed tables:

- `platform_call_screen_ad_seed_sets`
- `platform_call_screen_ad_seed_slides`
- `tenant_call_screen_ad_sets`
- `tenant_call_screen_text_slides`
- `store_call_screen_settings`

Important constraints:

- Platform seed `ad_type = 'text'`
- Tenant ad set `ad_type = 'text'`
- Store setting `ad_mode = 'text'`
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

The UI must include loading, error, saving, empty, and preview states. It must not expose binary asset controls or non-text ad mode controls in Phase 1.

## 8. Permission Model

`queue.display.view` is required for the terminal state API and should be granted to validation staff accounts by migration seed data where applicable.

Platform seed maintenance accepts `platform.call_screen_ad.manage`; `platform.tenant.manage` is temporarily accepted for compatibility with the existing platform admin model.

Tenant call screen configuration uses existing tenant admin management permission boundaries.

## 9. Test Coverage

Required tests:

- V007 table, index, constraint, seed, and permission migration behavior.
- Text-only schema boundary.
- Queue display API permission and store-scope rejection.
- Queue display fallback to default text slides.
- Tenant seed clone and editable copy behavior.
- Tenant text ad set create/update/settings activation.
- Platform text seed get/update/version validation.
- UI implementation validation for terminal, tenant admin page, and platform seed page.
- App Gate required permission coverage for `queue.display.view`.

## 10. Phase 2 Boundary

Phase 2 not implemented. A later spec must define asset ownership, validation, storage, lifecycle, security scanning, tenant isolation, API contracts, and terminal playback behavior before any image/video carousel work enters code.
