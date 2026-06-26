# Platform Call Screen Seed API Contract

Status: Phase 1 text-only

## Scope

Platform admins maintain the default text seed used to clone tenant call screen copy. Updating the platform seed does not mutate existing tenant copies.

Phase 1 only supports the text seed key `restaurant_default`.

## Permissions

Allowed:

- Role: `platform_admin`
- Permission: `platform.call_screen_ad.manage`

Temporary compatibility:

- `platform.tenant.manage` is accepted for existing platform admin accounts.

## Endpoints

`GET /api/v1/platform/call-screen/text-seed`

Returns the active platform text seed.

`PATCH /api/v1/platform/call-screen/text-seed`

Updates the platform text seed and replaces its active text slides.

## Request

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

## Response

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

## Error Codes

- `UNAUTHENTICATED`
- `FORBIDDEN`
- `SEED_NOT_FOUND`
- `REQUEST_INVALID`
- `VERSION_CONFLICT`
- `PERSISTENCE_ERROR`

## Phase 2 Not Implemented

Image/video seed templates and asset management require a separate design and are not part of this API contract.
