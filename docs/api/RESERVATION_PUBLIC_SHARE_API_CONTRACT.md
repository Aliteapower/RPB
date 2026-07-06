# Reservation Public Share API Contract

## Endpoint

`GET /api/v1/public/reservation-shares/{token}`

Public, unauthenticated, token-scoped read endpoint for customer-facing reservation share pages.

Optional query parameter:

```text
locale=zh-CN|en-SG
```

## Success Response

```json
{
  "success": true,
  "share": {
    "reservationNo": "R-SHARE-0007",
    "storeName": "食刻订位中心",
    "reservationDate": "20-06-2030",
    "reservationTime": "11:30",
    "partySize": 4,
    "tableCode": "A01",
    "tablePending": false,
    "arrivalNote": "请提前 10 分钟到店",
    "storePhone": "6333 1234",
    "storeEmail": "booking@example.test",
    "storeWhatsappPhone": "+6588880000",
    "storeAddress": "1 Example Road",
    "googleMapUrl": "https://maps.app.goo.gl/rpb",
    "shareTitle": "食刻订位中心 订位确认",
    "shareSummary": "20-06-2030 11:30 · 4人",
    "shareText": "门店：食刻订位中心\n编号：R-SHARE-0007\n时间：20-06-2030 11:30"
  }
}
```

`storeEmail` and `storeWhatsappPhone` are optional customer-facing contact fields maintained by tenant admin share profile settings. `storeWhatsappPhone` is returned only as an E.164 phone number; the frontend may turn it into a click-to-chat link without exposing tenant IDs or staff actor data.

`shareText` and `arrivalNote` are rendered by the backend from persisted `i18n_message_catalog` messages when available. Resolution follows store override, tenant override, platform default, then `zh-CN` fallback. Legacy store share template and arrival note fields remain the final fallback when no catalog message exists. If the resolved template contains unsupported variables, the active platform default template is used. The text may include customer-safe template variables such as contact name and masked phone, but must not expose raw customer phone, tenant ID, store ID, reservation ID, or actor data.

## Errors

- `INVALID_TOKEN`: HTTP 400
- `TOKEN_NOT_FOUND`: HTTP 404
- `TOKEN_REVOKED`: HTTP 410
- `TOKEN_EXPIRED`: HTTP 410
- `RESERVATION_NOT_FOUND`: HTTP 404
- `PERSISTENCE_ERROR`: HTTP 500

## Security

The endpoint is public only by unguessable token. It does not use App Gate actor context, does not mutate reservation workflow state, and must remain read-only.
