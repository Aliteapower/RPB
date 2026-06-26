# Call Screen Ad Config API Contract

## Endpoints

```http
GET /api/v1/stores/{storeId}/tenant-admin/call-screen/settings
PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/settings
GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets
POST /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets
GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}
PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}
```

## Permission

These endpoints require:

```text
role = tenant_admin
permission = tenant.admin.manage
```

The tenant scope comes from the authenticated actor. Request bodies must not provide or override `tenantId`. Store scope comes from `{storeId}` and must belong to the actor tenant.

## Settings Shape

```json
{
  "activeAdSetId": "81000000-0000-0000-0000-000000000001",
  "adMode": "text",
  "status": "active",
  "slideDurationSeconds": 5,
  "statePollSeconds": 3,
  "showWaitingPreview": true,
  "version": 0
}
```

`slideDurationSeconds` must be between 3 and 60. `statePollSeconds` must be between 2 and 30.

## Ad Set Shape

```json
{
  "id": "81000000-0000-0000-0000-000000000001",
  "name": "默认叫号屏文案",
  "adType": "text",
  "status": "active",
  "slides": [
    {
      "id": "82000000-0000-0000-0000-000000000001",
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

Tenant ad sets and slides are tenant scoped only. They do not carry `storeId`. Store-specific selection is stored by `store_call_screen_settings`.

`adType` is immutable after creation in Phase 1. `PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}` must not change an existing ad set from `text` to `image` or from `image` to `text`.

## Error Codes

| HTTP | Code | Meaning |
|---:|---|---|
| 403 | `FORBIDDEN` | Actor is missing `tenant_admin` or `tenant.admin.manage`. |
| 403 | `STORE_SCOPE_MISMATCH` | `{storeId}` is outside the actor tenant/store scope. |
| 400 | `REQUEST_INVALID` | Request body, status, sort order, duration, poll interval, or ad mode is invalid. |
| 404 | `AD_SET_NOT_FOUND` | Requested ad set does not exist in the actor tenant or is deleted. |
| 409 | `VERSION_CONFLICT` | Optimistic `version` does not match the current row version. |
| 500 | `PERSISTENCE_ERROR` | Database access failed or persisted configuration is inconsistent. |

## Text Configuration Scope

The original call-screen slice introduced text ad configuration first. `adType = image` remains a legacy reserved value, while new image/video carousel groups use `adType = "media"` and settings use `adMode = "media"`.

Platform seed maintenance is documented separately in `docs/api/PLATFORM_CALL_SCREEN_SEED_API_CONTRACT.md`. Tenant admin APIs always edit tenant-owned copies and never mutate platform seed rows.

## Media Addendum

Tenant-owned media carousel groups support image and video slides. The legacy reserved value `image` is normalized to `media` at the service boundary for compatibility.

Additional endpoint:

```http
POST /api/v1/stores/{storeId}/tenant-admin/call-screen/media
GET /api/v1/stores/{storeId}/tenant-admin/call-screen/media/{assetId}
GET /api/v1/stores/{storeId}/queue-display/media/{assetId}
```

Upload accepts `multipart/form-data` field `file`. Supported content types are `image/jpeg`, `image/png`, `image/webp`, `video/mp4`, and `video/webm`. Tenant media assets are tenant-scoped and cannot be referenced by another tenant.

Media ad-set payloads use:

```json
{
  "name": "默认图片/视频轮播",
  "adType": "media",
  "status": "active",
  "slides": [],
  "mediaSlides": [
    {
      "mediaAssetId": "8a000000-0000-0000-0000-000000000001",
      "mediaKind": "image",
      "title": "新品海报",
      "altText": "新品推荐图",
      "sortOrder": 1,
      "status": "active"
    }
  ],
  "version": 0
}
```

`GET /queue-display/media/{assetId}` validates that the asset belongs to the store tenant and appears in the store's currently active media ad set before serving the file.
