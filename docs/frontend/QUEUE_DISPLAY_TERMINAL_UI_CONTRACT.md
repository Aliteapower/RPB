# Queue Display Terminal UI Contract

## Route

```text
/stores/:storeId/queue-display
```

The terminal calls:

```http
GET /api/v1/stores/{storeId}/queue-display/state
```

The API requires `queue.display.view`.

## States

`loading`: show a stable full-screen loading surface while the first state request is pending.

`calling`: show the current called ticket when `currentCall` is present.

`advertising`: rotate returned text or media slides when `currentCall` is null.

`error`: keep layout stable and show a restrained failure state when the state API cannot be loaded.

## Display Contract

Calling state must emphasize `displayNumber`, `customerDisplayName`, `partySizeGroup`, and waiting count. It must not expose raw phone numbers or internal IDs.

Advertising state must render text slides with `title`, `subtitle`, and `tagline`, or media slides with image/video playback and safe fallback text. It uses `slideDurationSeconds` from the state response.

Polling uses `statePollSeconds` from configuration when provided by the API response contract. Default expected value is 3 seconds.

## Media Scope

The terminal supports API-provided image/video media slides. It serves media through queue-display scoped URLs and must not expose raw storage keys or cross-tenant asset access.
