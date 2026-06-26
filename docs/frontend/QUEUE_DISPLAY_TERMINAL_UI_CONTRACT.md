# Queue Display Terminal UI Contract

Status: Phase 1 text-only

## Page

`src/pages/QueueDisplayPage.vue`

Route:

- `/stores/:storeId/queue-display`

Entry:

- Queue ticket list opens the terminal in a new tab.

## Behavior

The terminal:

- Loads `GET /api/v1/stores/{storeId}/queue-display/state`.
- Polls by `ads.statePollSeconds`.
- Shows loading state.
- Shows current call state.
- Shows text advertising state when `currentCall` is null.
- Shows error/retry state.
- Rotates text slides by `ads.slideDurationSeconds`.
- Displays waiting count and waiting preview when configured.

The page must not hardcode platform seed slide copy. It renders only API-provided text slides.

## Phase 2 Not Implemented

Image/video carousel playback is deferred.
