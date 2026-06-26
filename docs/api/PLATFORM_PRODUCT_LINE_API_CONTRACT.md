# Platform Product Line API Contract

Status: Phase 1 implemented

## Scope

This API exposes the platform product line catalog. In Phase 1 the product line is a one-to-one view of App Gate `platform_apps.app_key`.

The first product line is:

- `appKey`: `reservation_queue`
- display name: `预约排队叫号产线`

## Permissions

All endpoints require a platform admin actor and one of:

- `platform.product_line.manage`
- `platform.tenant.manage` as Phase 1 compatibility

## Endpoints

### `GET /api/v1/platform/product-lines`

Returns all product lines sorted by `sortOrder`, then `appKey`.

Response:

```json
{
  "success": true,
  "productLines": [
    {
      "appKey": "reservation_queue",
      "displayName": "预约排队叫号产线",
      "status": "active",
      "defaultEntryRoute": "/stores/:storeId/staff",
      "description": "预约、排队、叫号一体化产线",
      "sortOrder": 10,
      "createdAt": "2026-06-26T00:00:00Z",
      "updatedAt": "2026-06-26T00:00:00Z"
    }
  ]
}
```

### `PATCH /api/v1/platform/product-lines/{appKey}`

Updates display metadata only. It does not change the App Gate `app_key`.

Request:

```json
{
  "displayName": "预约排队叫号产线",
  "status": "active",
  "description": "预约、排队、叫号一体化产线",
  "sortOrder": 10
}
```

Response:

```json
{
  "success": true,
  "productLine": {
    "appKey": "reservation_queue",
    "displayName": "预约排队叫号产线",
    "status": "active"
  }
}
```

## Error Codes

- `UNAUTHENTICATED`
- `FORBIDDEN`
- `REQUEST_INVALID`
- `PRODUCT_LINE_NOT_FOUND`
- `PERSISTENCE_ERROR`
