# Platform Call Screen Seed Implementation Report

## Scope

Implemented platform-admin maintenance for default call-screen text and image/video seed templates at `/platform/call-screen/text-seed`.

## Changed Files

- `src/types/platformCallScreenSeed.ts`
- `src/api/platformCallScreenSeedApi.ts`
- `src/pages/PlatformCallScreenSeedPage.vue`
- `src/components/platform/PlatformAdminNav.vue`
- `src/router/index.ts`
- `src/main/java/com/rpb/reservation/queuedisplay/api/PlatformCallScreenSeedController.java`
- `src/main/java/com/rpb/reservation/queuedisplay/application/PlatformCallScreenSeedService.java`
- `src/main/java/com/rpb/reservation/queuedisplay/persistence/PlatformCallScreenSeedRepository.java`
- `src/main/resources/db/migration/V007__queue_display_ad_config.sql`
- `src/test/java/com/rpb/reservation/queuedisplay/api/PlatformCallScreenSeedControllerTest.java`
- `src/test/java/com/rpb/reservation/queuedisplay/application/PlatformCallScreenSeedServiceTest.java`
- `src/test/java/com/rpb/reservation/queuedisplay/api/PlatformCallScreenSeedLocalRuntimeSecurityTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/PlatformCallScreenSeedUiImplementationValidationTest.java`

## API Endpoints

- `GET /api/v1/platform/call-screen/text-seed`
- `PATCH /api/v1/platform/call-screen/text-seed`
- `GET /api/v1/platform/call-screen/media-seed`
- `PATCH /api/v1/platform/call-screen/media-seed`
- `POST /api/v1/platform/call-screen/media`
- `GET /api/v1/platform/call-screen/media/{assetId}`

## Permission

- Role: `platform_admin`
- Permission: `platform.call_screen_ad.manage`; existing `platform_admin` accounts with `platform.tenant.manage` are accepted for compatibility with already-issued platform sessions.

This permission is seeded for local validation platform admin accounts by `V007__queue_display_ad_config.sql`.

## Runtime Behavior

- Platform admins can edit the platform-owned `restaurant_default` text seed template.
- Platform admins can upload platform image/video assets and edit the `restaurant_media_default` media seed template.
- Existing tenant-owned ad sets and text slides are not updated when the platform seed changes.
- New tenant default clones continue to copy from the active platform seed at clone time.
- Media seed edits do not mutate existing tenant copies.

## Validation

- `mvn -q "-Dtest=PlatformCallScreenSeedServiceTest,PlatformCallScreenSeedControllerTest,PlatformCallScreenSeedLocalRuntimeSecurityTest,PlatformCallScreenSeedUiImplementationValidationTest,QueueDisplayAdConfigMigrationTest,AuthMigrationTest" test`: PASS
- `npm run build`: PASS

## Rollback Notes

Rollback can remove the platform page, API client, controller, service, and repository without changing tenant call-screen configuration rows. If rolling back after migration, leave `platform_call_screen_ad_seed_*` rows in place because tenant text slides can reference platform seed slide ids through `source_seed_slide_id`.
