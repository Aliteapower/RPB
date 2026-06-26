# Call Screen Ad Config API Contract

Status: Phase 1 text-only

## Scope

Tenant admins manage store call screen settings and tenant-owned text ad sets. All contracts are scoped by `{storeId}` and the authenticated actor's tenant/store access.

Phase 1 only supports text advertising copy. `adType` and `adMode` must be `text`.

## Endpoints

`GET /api/v1/stores/{storeId}/tenant-admin/call-screen/settings`

Returns the current call screen setting. If missing, the backend creates a default setting bound to the tenant's cloned platform text seed.

`PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/settings`

Updates active text ad set, status, slide timing, polling timing, waiting preview flag, and optimistic version.

`GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets`

Lists tenant-owned text ad sets.

`POST /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets`

Creates a tenant-owned text ad set.

`GET /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}`

Reads one tenant-owned text ad set.

`PATCH /api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}`

Updates one tenant-owned text ad set.

## Settings Payload

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

## Text Ad Set Payload

```json
{
  "name": "默认叫号屏文案",
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
```

## Error Codes

- `UNAUTHENTICATED`
- `FORBIDDEN`
- `STORE_SCOPE_MISMATCH`
- `AD_SET_NOT_FOUND`
- `REQUEST_INVALID`
- `VERSION_CONFLICT`
- `PERSISTENCE_ERROR`

## Phase 2 Not Implemented

Image/video carousel groups and asset management are outside Phase 1. This contract must not expose binary asset controls, non-text slide fields, or non-text ad set writes.
