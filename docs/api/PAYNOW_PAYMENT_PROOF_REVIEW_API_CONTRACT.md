# PayNow Payment Proof Review API Contract

## Purpose

Payment Proof Review lets a payment-enabled store employee upload or capture a PayNow bank receipt screenshot. RPB extracts the RPB-generated Ref and amount, matches them to one active Quick Pay intent in the same tenant/store, and auto-confirms only when both values match.

The PayNow proof template library improves extraction accuracy by letting RPB maintain platform seed templates and letting tenants add store-facing receipt samples. Template matching may improve OCR fields, but it must not bypass the final Ref uniqueness and amount equality checks.

Amount equality is exact numeric equality with no tolerance; decimal scale alone does not make equal monetary values different.

## Ref Definition

`Ref` is `payment_intents.payment_reference`.

Accepted RPB reference patterns:

- `PIT-202608-0021`
- `QP-202608-0040-87D0`
- `AB12-202608-123456-Z9X7`

Ref extraction must ignore bank transaction identifiers such as `Transaction ID`, `Transaction Ref`, `交易编号`, and long non-hyphenated bank ids.

## Endpoints

## Platform Proof Template Library

### Authorization and scope

- Platform endpoints require a `platform_admin` actor with `platform.payment_proof_template.manage`.
- Tenant endpoints require `payment.proof_template.manage`, matching tenant identity, and access to `{storeId}`.
- Tenant list and submit queries are tenant-scoped. A tenant cannot read another tenant's contributions or submit a platform/foreign tenant template as `sourceTemplateId`.
- Platform rule suggestions and contribution reviews do not create payment proofs or verifications and do not mutate payment intent/session state.

Platform admins manage shared PayNow bank receipt OCR templates under:

- `GET /api/v1/platform/payment/proof-templates`
- `POST /api/v1/platform/payment/proof-templates`
- `PATCH /api/v1/platform/payment/proof-templates/{templateId}`
- `POST /api/v1/platform/payment/proof-templates/rule-suggestions`
- `GET /api/v1/platform/payment/proof-template-contributions`
- `POST /api/v1/platform/payment/proof-template-contributions/{contributionId}/accept`
- `POST /api/v1/platform/payment/proof-template-contributions/{contributionId}/reject`

Tenant admins contribute missing templates under:

- `GET /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
- `POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
- `POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-rule-suggestions`

Rule suggestions never mutate payment intent/session/proof verification state.

### Template request and response schemas

Template create/PATCH request:

```json
{
  "bankCode": "ocbc",
  "bankName": "OCBC",
  "locale": "zh-CN",
  "templateName": "OCBC PayNow",
  "status": "active",
  "priority": 20,
  "layoutJson": "{\"matchKeywords\":[\"OCBC\"],\"referencePatterns\":[\"(?:讯息|Message)\\\\s*[:：]?\\\\s*([A-Z0-9.-]{10,32})\"],\"amountPatterns\":[\"您已支付\\\\s*([0-9OoIl,.]+)\\\\s*SGD\"]}",
  "version": 3
}
```

`version` is required for platform PATCH and must equal the current template version; it may be omitted on create. Tenant PATCH clients also send the current version for optimistic locking. A missing platform PATCH version returns `REQUEST_INVALID`; a stale supplied version returns `VERSION_CONFLICT`.

`layoutJson` must decode to a JSON object. When present, `matchKeywords`, `successKeywords`, `referencePatterns`, and `amountPatterns` must be arrays of nonblank strings. Every reference/amount pattern must compile as a Java regular expression. Invalid shape or regex returns `REQUEST_INVALID` before a template can be created, updated, or activated through contribution acceptance.

Template mutation response:

```json
{
  "success": true,
  "template": {
    "id": "30000000-0000-0000-0000-000000000001",
    "tenantId": null,
    "bankCode": "ocbc",
    "bankName": "OCBC",
    "locale": "zh-CN",
    "templateName": "OCBC PayNow",
    "source": "platform_seed",
    "status": "active",
    "priority": 20,
    "version": 4,
    "layoutJson": "{}",
    "createdAt": "2026-08-09T00:00:00Z",
    "updatedAt": "2026-08-09T01:00:00Z"
  }
}
```

List responses use `{ "success": true, "templates": [...] }`.

### Rule suggestions

Both rule-suggestion endpoints consume `multipart/form-data`:

- `image`: required nonempty PNG, JPEG, or WebP.
- `bankCode`: required.
- `bankName`: required.
- `locale`: optional, defaults to `zh-CN`.

Response:

