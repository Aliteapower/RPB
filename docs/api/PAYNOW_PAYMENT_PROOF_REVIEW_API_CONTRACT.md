# PayNow Payment Proof Review API Contract

## Purpose

Payment Proof Review lets a payment-enabled store employee upload or capture a PayNow bank receipt screenshot. RPB extracts the RPB-generated Ref and amount, matches them to one active Quick Pay intent in the same tenant/store, and auto-confirms only when both values match.

The PayNow proof template library improves extraction accuracy by letting RPB maintain platform seed templates and letting tenants add store-facing receipt samples. Template matching may improve OCR fields, but it must not bypass the final Ref uniqueness and amount equality checks.

## Ref Definition

`Ref` is `payment_intents.payment_reference`.

Accepted RPB reference patterns:

- `PIT-202608-0021`
- `QP-202608-0040-87D0`
- `AB12-202608-123456-Z9X7`

Ref extraction must ignore bank transaction identifiers such as `Transaction ID`, `Transaction Ref`, `交易编号`, and long non-hyphenated bank ids.

## Endpoints

### GET /api/v1/stores/{storeId}/tenant-admin/payment/proof-templates

Permission: `payment.proof_template.manage`

Returns active/inactive platform seed templates and tenant custom templates visible to the store's tenant.

### POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-templates

Permission: `payment.proof_template.manage`

Creates a tenant custom template. Platform seed templates are read-only for tenants.

Request:

```json
{
  "bankCode": "ocbc",
  "bankName": "OCBC",
  "locale": "zh-CN",
  "templateName": "OCBC tenant PayNow",
  "status": "active",
  "priority": 20,
  "layoutJson": "{\"matchKeywords\":[\"OCBC\"],\"referencePatterns\":[\"(?:讯息|Message)\\\\s*[:：]?\\\\s*([A-Z0-9.-]{10,32})\"],\"amountPatterns\":[\"您已支付\\\\s*([0-9OoIl,.]+)\\\\s*SGD\"]}",
  "version": 0
}
```

### PATCH /api/v1/stores/{storeId}/tenant-admin/payment/proof-templates/{templateId}

Permission: `payment.proof_template.manage`

Updates a tenant-owned proof template. Updating a platform seed through the tenant endpoint returns a conflict or invalid request.

### POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-templates/test-scan

Permission: `payment.proof_template.manage`

Request: `multipart/form-data`

- `image`: required file, one of `.png`, `.jpg`, `.jpeg`, `.webp`.

Response:

```json
{
  "success": true,
  "template": {
    "id": "30000000-0000-0000-0000-000000000001",
    "tenantId": null,
    "bankCode": "ocbc",
    "bankName": "OCBC",
    "locale": "zh-CN",
    "templateName": "OCBC Chinese PayNow",
    "source": "platform_seed",
    "status": "active",
    "priority": 10,
    "version": 0,
    "layoutJson": "{}",
    "createdAt": "2026-08-09T00:00:00Z",
    "updatedAt": "2026-08-09T00:00:00Z"
  },
  "ocr": {
    "extractedReference": "QP202608090017GQVQ",
    "extractedAmount": 1.00,
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": 0.7100,
    "rawText": "OCBC\n您已支付 1.00 SGD\n讯息\nQP202608090017GQVQ"
  }
}
```

`test-scan` records a template sample for tuning only. It must not create payment proofs, verifications, or update payment intent/session state.

### GET /api/v1/stores/{storeId}/payments/proof-review/candidates

Permission: `payment.proof.review`

Query:

- `businessDate`: optional ISO date. Defaults to current open payment business day.
- `terminalCode`: optional exact terminal code.
- `limit`: optional integer, default `80`, min `1`, max `200`.

Response:

```json
{
  "success": true,
  "businessDate": "2026-08-08",
  "candidates": [
    {
      "intentId": "50000000-0000-0000-0000-000000000001",
      "sessionId": "60000000-0000-0000-0000-000000000001",
      "intentNo": "PIT-202608-0021",
      "sessionNo": "PRS-ABCDEF1234567890",
      "displayNumber": 21,
      "paymentReference": "PIT-202608-0021",
      "amount": "0.10",
      "currency": "SGD",
      "intentStatus": "pending",
      "sessionStatus": "pending",
      "terminalCode": "T1",
      "cashierName": "Alice",
      "createdAt": "2026-08-08T04:10:00Z",
      "expiresAt": "2026-08-08T04:12:00Z"
    }
  ]
}
```

### POST /api/v1/stores/{storeId}/payments/proof-review/scan

Permission: `payment.proof.review`

Request: `multipart/form-data`

- `image`: required file, one of `.png`, `.jpg`, `.jpeg`, `.webp`.
- `idempotencyKey`: required string.
- `terminalCode`: optional string.
- `businessDate`: optional ISO date.

Auto-confirm response:

```json
{
  "success": true,
  "outcome": "auto_confirmed",
  "replayed": false,
  "intentId": "50000000-0000-0000-0000-000000000001",
  "sessionId": "60000000-0000-0000-0000-000000000001",
  "proofId": "70000000-0000-0000-0000-000000000001",
  "verificationId": "80000000-0000-0000-0000-000000000001",
  "paymentReference": "PIT-202608-0021",
  "expectedAmount": "0.10",
  "ocr": {
    "extractedReference": "PIT-202608-0021",
    "extractedAmount": "0.10",
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": "0.9600"
  },
  "checks": {
    "reference": "match",
    "amount": "match"
  }
}
```

Review response:

```json
{
  "success": true,
  "outcome": "needs_review",
  "replayed": false,
  "intentId": "50000000-0000-0000-0000-000000000001",
  "sessionId": "60000000-0000-0000-0000-000000000001",
  "proofId": "70000000-0000-0000-0000-000000000001",
  "verificationId": "80000000-0000-0000-0000-000000000001",
  "paymentReference": "PIT-202608-0021",
  "expectedAmount": "0.10",
  "ocr": {
    "extractedReference": "PIT-202608-0021",
    "extractedAmount": null,
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": "0.7100"
  },
  "checks": {
    "reference": "match",
    "amount": "missing"
  }
}
```

No-match response:

```json
{
  "success": true,
  "outcome": "no_match",
  "replayed": false,
  "ocr": {
    "extractedReference": null,
    "extractedAmount": "0.10",
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": "0.5200"
  },
  "checks": {
    "reference": "missing",
    "amount": "not_checked"
  }
}
```

## Error Codes

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `REQUEST_INVALID` | Missing idempotency key, invalid file type, invalid business date, or malformed request. |
| 400 | `PAYMENT_OCR_UNAVAILABLE` | OCR adapter is not configured or failed before text extraction. |
| 401 | `UNAUTHENTICATED` | No current actor. |
| 403 | `FORBIDDEN` | Actor lacks store access or App Gate permission. |
| 409 | `PAYMENT_INTENT_STATE_CONFLICT` | Matched intent is paid, cancelled, failed, or no longer confirmable. |
| 409 | `IDEMPOTENCY_CONFLICT` | Same idempotency key was used with a different file digest or scope. |
| 500 | `PERSISTENCE_ERROR` | Database operation failed. |

## Idempotency

`idempotencyKey` is scoped by tenant. A replay with the same scope and file digest returns the original result with `replayed = true`. A replay with a different file digest returns `IDEMPOTENCY_CONFLICT`.

Template library create/update calls are ordinary admin mutations guarded by optimistic `version` on update. `test-scan` is diagnostic and non-confirming; repeated uploads may create additional template sample audit rows.
