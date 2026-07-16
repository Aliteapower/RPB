# Platform Call Screen Seed API Contract

## Endpoints

```http
GET /api/v1/platform/call-screen/text-seed
PATCH /api/v1/platform/call-screen/text-seed
```

## Permission

These endpoints require:

```text
role = platform_admin
permission = platform.call_screen_ad.manage
```

Existing platform tenant managers are also accepted for compatibility:

```text
role = platform_admin
permission = platform.tenant.manage
```

`platform.call_screen_ad.manage` remains the dedicated platform-backoffice permission for this module. `platform.tenant.manage` is accepted so already logged-in platform accounts can enter the new seed-template page before their session has been refreshed with the new permission. This is not an App Gate permission and does not use a `{storeId}` route scope.

## Response Shape

```json
{
  "success": true,
  "seedSet": {
    "id": "82000000-0000-0000-0000-000000000001",
    "seedKey": "restaurant_default",
    "displayName": "餐厅默认叫号屏文案",
    "adType": "text",
    "status": "active",
    "slides": [
      {
        "id": "83000000-0000-0000-0000-000000000001",
        "title": "欢迎光临",
        "subtitle": "食刻 · 餐厅",
        "tagline": "新鲜食材 · 匠心烹饪 · 极致服务",
        "sortOrder": 1,
        "status": "active",
        "version": 0
      }
    ],
    "version": 0
  }
}
```

## PATCH Request Shape

```json
{
  "displayName": "餐厅默认叫号屏文案",
  "status": "active",
  "slides": [
    {
      "id": "83000000-0000-0000-0000-000000000001",
      "title": "欢迎光临",
      "subtitle": "食刻 · 餐厅",
      "tagline": "新鲜食材 · 匠心烹饪 · 极致服务",
      "sortOrder": 1,
      "status": "active",
      "version": 0
    }
  ],
  "version": 0
}
```

The request must not include `tenantId` or `storeId`.

## Rules

- Phase 1 maintains only the `restaurant_default` text seed set.
- `sortOrder` must be a positive integer and unique within the submitted text seed.
- `status` must be `active` or `disabled`.
- `version` is checked optimistically. A stale version returns `VERSION_CONFLICT`.
- Updating platform seed rows must not update existing `tenant_call_screen_ad_sets` or `tenant_call_screen_text_slides`.
- New tenant clones use the current active platform seed data at clone time.

## Error Codes

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `UNAUTHENTICATED` | No current actor is available. |
| 403 | `FORBIDDEN` | Actor is missing `platform_admin`, or has neither `platform.call_screen_ad.manage` nor `platform.tenant.manage`. |
| 400 | `REQUEST_INVALID` | Request body, status, slide text, or sort order is invalid. |
| 404 | `SEED_NOT_FOUND` | The default text seed set is missing or deleted. |
| 409 | `VERSION_CONFLICT` | Optimistic `version` does not match the current seed version. |
| 500 | `PERSISTENCE_ERROR` | Database access failed. |

## Media Seed Addendum

The platform page implements platform-owned media seed template maintenance for image and video carousel templates.

```http
GET /api/v1/platform/call-screen/media-seed
PATCH /api/v1/platform/call-screen/media-seed
POST /api/v1/platform/call-screen/media
GET /api/v1/platform/call-screen/media/{assetId}
```

The same platform permission rule applies: the actor must have role `platform_admin` and either `platform.call_screen_ad.manage` or `platform.tenant.manage`.

Media seed slides use `mediaSlides`:

```json
{
  "displayName": "餐厅默认叫号屏图片/视频",
  "status": "active",
  "mediaSlides": [
    {
      "mediaAssetId": "8a000000-0000-0000-0000-000000000001",
      "mediaKind": "video",
      "title": "环境短片",
      "altText": "餐厅环境视频",
      "sortOrder": 1,
      "status": "active"
    }
  ],
  "version": 0
}
```

Platform media assets use owner scope `platform`; tenant media assets remain isolated and are not shared through platform seed maintenance.
