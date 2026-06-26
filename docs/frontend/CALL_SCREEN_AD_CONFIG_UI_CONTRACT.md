# Call Screen Ad Config UI Contract

Status: Phase 1 text-only

## Page

`src/pages/TenantAdminCallScreenPage.vue`

Route:

- `/stores/:storeId/admin/call-screen`

Navigation:

- Tenant admin nav shows `叫号屏配置`

## Behavior

The page loads call screen settings and tenant text ad sets. It supports:

- Loading state.
- Error state.
- Saving state.
- Text ad set selection.
- Text slide title, subtitle, tagline, sort order, and status editing.
- Text preview.
- Save settings.
- Save text ad set.

## API Client

`src/api/callScreenAdminApi.ts`

Allowed calls:

- `getCallScreenSettings`
- `updateCallScreenSettings`
- `listCallScreenAdSets`
- `createCallScreenAdSet`
- `getCallScreenAdSet`
- `updateCallScreenAdSet`

## Types

`src/types/callScreenAdmin.ts`

Allowed ad mode/type:

- `text`

## Phase 2 Not Implemented

Image/video carousel editing and binary asset controls must not render in Phase 1.
