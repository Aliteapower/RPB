# Call Screen Ad Config Implementation Report

## Scope

Implemented tenant-admin call-screen text and image/video ad configuration at `/stores/:storeId/admin/call-screen`.

## Changed Files

- `src/types/callScreenAdmin.ts`
- `src/api/callScreenAdminApi.ts`
- `src/pages/TenantAdminCallScreenPage.vue`
- `src/components/tenant-admin/TenantAdminNav.vue`
- `src/router/index.ts`
- `src/test/java/com/rpb/reservation/appgate/ui/QueueDisplayCallScreenUiImplementationValidationTest.java`

## API Endpoints

- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/settings`
- `PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/settings`
- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets`
- `POST /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets`
- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}`
- `PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}`
- `POST /api/v1/stores/{storeId}/tenant-admin/call-screen/media`
- `GET /api/v1/stores/{storeId}/tenant-admin/call-screen/media/{assetId}`

## Permission

- Role: `tenant_admin`
- Permission: `tenant.admin.manage`

Tenant scope comes from the authenticated actor. Store scope comes from the route `{storeId}`. The frontend does not send or trust a request-body `tenantId`.

## Runtime Behavior

- Loads store call-screen settings and tenant text/media ad sets through real APIs.
- Allows tenant admins to select the active text or media ad set, tune slide duration and poll interval, and toggle waiting preview.
- Allows tenant admins to edit text slide title, subtitle, tagline, sort order, and status.
- Allows tenant admins to upload image/video assets and edit media slide title, alt text, sort order, and status.
- Provides a dark preview panel matching the terminal style.

## Migration

Backed by `V007__queue_display_ad_config.sql`, which seeds the platform text library and supports tenant-owned editable copies, plus `V009__call_screen_media_carousel.sql`, which adds media assets and media slide tables.

## Validation

- `npm run build`: PASS
- `mvn "-Dtest=QueueDisplayCallScreenUiImplementationValidationTest,QueueCallUiImplementationValidationTest,QueueSkipUiImplementationValidationTest,QueueRejoinUiImplementationValidationTest,ReservationArrivedToQueueUiImplementationValidationTest,SeatingFromCalledQueueUiImplementationValidationTest" test`: PASS

## Known Limitations

- Phase 1 edits the tenant default text set returned by the backend. Creating additional text groups is supported by the API client, but the first UI flow focuses on editing and activating the seeded tenant copy.
- Platform-owned seed template maintenance is implemented separately at `/platform/call-screen/text-seed`; saving tenant copy edits does not update platform seed rows.
