# Platform Call Screen Seed UI Contract

Status: Phase 1 text-only

## Page

`src/pages/PlatformCallScreenSeedPage.vue`

Route:

- `/platform/call-screen/text-seed`

Navigation:

- Platform admin nav shows `叫号模板`

## Behavior

The page loads and edits the platform text seed:

- Loading state.
- Error state.
- Saving state.
- Text slide editor.
- Text preview.
- Optimistic version save.

Editing platform seed text does not alter existing tenant copies.

## API Client

`src/api/platformCallScreenSeedApi.ts`

Allowed calls:

- `getPlatformCallScreenTextSeed`
- `updatePlatformCallScreenTextSeed`

## Phase 2 Not Implemented

Image/video seed templates and asset management are deferred.
