# Queue Display / Call Screen Phase 1 Implementation Plan

Date: 2026-06-26

Status: Phase 1 text-only plan

## Scope

Implement a text-only queue display and call screen ad configuration slice:

- Full-screen call terminal.
- Text ad rotation when no ticket is being called.
- Four platform seed text slides.
- Tenant-owned editable text slide copies.
- Tenant admin configuration page.
- Platform admin seed page.
- `queue.display.view` App Gate permission.

Phase 2 not implemented: image/video carousel groups and asset management stay outside this plan.

## Architecture

Create a `queuedisplay` backend slice with explicit layers:

- API controllers map HTTP requests and stable error codes.
- Application services enforce text-only rules, optimistic version checks, and tenant/store scope.
- Repositories hide SQL and table names from controllers and services.
- Migration V007 owns all text ad schema.
- Vue pages use typed API clients and never inspect tenant IDs directly.

This preserves OOD boundaries: terminal display, tenant admin text configuration, and platform seed maintenance are separate use cases with shared text slide value objects where useful.

## Phase 1 Steps

1. Add backend application records for queue display state, text ads, settings, ad sets, and platform seed slides.
2. Add `QueueDisplayController` with `GET /api/v1/stores/{storeId}/queue-display/state`.
3. Add `CallScreenAdminController` for store-scoped tenant text settings and text ad set management.
4. Add `PlatformCallScreenSeedController` for platform text seed get/update.
5. Add V007 migration with only text seed, tenant text slides, tenant text ad sets, and store settings.
6. Seed four platform text slides and `queue.display.view` where validation accounts exist.
7. Add frontend typed API clients and response validation.
8. Add `QueueDisplayPage.vue`, `TenantAdminCallScreenPage.vue`, and `PlatformCallScreenSeedPage.vue`.
9. Add router/nav entries for terminal, tenant admin call screen, and platform seed maintenance.
10. Add migration, application, API, App Gate, and UI validation tests.

## Database Plan

Only V007 is part of Phase 1.

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

No Phase 2 asset tables are included.

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

Platform:

- `GET /api/v1/platform/call-screen/text-seed`
- `PATCH /api/v1/platform/call-screen/text-seed`

All payloads use text slides with `title`, `subtitle`, `tagline`, `sortOrder`, `status`, and optional `version`.

## Frontend Plan

Terminal page:

- Load state on mount.
- Poll using `statePollSeconds`.
- Render loading, calling, advertising, and error states.
- Rotate text slides using `slideDurationSeconds`.
- Avoid built-in hardcoded slide content.

Tenant admin page:

- Load settings and ad sets.
- Create and edit text ad sets.
- Edit title, subtitle, tagline, sort order, and status.
- Save settings and ad sets with clear saving/error states.
- Show a text-only preview.

Platform seed page:

- Load platform text seed.
- Edit four or more text slides.
- Save with optimistic version.
- Show text-only preview.

## Test Plan

Migration:

- V007 creates only Phase 1 tables.
- V007 seeds four text slides.
- V007 grants `queue.display.view` to validation accounts.
- V007 enforces text-only `ad_type` and `ad_mode`.
- V007 remains replay-safe.

Backend:

- Queue display state returns current call or text ads.
- Store scope and permission failures return stable errors.
- Tenant text set clone, create, update, and settings activation work.
- Platform text seed update validates sort order and version.

Frontend:

- API clients point to approved endpoints.
- Pages are route/nav reachable.
- Loading, error, saving, and preview code paths exist.

## Phase 2 Not Implemented

A separate Phase 2 spec is required before adding image/video carousel groups, asset storage, asset validation, platform asset templates, tenant asset ownership, or terminal playback of binary assets.
