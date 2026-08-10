# PayNow Quick Payment Records API Contract

## Endpoint

`GET /api/v1/stores/{storeId}/payments/intents/quick-pay-records`

Permission: App Gate `payment.intent.view`.

## Purpose

Returns store-scoped Quick Pay records and a summary for operational reporting. Staff use the same endpoint for the daily payment report, while tenant admins use it for filtered record review.

## Query

- `businessDate`: optional ISO date.
- `status`: optional, one of `pending`, `awaiting_verification`, `paid`, `expired`, `cancelled`, `failed`.
- `terminalCode`: optional exact terminal code after trimming.
- `cashierName`: optional exact cashier name after trimming.
- `q`: optional keyword search.
- `limit`: optional integer, default `80`, min `1`, max `200`.

## Response

```json
{
  "success": true,
  "records": [
    {
      "intentId": "50000000-0000-0000-0000-000000000001",
      "sessionId": "60000000-0000-0000-0000-000000000001",
      "intentNo": "PIT-202608-0001",
      "sessionNo": "PRS-ABCDEF1234567890",
      "displayNumber": 7,
      "businessDate": "2026-08-10",
      "amount": "18.80",
      "currency": "SGD",
      "paymentReference": "QP202608100007ACDE",
      "intentStatus": "paid",
      "sessionStatus": "paid",
      "terminalCode": "T1",
      "cashierName": "Alice",
      "createdAt": "2026-08-10T04:10:00Z",
      "expiresAt": "2026-08-10T04:12:00Z"
    }
  ],
  "summary": {
    "count": 3,
    "pendingCount": 1,
    "awaitingVerificationCount": 1,
    "paidCount": 1,
    "totalAmount": "31.8",
    "pendingAmount": "5",
    "awaitingVerificationAmount": "8",
    "paidAmount": "18.8",
    "currency": "SGD"
  }
}
```

## Summary Semantics

- `paidAmount` is the real collected amount.
- `pendingAmount` is money still waiting for customer payment.
- `awaitingVerificationAmount` is money with receipt evidence or matching work still waiting for verification confirmation.
- `totalAmount` includes all returned rows, including expired, cancelled, and failed rows when the caller filters for them.
- Counts and amounts are calculated from the returned filtered rows, bounded by `limit`.

## Errors

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `REQUEST_INVALID` | Invalid status or malformed request parameter. |
| 401 | `UNAUTHENTICATED` | No current actor. |
| 403 | `FORBIDDEN` | Actor lacks store access or payment view permission. |
| 500 | `PERSISTENCE_ERROR` | Database operation failed. |

## Compatibility

The `cashierName`, `awaitingVerificationCount`, `pendingAmount`, and `awaitingVerificationAmount` fields are additive. Existing consumers that only read the previous fields remain compatible.
