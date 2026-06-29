# Reservation Staff Share Info API Contract

## Endpoints

`GET /api/v1/stores/{storeId}/reservations/{reservationId}/share-info`

Returns backend-rendered reservation share copy, a public H5 token path, and staff click-to-chat channel links.

`POST /api/v1/stores/{storeId}/reservations/{reservationId}/share-info/intent`

Records a staff sharing intent audit event. It does not send a message and does not mutate reservation workflow state.

## Permission

Both endpoints are store-scoped and require the existing App Gate entry:

```text
app_key = reservation_queue
permission = reservation.today_view
```

The controller also requires a current actor that can access `{storeId}` and has one of the existing staff/admin roles accepted by the reservation today view surface.

## GET Success Response

```json
{
  "success": true,
  "shareInfo": {
    "reservationId": "50000000-0000-0000-0000-000000001201",
    "reservationNo": "R-20300620-0007",
    "channel": "manual_copy",
    "shareText": "门店：食刻订位中心\n编号：R-20300620-0007\n时间：20-06-2030 11:30",
    "customerMaskedPhone": "****4567",
    "customerPhoneAvailable": true,
    "senderLabel": "+6588880000",
    "canOpenWhatsAppLink": true,
    "whatsappLink": "https://wa.me/6591234567?text=...",
    "canOpenWechatLink": true,
    "wechatLink": "weixin://",
    "wechatShareText": "门店：食刻订位中心\n编号：R-20300620-0007\n时间：20-06-2030 11:30\n\nhttps://staff.example/reservation-share/{token}",
    "shareToken": "{token}",
    "sharePath": "/reservation-share/{token}",
    "shareTitle": "食刻订位中心 订位确认",
    "shareSummary": "20-06-2030 11:30 · 4人"
  }
}
```

## Link Rules

- `shareText` is rendered by the backend from the store reservation share template. If the store template is blank or contains unsupported variables, the platform default template is used.
- `shareToken` and `sharePath` point to the customer-facing public H5 reservation share page.
- `whatsappLink` is generated only when the customer phone is valid E.164. The link format is `https://wa.me/{customerPhoneDigits}?text={encodedShareMessage}`.
- `whatsappLink` necessarily contains the recipient phone digits because it is a click-to-chat URL. Frontend must use it only as the action target and must not render or log it as customer phone display text.
- `senderLabel` is informational and comes from `stores.whatsapp_business_phone_e164` when configured, otherwise the store display label. It is not an API credential and does not control which WhatsApp account actually sends.
- `wechatLink` opens the local WeChat app where supported. WeChat does not provide the same phone-addressed prefilled chat URL as `wa.me`; staff copy/use `wechatShareText` and manually choose the customer conversation.

## Intent Request

```json
{
  "channel": "whatsapp"
}
```

Allowed `channel` values:

```text
whatsapp
wechat
system_share
copy_link
```

## Intent Success Response

```json
{
  "success": true,
  "channel": "whatsapp"
}
```

## Intent Audit

The endpoint appends `audit_logs.operation_code = reservation.share_intent` with target type `reservation`.

Audit metadata may include:

```json
{
  "channel": "whatsapp",
  "customerPhoneAvailable": true,
  "customerMaskedPhone": "****4567",
  "senderLabel": "+6588880000",
  "shareToken": "{token}",
  "sharePath": "/reservation-share/{token}",
  "canOpenWhatsAppLink": true,
  "canOpenWechatLink": true
}
```

Audit metadata must not store message body, raw customer phone, or full rendered share text.

## Tenant Admin Field

Tenant admin share profile includes `whatsappBusinessPhoneE164`, stored as `stores.whatsapp_business_phone_e164`.

Validation:

```text
null or ^[+][1-9][0-9]{1,14}$
```

The field is used for display/sender labeling only. It is not a Meta credential, webhook configuration, or sending authority.

## Errors

The GET endpoint can return existing reservation share info errors such as:

- `INVALID_COMMAND`: HTTP 400
- `STORE_NOT_FOUND`: HTTP 404
- `STORE_SCOPE_MISMATCH`: HTTP 403
- `STORE_ACCESS_DENIED`: HTTP 403
- `RESERVATION_NOT_FOUND`: HTTP 404
- `PERSISTENCE_ERROR`: HTTP 500

The intent endpoint reuses the same error mapping and returns `INVALID_COMMAND` when `channel` is missing or unsupported.

## Idempotency

The intent endpoint is intentionally not idempotent. Each staff click is a distinct intent signal and may create a separate audit row.

## Compatibility

The GET response is additive to the existing staff share-info shape. Existing consumers that only read `shareText`, `shareToken`, `sharePath`, `shareTitle`, and `shareSummary` can continue to do so.
