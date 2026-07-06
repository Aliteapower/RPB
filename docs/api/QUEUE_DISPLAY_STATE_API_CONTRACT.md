# Queue Display State API Contract

## Endpoint

```http
GET /api/v1/stores/{storeId}/queue-display/state
```

This endpoint returns the read-only terminal projection for a store queue display. It must not call, skip, seat, cancel, or otherwise mutate queue state.

Optional query parameters:

| Name | Values | Purpose |
| --- | --- | --- |
| `locale` | `zh-CN`, `en-SG` | Resolve configurable display copy from the persisted i18n catalog. Unsupported or missing values fall back to `zh-CN`. |

## Permission

```text
app_key = reservation_queue
permission = queue.display.view
```

The authenticated actor must be tenant/store scoped to `{storeId}`. Phase 1 does not provide public unauthenticated terminal tokens.

## Success Response

```json
{
  "success": true,
  "serverNow": "2026-06-26T03:03:00Z",
  "storeTime": {
    "timezone": "Asia/Singapore",
    "timeText": "03:03",
    "businessDate": "2026-06-26"
  },
  "currentCall": {
    "queueTicketId": "91000000-0000-0000-0000-000000000002",
    "displayNumber": "A002",
    "customerDisplayName": "孙女士",
    "partySize": 2,
    "partySizeGroup": "1-2",
    "calledAt": "2026-06-26T03:01:00Z",
    "holdUntilAt": "2026-06-26T03:04:00Z"
  },
  "waiting": {
    "count": 1,
    "preview": [
      {
        "displayNumber": "A001",
        "customerDisplayName": "赵先生",
        "partySize": 3,
        "partySizeGroup": "3-4"
      }
    ]
  },
  "ads": {
    "mode": "text",
    "slideDurationSeconds": 5,
    "statePollSeconds": 3,
    "slides": [
      {
        "slideId": "text-1",
        "title": "欢迎光临",
        "subtitle": "食刻 · 餐厅",
        "tagline": "新鲜食材 · 匠心烹饪 · 极致服务"
      }
    ]
  }
}
```

`currentCall` is nullable. When it is null, the terminal enters advertising state and uses the returned text slides.

Text slide `title`, `subtitle`, and `tagline` values are resolved at runtime from `i18n_message_catalog` when the slide is linked to an authorized i18n key. Resolution follows store override, tenant override, platform default, then `zh-CN` fallback. Legacy slide table text remains the final fallback for rows that are not linked to a catalog key.

## Media Addendum

When the store activates a media ad set, `ads.mode` is `media` and slides use authenticated media URLs:

```json
{
  "mode": "media",
  "slideDurationSeconds": 8,
  "statePollSeconds": 4,
  "slides": [
    {
      "slideId": "media-slide-1",
      "title": "新品海报",
      "mediaKind": "image",
      "mediaUrl": "/api/v1/stores/20000000-0000-0000-0000-000000000983/queue-display/media/8a000000-0000-0000-0000-000000000001",
      "altText": "新品推荐图"
    },
    {
      "slideId": "media-slide-2",
      "title": "环境短片",
      "mediaKind": "video",
      "mediaUrl": "/api/v1/stores/20000000-0000-0000-0000-000000000983/queue-display/media/8a000000-0000-0000-0000-000000000002",
      "altText": "餐厅环境视频"
    }
  ]
}
```

The state endpoint remains read-only. Media bytes are served by `GET /api/v1/stores/{storeId}/queue-display/media/{assetId}` after `queue.display.view` and active ad-set membership checks.