```json
{
  "success": true,
  "bankCode": "ocbc",
  "bankName": "OCBC",
  "locale": "zh-CN",
  "templateName": "OCBC PayNow",
  "suggestedLayoutJson": "{\"matchKeywords\":[\"OCBC\"]}",
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

Sample image bytes are temporary extraction input and are not stored in the database.

### Tenant contributions

`POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions` request:

```json
{
  "sourceTemplateId": "50000000-0000-0000-0000-000000000003",
  "bankCode": "ocbc",
  "bankName": "OCBC",
  "locale": "zh-CN",
  "templateName": "OCBC new receipt",
  "layoutJson": "{\"matchKeywords\":[\"OCBC\"]}",
  "sampleFileName": "receipt.jpg",
  "sampleContentType": "image/jpeg",
  "sampleFileDigest": "sha256-hex",
  "sampleRawText": "OCBC raw OCR text",
  "sampleOcrReference": "QP202608090017GQVQ",
  "sampleOcrAmount": 1.00
}
```

The source template is optional, but when supplied it must be a tenant-owned template visible in the current tenant/store scope. `sampleOcrAmount`, when supplied, must be positive. `sampleContentType`, when supplied, must be PNG, JPEG, or WebP. The request carries metadata/OCR evidence only; it has no image-bytes field.

Contribution create and review mutations return the contribution as a flat body, not under a `contribution` property:

```json
{
  "id": "90000000-0000-0000-0000-000000000001",
  "tenantId": "10000000-0000-0000-0000-000000000003",
  "storeId": "20000000-0000-0000-0000-000000000003",
  "sourceTemplateId": "50000000-0000-0000-0000-000000000003",
  "platformTemplateId": null,
  "bankCode": "ocbc",
  "bankName": "OCBC",
  "locale": "zh-CN",
  "templateName": "OCBC new receipt",
  "layoutJson": "{\"matchKeywords\":[\"OCBC\"]}",
  "sampleFileName": "receipt.jpg",
  "sampleContentType": "image/jpeg",
  "sampleFileDigest": "sha256-hex",
  "sampleRawText": "OCBC raw OCR text",
  "sampleOcrReference": "QP202608090017GQVQ",
  "sampleOcrAmount": 1.00,
  "status": "submitted",
  "reviewNote": null,
  "submittedBy": "30000000-0000-0000-0000-000000000003",
  "reviewedBy": null,
  "createdAt": "2026-08-09T00:00:00Z",
  "updatedAt": "2026-08-09T00:00:00Z",
  "reviewedAt": null,
  "version": 0
}
```

Contribution list responses use `{ "success": true, "contributions": [...] }`. The tenant list includes all outcomes so tenants can observe acceptance/rejection and `reviewNote`. The platform list accepts optional `status`; supported values are `submitted`, `accepted`, `rejected`, and `withdrawn`.

### Contribution review

Accept into a new platform template:

```json
{
  "platformTemplateId": null,
  "targetTemplateVersion": null,
  "reviewNote": "Accepted as a new OCBC rule",
  "version": 0
}
```

Accept into an existing platform template:

```json
{
  "platformTemplateId": "30000000-0000-0000-0000-000000000001",
  "targetTemplateVersion": 3,
  "reviewNote": "Apply the tenant evidence",
  "version": 0
}
```

Reject:

```json
{
  "platformTemplateId": null,
  "targetTemplateVersion": null,
  "reviewNote": "Reference pattern captures the bank transaction id",
  "version": 0
}
```

`version` is always required and is the current contribution version. `targetTemplateVersion` is required when `platformTemplateId` selects an existing template and must equal that template's current version. A reject requires a nonblank `reviewNote`. Missing required versions/notes return `REQUEST_INVALID`; stale contribution or target-template versions return `VERSION_CONFLICT`.

Allowed review transitions:

| Current status | Command | Result |
|---|---|---|
| `submitted` | accept | `accepted`; creates or updates one platform template and records `platformTemplateId` |
| `submitted` | reject with nonblank note | `rejected` |
| `accepted`, `rejected`, or `withdrawn` | accept/reject | `REQUEST_INVALID`; terminal outcomes are not reviewed again |

### Template/contribution errors

| HTTP | Code | Applies when |
|---:|---|---|
| 400 | `REQUEST_INVALID` | Missing/invalid request, malformed layout shape/regex, invalid image metadata, blank reject note, missing required version, invalid status transition, or foreign/invalid source or target template. |
| 401 | `UNAUTHENTICATED` | No current actor. |
| 403 | `FORBIDDEN` | Platform role/permission is absent, or tenant/store scope/permission is absent. |
| 409 | `VERSION_CONFLICT` | Contribution, platform template, or tenant template optimistic version is stale. |
| 500 | `PERSISTENCE_ERROR` | Database operation fails. |

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
