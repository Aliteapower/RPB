# Queue Display State API Contract

Status: Phase 1 text-only

## Endpoint

`GET /api/v1/stores/{storeId}/queue-display/state`

## Authorization

Required App Gate:

- `appKey = reservation_queue`
- `permission = queue.display.view`

Allowed roles:

- `tenant_admin`
- `store_manager`
- `store_staff`

The authenticated actor must include `{storeId}` in store scope.

## Response

```json
{
  "success": true,
  "serverNow": "2030-06-20T02:30:00Z",
  "storeTime": {
    "timezone": "Asia/Singapore",
    "timeText": "10:30",
    "businessDate": "2030-06-20"
  },
  "currentCall": {
    "queueTicketId": "91000000-0000-0000-0000-000000000001",
    "displayNumber": "A7",
    "customerDisplayName": "赵先生",
    "partySize": 2,
    "calledAt": "2030-06-20T02:29:00Z",
    "expiresAt": "2030-06-20T02:33:00Z"
  },
  "waiting": {
    "count": 3,
    "preview": [
      {
        "displayNumber": "A8",
        "customerDisplayName": "钱女士",
        "partySize": 2
      }
    ]
  },
  "ads": {
    "mode": "text",
    "slideDurationSeconds": 5,
    "statePollSeconds": 3,
    "slides": [
      {
        "slideId": "83000000-0000-0000-0000-000000000001",
        "title": "欢迎光临",
        "subtitle": "食刻 · 餐厅",
        "tagline": "新鲜食材 · 匠心烹饪 · 极致服务"
      }
    ]
  }
}
```

`currentCall` is null when no valid called ticket exists. In that state the terminal rotates the text slides from `ads.slides`.

## Error Codes

- `FORBIDDEN`
- `STORE_SCOPE_MISMATCH`
- `STORE_NOT_FOUND`
- `PERSISTENCE_ERROR`

## Phase 2 Not Implemented

Image/video carousel groups and asset playback are outside Phase 1. The state response carries text slides only.
