# PayNow Payment Product Line Design

## Status

Design specification only. This document does not create executable schema, Java code, Vue code, runtime configuration, dependency changes, production data changes, seed data, or migrations.

Approved direction:

```text
Create an RPB-native payment product line. Use D:\payment_runtime as business-flow evidence for PayNow, quick payment, payment sessions, proof review, and verification. Do not run D:\payment_runtime as a sidecar service and do not call it from RPB at runtime.
```

## Source Context

### payment_runtime Evidence

`D:\payment_runtime` is a standalone Flask and Vue runtime. It is useful as a business reference, not as code to embed.

Relevant backend evidence:

| Area | Evidence | Product Meaning |
|---|---|---|
| PayNow SGQR generation | `backend/app/services/paynow_qr_service.py` | Build PayNow QR payload from proxy type, mobile or UEN, merchant name, amount, and reference. |
| Quick payment request | `backend/app/services/payment_request_service.py` | Create short-lived PayNow payment requests with request number, payment reference, QR payload, business date, display number, terminal, and cashier snapshot. |
| Payment intent runtime | `backend/app/payment_runtime/service.py` | Intent/session model with source type, amount, reference, session number, QR payloads, expiration, proof scan, and confirmation. |
| Verification adapter | `backend/app/payment_runtime/adapters.py` | Confirming a verification marks the intent and sessions paid, with a source-type adapter extension point. |
| Proof and OCR | `backend/app/models/payment_proof.py`, `payment_ocr_result_core.py`, `payment_verification.py` | Store uploaded payment proof metadata, OCR result, and manual verification state. |
| Terminal API | `backend/app/api/terminal/routes.py` | Terminal quick payment, active display sessions, public display polling/SSE, proof scan, confirm, reject. |
| Runtime audit API | `backend/app/api/payment_runtime/routes.py` | Payment audit list across intents and proof reviews. |

Important business concepts:

- PayNow may be configured by mobile number or UEN.
- PayNow merchant name is required.
- Payment references must be generated and unique.
- QR sessions are short lived.
- A terminal display number is allocated by tenant/business date and must avoid duplicates.
- QR payloads may include PayNow SGQR and a reserved internal QR payload.
- Proof upload and OCR are separate from final human confirmation.
- Confirmation should be source-aware so a POS order, reservation deposit, or generic payment can be updated by the appropriate adapter.

Direct reuse is not recommended because:

- It uses Python Flask and SQLAlchemy, while RPB backend is Java Spring Boot.
- Its model uses integer identifiers and SQLite-oriented schema repair patterns, while RPB uses PostgreSQL, Flyway, UUIDs, tenant and store scoped constraints.
- It has separate auth, permissions, feature flags, tenant resolution, and terminal roles.
- Sidecar operation would create two user systems, two permission systems, two databases, and two tenant boundaries.
- Future POS and reservation reuse would become cross-runtime orchestration instead of clean module reuse.

### RPB Evidence

RPB already has:

- Product-line catalog through `platform_apps`.
- Platform product-line pricing through `platform_product_line_prices`.
- Tenant and store billing through `tenant_product_subscriptions` and `tenant_product_subscription_items`.
- Runtime product access through App Gate:
  - `platform_apps`
  - `tenant_app_entitlements`
  - `store_app_settings`
  - endpoint permission checks
- Existing product-line management API under `/api/v1/platform/product-lines`.
- Existing platform billing API under `/api/v1/platform/tenants/{tenantId}/product-subscriptions`.
- Store, tenant, staff, reservation, queue, walk-in, seating, cleaning, and platform billing modules.
- A POS product-line design that explicitly deferred PayNow QR automation and gateway settlement from POS Phase 1.

Current gaps:

- RPB has no native restaurant customer payment module.
- App Gate visible app entry permissions are currently hardcoded for `reservation_queue`.
- POS, reservation deposit, and future store self-payment need a shared payment capability instead of separate PayNow implementations.

## Goals

1. Add a native RPB product line with `app_key = payment`.
2. Implement PayNow quick payment as the first payment capability.
3. Use `D:\payment_runtime` as business-flow evidence, not a runtime dependency.
4. Provide one shared payment module that can be used by POS, reservation deposits, queue or table orders, platform billing self-payment, and generic merchant collection.
5. Keep restaurant customer payments separate from platform subscription billing.
6. Use App Gate for runtime product-line entitlement, store enablement, and endpoint permissions.
7. Keep tenant and store scope explicit in every payment query and mutation.
8. Make payment commands idempotent because terminals, tablets, and cashier devices can retry after network interruption.
9. Reserve clean extension points for OCR, bank reconciliation, provider adapters, refunds, and non-PayNow channels without implementing all of them in Phase 1.

## Non-Goals

- No sidecar service call to `D:\payment_runtime`.
- No Python, Flask, SQLAlchemy, SQLite, or Vue runtime migration from `D:\payment_runtime`.
- No payment gateway settlement, card gateway, Stripe, webhook, or automatic bank transfer confirmation in Phase 1.
- No storage of raw card numbers, bank credentials, PayNow account secrets, or online banking credentials.
- No platform subscription billing rewrite.
- No automatic invoice, tax filing, GST submission, or accounting export in Phase 1.
- No broad POS implementation in this document. POS may consume payment later through explicit adapters.
- No OCR implementation in Phase 1 unless a later implementation plan expands scope.
- No store self-service platform bill checkout in Phase 1.
- No cross-tenant payment search for tenant staff.

## Product Line Boundary

The payment product line is one App Gate app:

| Field | Value |
|---|---|
| `app_key` | `payment` |
| Platform display name | `PayNow 支付产线` |
| Default entry route | `/stores/:storeId/payments` |
| Product-line billing | Existing `platformbilling` subscription and store item model |
| Runtime authorization | App Gate plus payment endpoint permissions |

The `payment` product line owns merchant collection capability. It does not own POS order lifecycle, reservation lifecycle, queue lifecycle, or platform subscription billing.

Relationship to other product lines:

| Consumer | Relationship |
|---|---|
| POS | POS owns order and receipt. Payment owns PayNow intent, QR session, proof, verification, and paid event. |
| Reservation Queue | Reservation owns booking and deposit requirement. Payment owns deposit collection. |
| Platform Billing | Platform billing owns subscriptions and commercial access. Payment may later collect self-service platform bills, but does not decide entitlement state directly. |
| Quick Pay | Payment owns the full quick payment workflow because it has no upstream business order. |

Runtime access rule:

```text
A store must have the payment app enabled before staff can create PayNow payment intents or sessions. A consuming product line may also require its own app gate.
```

Example:

```text
POS PayNow collection requires both:
1. app_key = pos for POS order actions.
2. app_key = payment for PayNow collection actions.
```

## Recommended Approach

Use this approach:

```text
RPB-native payment module, PayNow-first provider, source adapters for product-line consumers, no sidecar bridge.
```

Alternatives considered:

| Option | Description | Trade-Off | Decision |
|---|---|---|---|
| Sidecar bridge | Run `D:\payment_runtime` next to RPB and call it over HTTP. | Fast demo, but creates duplicate auth, tenants, permissions, database, and operational ownership. | Reject. |
| Copy Flask schema | Translate payment_runtime tables directly into RPB. | Preserves names but imports runtime-specific assumptions and weak tenant/store constraints. | Reject. |
| PayNow helper only | Add only a QR helper inside POS or reservation modules. | Too narrow; each product line would duplicate session, proof, verification, and audit rules. | Reject. |
| Native payment product line | Build shared RPB payment module and expose PayNow through stable APIs and adapters. | More design and implementation work, but clean SaaS product-line boundary. | Recommend. |

## Module And OOD Design

Backend package:

```text
com.rpb.reservation.payment
  api
  application
  domain
  persistence
  provider
```

Frontend structure:

```text
src/api/paymentApi.ts
src/types/payment.ts
src/pages/PaymentTerminalPage.vue
src/pages/TenantAdminPaymentSettingsPage.vue
src/pages/TenantAdminPaymentAuditPage.vue
src/components/payment-terminal/*
src/components/tenant-admin-payment/*
```

### Domain Objects

| Object | Responsibility | Must Not Do |
|---|---|---|
| `PaymentMethodProfile` | Store or tenant scoped payment method configuration, initially PayNow mobile/UEN and merchant name. | Decide product-line subscription state. |
| `PaymentIntent` | Aggregate root for one amount to collect from a customer or payer. | Mutate POS, reservation, or platform billing tables directly. |
| `PaymentSession` | Short-lived QR display and scan session for one intent. | Own the business source order or booking. |
| `PaymentQrPayload` | Provider-neutral QR payload envelope with PayNow SGQR and optional internal payload slots. | Store provider secrets. |
| `PaymentProof` | Uploaded payment proof metadata and storage pointer. | Confirm payment by itself. |
| `PaymentOcrResult` | Parsed proof fields such as reference, amount, timestamp, bank code, and confidence. | Act as final financial truth. |
| `PaymentVerification` | Review state for proof matching and confirmation/rejection. | Bypass source adapter rules. |
| `PaymentDisplayCounter` | Tenant/store/business-date display number allocation. | Generate global ids or order numbers. |
| `PaymentAuditEvent` | Append-only payment lifecycle event. | Replace App Gate denial audit. |
| `PaymentSourceRef` | Source type and source id value object for consumers such as POS order or reservation deposit. | Load arbitrary cross-module entities without an adapter. |

### Application Services

| Service | Responsibility |
|---|---|
| `PaymentMethodProfileService` | Tenant admin setup and validation for PayNow profile fields and display settings. |
| `PaymentIntentService` | Create, query, expire, cancel, and replay payment intents. |
| `PaymentSessionService` | Create QR sessions, allocate display numbers, return QR images or payloads, and expire sessions. |
| `PaymentProofService` | Accept proof uploads, store metadata, and create proof records. |
| `PaymentVerificationService` | Match proof to intent, confirm, reject, and invoke source adapter after confirmation. |
| `PaymentAuditService` | Append lifecycle and review events. |
| `PaymentPermissionPolicy` | Centralize endpoint and command permission mapping. |
| `PaymentProviderRegistry` | Resolve payment provider implementation by method such as `paynow`. |
| `PaymentSourceAdapterRegistry` | Resolve source adapter by source type such as `pos_order` or `reservation_deposit`. |

### Provider Interfaces

Provider interface:

```text
PaymentProvider
  method(): PaymentMethod
  createSessionPayload(PaymentProviderRequest): PaymentProviderPayload
  validateProfile(PaymentMethodProfile): PaymentProfileValidation
```

Initial provider:

```text
PayNowPaymentProvider
```

PayNow provider rules:

1. `paynowType` must be `mobile` or `uen`.
2. Mobile profile requires `paynowMobile`.
3. UEN profile requires `paynowUen`.
4. `merchantName` is required.
5. Amount must be greater than zero.
6. Currency is `SGD` in Phase 1.
7. The generated payment reference is immutable after intent creation.
8. QR payload must include amount and reference, matching payment_runtime behavior.

### Source Adapter Interfaces

Source adapter interface:

```text
PaymentSourceAdapter
  sourceType(): PaymentSourceType
  validateCreatable(PaymentIntentDraft, CurrentActor): PaymentSourceValidation
  paymentConfirmed(PaymentConfirmedEvent): PaymentSourceResult
  paymentRejected(PaymentRejectedEvent): PaymentSourceResult
```

Initial adapters:

| Adapter | Source Type | Phase |
|---|---|---|
| `QuickPaySourceAdapter` | `quick_pay` | Phase 1 |
| `GenericMerchantSourceAdapter` | `generic_merchant` | Phase 1 |
| `PosOrderPaymentSourceAdapter` | `pos_order` | Phase 2 after POS payment integration |
| `ReservationDepositSourceAdapter` | `reservation_deposit` | Phase 2 |
| `PlatformBillingPaymentSourceAdapter` | `platform_billing` | Phase 3 or later |

Adapters are the only place where payment confirmation may update a consuming module.

### Dependency Rules

1. `payment` may read tenant, store, and actor context through explicit ports.
2. `payment` may not update POS, Reservation, Queue, or Platform Billing tables except through source adapters owned by those integration slices.
3. POS, Reservation, Queue, and Platform Billing may request payment intents through application APIs or narrow application ports.
4. `platformbilling` must not depend on payment for product-line entitlement decisions in Phase 1.
5. `appgate` must not depend on payment domain services.
6. Controllers call payment application services, not repositories.
7. Persistence entities do not leak into API responses.
8. Payment provider implementations do not query business source tables.
9. Payment confirmation is transactional around payment state and source adapter result where a same-database adapter is used.
10. External provider integrations added later must use outbox or retry-safe events, not synchronous best-effort mutation.

## App Gate Design

### Product-Line Seed

Implementation should seed a product line equivalent to:

```sql
insert into platform_apps (
    app_key,
    app_name,
    status,
    default_entry_route,
    description,
    sort_order,
    config_json
) values (
    'payment',
    'PayNow 支付产线',
    'active',
    '/stores/:storeId/payments',
    'PayNow QR collection, quick payment terminal, payment proof review, and reusable payment sessions.',
    30,
    '{"entryPermissions":["payment.intent.view","payment.intent.create","payment.verification.review"]}'::jsonb
);
```

The design intentionally records seed intent only. No migration is created here.

### Entry Permission Gap

`AppGateService.visibleApps()` currently returns entry permissions only for `reservation_queue`. Payment requires one of these implementation decisions:

| Option | Description | Recommendation |
|---|---|---|
| Explicit payment constants | Add payment permission constants beside existing reservation queue entry permissions. | Acceptable for Phase 1. |
| Config-driven entry permissions | Read `platform_apps.config_json.entryPermissions` for every active app. | Preferred long-term. |

Recommended implementation:

```text
Move visible app entry permission resolution toward config_json.entryPermissions. If the first implementation keeps explicit constants, keep platform_apps.config_json aligned so a later generic registry can replace the hardcoded switch.
```

### Runtime Enforcement

All store-scoped payment APIs require:

```text
@RequireAppGate(appKey = "payment", permission = "<payment permission>")
```

For source-specific calls, the consuming controller may require both app gates.

Example POS PayNow flow:

```text
1. POS controller validates @RequireAppGate(appKey = "pos", permission = "pos.payment.take").
2. Payment application port validates payment App Gate or is called through a payment controller that validates @RequireAppGate(appKey = "payment", permission = "payment.intent.create").
3. Source adapter validates the POS order belongs to the same tenant/store and is payable.
```

## Permissions

### Platform Permissions

| Permission | Purpose |
|---|---|
| `platform.product_line.manage` | Create and update the `payment` product-line catalog entry. |
| `platform.billing.manage` | Open, renew, suspend, or cancel store billing for `payment`. |

### Tenant Admin Permissions

| Permission | Purpose |
|---|---|
| `payment.settings.manage` | Configure PayNow mobile/UEN, merchant name, profile status, and terminal display defaults. |
| `payment.audit.view` | View payment intents, sessions, proof review history, and lifecycle events. |
| `payment.verification.review` | Confirm or reject submitted payment proofs. |
| `payment.report.view` | View payment summaries and settlement-oriented reports. |

### Staff Permissions

| Permission | Purpose |
|---|---|
| `payment.intent.view` | View active and recent payment intents for accessible stores. |
| `payment.intent.create` | Create quick payment or source-backed PayNow intents. |
| `payment.session.create` | Create a QR display session for an intent. |
| `payment.proof.submit` | Upload or scan payment proof for a session. |
| `payment.verification.confirm` | Confirm matched payment proof from terminal flow. |
| `payment.verification.reject` | Reject proof from terminal flow. |
| `payment.intent.cancel` | Cancel a pending intent. |

Entry visibility should require at least one of:

```text
payment.intent.view
payment.intent.create
payment.verification.review
```

## Data Model

All tables are PostgreSQL tables in RPB. UUID primary keys are preferred. Monetary values use `numeric(12,2)`, not floating point.

### `payment_method_profiles`

Purpose: store or tenant scoped payment method settings.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Nullable. Null means tenant default profile. |
| `method` | text | `paynow` in Phase 1. |
| `status` | text | `active`, `disabled`. |
| `paynow_type` | text | `mobile` or `uen`. |
| `paynow_mobile` | text | Nullable, required for mobile profile. |
| `paynow_uen` | text | Nullable, required for UEN profile. |
| `merchant_name` | text | Required for active PayNow profile. |
| `currency` | text | `SGD` in Phase 1. |
| `config_json` | jsonb | Default `{}`; reserved for display and provider options. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Constraints and indexes:

- Unique `(tenant_id, store_id, method)` for store profile rows.
- Unique `(tenant_id, method)` for tenant default rows where `store_id is null`.
- FK `(store_id, tenant_id)` to `stores(id, tenant_id)` when `store_id` is present.
- Check `method in ('paynow')`.
- Check `status in ('active','disabled')`.
- Check `paynow_type in ('mobile','uen')` when method is `paynow`.
- Check `currency = upper(currency) and length(currency) = 3`.
- Index `(tenant_id, store_id, method, status)`.

### `payment_intents`

Purpose: aggregate root for one amount to collect.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Nullable only for future platform-scoped payment. Required for Phase 1 store collection. |
| `intent_no` | text | Tenant scoped human-readable number. |
| `source_type` | text | `quick_pay`, `generic_merchant`, `pos_order`, `reservation_deposit`, `platform_billing`. |
| `source_id` | uuid | Nullable for quick pay. |
| `method` | text | `paynow` in Phase 1. |
| `amount` | numeric(12,2) | Required, greater than zero. |
| `currency` | text | `SGD` in Phase 1. |
| `payment_reference` | text | Required immutable provider reference. |
| `status` | text | `pending`, `awaiting_verification`, `paid`, `expired`, `cancelled`, `failed`. |
| `expires_at` | timestamptz | Optional intent-level expiry. |
| `metadata_json` | jsonb | Default `{}`. |
| `created_by` | uuid | Actor account when available. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Constraints and indexes:

- Unique `(tenant_id, intent_no)`.
- Unique `(tenant_id, payment_reference)`.
- Unique active source guard may be added per source where business rules require one unpaid payment per source.
- FK `(store_id, tenant_id)` to stores when store scoped.
- Check valid `source_type`, `method`, `status`, positive amount, and uppercase currency.
- Index `(tenant_id, store_id, status, created_at desc)`.
- Index `(tenant_id, source_type, source_id)`.
- Index `(tenant_id, payment_reference)`.

### `payment_sessions`

Purpose: short-lived QR display session for an intent.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required, duplicated for scoped queries. |
| `store_id` | uuid | Required for Phase 1. |
| `intent_id` | uuid | Required. |
| `session_no` | text | Required unique external session number. |
| `terminal_code` | text | Nullable. |
| `display_number` | integer | Nullable, allocated for terminal display. |
| `business_date` | date | Store business date. |
| `qr_payloads_json` | jsonb | Provider-neutral payload envelope. |
| `status` | text | `pending`, `awaiting_verification`, `paid`, `expired`, `cancelled`, `failed`. |
| `expires_at` | timestamptz | Required. |
| `cashier_name` | text | Optional display snapshot. |
| `created_by` | uuid | Actor account when available. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Constraints and indexes:

- Unique `(tenant_id, session_no)`.
- Unique `(tenant_id, store_id, business_date, display_number)` where `display_number is not null`.
- Intent FK must be same tenant/store.
- Check valid status and positive display number.
- Index `(tenant_id, store_id, status, expires_at)`.
- Index `(tenant_id, store_id, terminal_code, created_at desc)`.

### `payment_display_counters`

Purpose: allocate display numbers safely by tenant, store, and business date.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `business_date` | date | Required. |
| `last_number` | integer | Required. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |

Constraints and indexes:

- Unique `(tenant_id, store_id, business_date)`.
- Check `last_number between 0 and 9999`.
- Use row-level lock during allocation.

### `payment_proofs`

Purpose: uploaded proof metadata.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required for Phase 1. |
| `intent_id` | uuid | Required. |
| `session_id` | uuid | Nullable. |
| `uploaded_by_type` | text | `staff`, `terminal`, `public`, `system`. |
| `uploaded_by` | uuid | Nullable actor account. |
| `file_path` | text | Required storage pointer. |
| `file_name` | text | Required stored file name. |
| `original_name` | text | Optional client filename. |
| `file_type` | text | MIME type. |
| `file_size` | integer | Nullable. |
| `status` | text | `submitted`, `matched`, `confirmed`, `rejected`, `failed`. |
| `created_at` | timestamptz | Required. |

Constraints and indexes:

- Intent and session FKs must be same tenant/store.
- Check valid status and uploader type.
- Check file size is null or positive.
- Index `(tenant_id, store_id, intent_id, created_at desc)`.

### `payment_ocr_results`

Purpose: parsed fields from proof scan.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required for Phase 1. |
| `proof_id` | uuid | Required. |
| `ocr_reference` | text | Nullable. |
| `ocr_amount` | numeric(12,2) | Nullable. |
| `ocr_time` | timestamptz | Nullable. |
| `bank_code` | text | Nullable. |
| `success_detected` | boolean | Required default false. |
| `confidence_score` | integer | Nullable 0-100. |
| `match_result` | text | `matched`, `reference_mismatch`, `amount_mismatch`, `not_detected`, `manual_review`. |
| `confidence_breakdown_json` | jsonb | Default `{}`. |
| `raw_text` | text | Nullable, access restricted. |
| `created_at` | timestamptz | Required. |

Constraints and indexes:

- Proof FK must be same tenant/store.
- Check amount is null or non-negative.
- Check confidence score between 0 and 100 when present.
- Index `(tenant_id, store_id, ocr_reference)`.
- Index `(tenant_id, store_id, bank_code, created_at desc)`.

### `payment_verifications`

Purpose: review and final confirmation state.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required for Phase 1. |
| `intent_id` | uuid | Required. |
| `proof_id` | uuid | Required. |
| `status` | text | `submitted`, `matched`, `confirmed`, `rejected`. |
| `verified_by` | uuid | Nullable actor account. |
| `verified_at` | timestamptz | Nullable. |
| `reason` | text | Nullable. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Constraints and indexes:

- Intent and proof FKs must be same tenant/store.
- Check valid status.
- Index `(tenant_id, store_id, status, created_at desc)`.
- Unique active review may be added per proof if implementation permits only one verification row per proof.

### `payment_events`

Purpose: append-only payment lifecycle audit.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Nullable for future platform scoped payment. |
| `intent_id` | uuid | Nullable for profile events. |
| `session_id` | uuid | Nullable. |
| `event_type` | text | `intent_created`, `session_created`, `proof_submitted`, `verification_confirmed`, etc. |
| `idempotency_key` | text | Nullable, required for commands. |
| `actor_id` | uuid | Nullable. |
| `actor_type` | text | Nullable. |
| `event_payload` | jsonb | Required default `{}`. |
| `created_at` | timestamptz | Required. |

Constraints and indexes:

- Intent/session FKs must match tenant/store when present.
- Unique idempotency index by `(tenant_id, store_id, event_type, idempotency_key)` where `idempotency_key is not null`.
- Index `(tenant_id, store_id, intent_id, created_at)`.
- Index `(tenant_id, store_id, event_type, created_at desc)`.

## API Contract

All endpoints use `/api/v1`, explicit DTOs, stable error codes, and App Gate enforcement.

### Payment Profiles

```http
GET   /api/v1/stores/{storeId}/tenant-admin/payment/profile
PATCH /api/v1/stores/{storeId}/tenant-admin/payment/profile
```

Permission:

```text
@RequireAppGate(appKey = "payment", permission = "payment.settings.manage")
```

Patch request:

```json
{
  "method": "paynow",
  "status": "active",
  "paynowType": "uen",
  "paynowMobile": null,
  "paynowUen": "202012345A",
  "merchantName": "RPB Demo Restaurant",
  "currency": "SGD",
  "config": {
    "sessionTtlSeconds": 120,
    "dailyStartNumber": 0,
    "allowCustomDisplayNumber": false,
    "defaultQrDisplay": "paynow"
  },
  "version": 0
}
```

### Payment Intents

```http
POST /api/v1/stores/{storeId}/payments/intents
GET  /api/v1/stores/{storeId}/payments/intents/{intentId}
GET  /api/v1/stores/{storeId}/payments/intents
POST /api/v1/stores/{storeId}/payments/intents/{intentId}/cancel
```

Permission mapping:

| Endpoint | Permission |
|---|---|
| Create intent | `payment.intent.create` |
| Get/list intent | `payment.intent.view` |
| Cancel intent | `payment.intent.cancel` |

Create quick pay request:

```json
{
  "idempotencyKey": "quick-pay-20260805-store-001-0001",
  "sourceType": "quick_pay",
  "sourceId": null,
  "method": "paynow",
  "amount": "18.80",
  "currency": "SGD",
  "terminalCode": "COUNTER-1",
  "cashierName": "Alice",
  "requestedDisplayNumber": null,
  "metadata": {
    "note": "Counter quick payment"
  }
}
```

Create source-backed request:

```json
{
  "idempotencyKey": "pos-order-73000000-0000-0000-0000-000000000001-paynow",
  "sourceType": "pos_order",
  "sourceId": "73000000-0000-0000-0000-000000000001",
  "method": "paynow",
  "amount": "42.60",
  "currency": "SGD",
  "terminalCode": "COUNTER-1",
  "cashierName": "Alice",
  "requestedDisplayNumber": null,
  "metadata": {}
}
```

Create response:

```json
{
  "success": true,
  "replayed": false,
  "intent": {
    "id": "91000000-0000-0000-0000-000000000001",
    "storeId": "20000000-0000-0000-0000-000000000001",
    "intentNo": "PIT-202608-0001",
    "sourceType": "quick_pay",
    "sourceId": null,
    "method": "paynow",
    "amount": "18.80",
    "currency": "SGD",
    "paymentReference": "QP-202608-0001-A1B2",
    "status": "pending",
    "expiresAt": "2026-08-05T04:12:00Z",
    "version": 0
  },
  "session": {
    "id": "92000000-0000-0000-0000-000000000001",
    "sessionNo": "PRS-ABCDEF1234567890",
    "displayNumber": 1,
    "businessDate": "2026-08-05",
    "status": "pending",
    "expiresAt": "2026-08-05T04:12:00Z",
    "expiresIn": 120,
    "qrPayloads": {
      "version": 1,
      "defaultDisplay": "paynow",
      "payloads": {
        "paynow": {
          "type": "sgqr",
          "payload": "000201...",
          "enabled": true,
          "priority": 1
        }
      }
    }
  },
  "nextDisplayNumber": 2
}
```

Cancel behavior:

- Reject paid intents.
- Mark pending or awaiting verification intents as `cancelled`.
- Mark active sessions as `cancelled`.
- Append a payment event.
- Replay same idempotency key with same payload as `replayed = true`.

### Payment Sessions

```http
POST /api/v1/stores/{storeId}/payments/intents/{intentId}/sessions
GET  /api/v1/stores/{storeId}/payments/sessions/{sessionNo}
GET  /api/v1/stores/{storeId}/payments/terminal-display/active
GET  /api/v1/stores/{storeId}/payments/terminal-display/events
```

Permission mapping:

| Endpoint | Permission |
|---|---|
| Create session | `payment.session.create` |
| Get session | `payment.intent.view` |
| Active display | `payment.intent.view` |
| Display event stream | `payment.intent.view` |

Session creation is useful when the first QR session expires but the intent is still payable.

Phase 1 may return QR payloads only. QR PNG or data URL generation can be server-side or frontend-side, but the API contract must keep payload text stable.

### Proof And Verification

```http
POST /api/v1/stores/{storeId}/payments/proofs/scan
POST /api/v1/stores/{storeId}/payments/verifications/{verificationId}/confirm
POST /api/v1/stores/{storeId}/payments/verifications/{verificationId}/reject
GET  /api/v1/stores/{storeId}/tenant-admin/payments/verifications
GET  /api/v1/stores/{storeId}/tenant-admin/payments/proofs/{proofId}/file
```

Permission mapping:

| Endpoint | Permission |
|---|---|
| Submit proof | `payment.proof.submit` |
| Confirm from terminal | `payment.verification.confirm` |
| Reject from terminal | `payment.verification.reject` |
| Tenant admin review list | `payment.verification.review` |
| Proof file | `payment.verification.review` |

Proof scan request:

```text
multipart/form-data
image: file
expectedReference: QP-202608-0001-A1B2
expectedAmount: 18.80
expectedSessionNo: PRS-ABCDEF1234567890
idempotencyKey: proof-20260805-session-PRS-ABCDEF1234567890
```

Confirm request:

```json
{
  "idempotencyKey": "confirm-verification-93000000-0000-0000-0000-000000000001",
  "version": 0
}
```

Reject request:

```json
{
  "idempotencyKey": "reject-verification-93000000-0000-0000-0000-000000000001",
  "reason": "Amount mismatch",
  "version": 0
}
```

Confirmation behavior:

1. Validate verification belongs to tenant/store.
2. Reject if intent is expired, cancelled, failed, or already rejected.
3. If already paid with the same verification, return replay-safe success.
4. Mark verification confirmed.
5. Mark proof confirmed.
6. Mark intent paid.
7. Mark all intent sessions paid.
8. Invoke source adapter `paymentConfirmed`.
9. Append audit event.

### Audit And Reports

```http
GET /api/v1/stores/{storeId}/tenant-admin/payments/audit
GET /api/v1/stores/{storeId}/tenant-admin/payments/reports/summary
```

Permission:

```text
@RequireAppGate(appKey = "payment", permission = "payment.audit.view")
```

Audit query parameters:

- `q`
- `status`
- `sourceType`
- `method`
- `businessDate`
- `from`
- `to`
- `limit`
- `offset`

Summary is read-only and must not replace accounting exports in Phase 1.

### Error Codes

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `UNAUTHENTICATED` | No current actor. |
| 403 | `FORBIDDEN` | Actor lacks role, store access, App Gate entitlement, or permission. |
| 400 | `REQUEST_INVALID` | Body, file, amount, currency, method, status, or idempotency key invalid. |
| 404 | `STORE_NOT_FOUND` | Store does not exist or actor cannot access it. |
| 404 | `PAYMENT_PROFILE_NOT_FOUND` | No active PayNow profile for the tenant/store. |
| 404 | `PAYMENT_INTENT_NOT_FOUND` | Intent not found in the same tenant/store. |
| 404 | `PAYMENT_SESSION_NOT_FOUND` | Session not found in the same tenant/store. |
| 404 | `PAYMENT_PROOF_NOT_FOUND` | Proof not found in the same tenant/store. |
| 404 | `PAYMENT_VERIFICATION_NOT_FOUND` | Verification not found in the same tenant/store. |
| 409 | `PAYMENT_PROFILE_DISABLED` | PayNow profile exists but is disabled. |
| 409 | `PAYMENT_INTENT_STATE_CONFLICT` | Command invalid for current intent status. |
| 409 | `PAYMENT_SESSION_EXPIRED` | Session is no longer payable. |
| 409 | `PAYMENT_AMOUNT_MISMATCH` | Proof or source amount does not match expected amount. |
| 409 | `PAYMENT_REFERENCE_MISMATCH` | Proof reference does not match expected reference. |
| 409 | `PAYMENT_SOURCE_CONFLICT` | Source adapter rejects the payment operation. |
| 409 | `VERSION_CONFLICT` | Optimistic version mismatch. |
| 409 | `IDEMPOTENCY_CONFLICT` | Same key used with different payload. |
| 500 | `PERSISTENCE_ERROR` | Database operation failed. |

## Idempotency And Replay

Required idempotent commands:

- Configure profile.
- Create intent.
- Create session.
- Cancel intent.
- Submit proof.
- Confirm verification.
- Reject verification.

Replay rules:

| Case | Behavior |
|---|---|
| Same key and same payload after success | Return original result with `replayed = true`. |
| Same key and different payload | Return `IDEMPOTENCY_CONFLICT`. |
| Same key after failure before mutation | Allow retry. |
| New key with stale version | Return `VERSION_CONFLICT`. |
| Confirm after already paid by same verification | Return success with current paid state. |
| Confirm after already paid by different verification | Return `PAYMENT_INTENT_STATE_CONFLICT` unless source adapter explicitly allows it. |

Implementation may use the existing shared idempotency module or payment event unique keys. The implementation plan must choose one before coding.

## Data Flow

### Quick Pay

```text
Staff opens /stores/:storeId/payments
-> App Gate checks app_key=payment and entry permission
-> Staff enters amount and terminal context
-> PaymentIntentService validates active PayNow profile
-> PaymentProvider creates PayNow SGQR payload
-> PaymentSessionService allocates display number
-> API returns intent/session/QR payload
-> Customer pays through PayNow
-> Staff uploads proof or manually reviews
-> PaymentVerificationService confirms
-> Intent and sessions become paid
-> QuickPaySourceAdapter records no external business mutation
```

### POS PayNow Later

```text
POS order reaches payment step
-> POS validates app_key=pos and pos.payment.take
-> POS asks payment module to create sourceType=pos_order intent
-> Payment validates app_key=payment and active PayNow profile
-> Customer pays
-> Verification confirmed
-> PosOrderPaymentSourceAdapter marks the POS order payment as paid
-> POS order can close through POS rules
```

### Reservation Deposit Later

```text
Reservation requires deposit
-> Reservation creates sourceType=reservation_deposit payment intent
-> Customer pays PayNow
-> Verification confirmed
-> ReservationDepositSourceAdapter records deposit paid against reservation
```

## Page Flow

### Store Payment Terminal

Route:

```text
/stores/:storeId/payments
```

Required workflows:

1. View active quick payment sessions.
2. Create a quick payment amount.
3. Show PayNow QR payload or QR image.
4. Show display number and expiry countdown.
5. Upload/scan payment proof.
6. Confirm or reject matched payment when permitted.
7. Show paid, expired, cancelled, and awaiting verification states.

Required states:

- Loading App Gate and profile.
- Payment product line not enabled.
- Store payment disabled.
- Permission denied.
- PayNow profile missing.
- QR session active.
- Session expired.
- Awaiting verification.
- Paid.
- Proof rejected.
- API error with stable message.

### Tenant Admin Payment Settings

Routes:

```text
/stores/:storeId/admin/payment/settings
/stores/:storeId/admin/payment/audit
/stores/:storeId/admin/payment/verifications
/stores/:storeId/admin/payment/reports
```

Required workflows:

1. Configure PayNow by mobile or UEN.
2. Configure merchant display name.
3. Enable or disable payment profile.
4. Configure terminal display defaults.
5. Review payment intents and sessions.
6. Review proof submissions.
7. Confirm or reject proofs.
8. View payment summary.

Tenant admin pages must not include platform subscription controls. Those remain in platform billing pages.

### Platform Product Line And Billing

Existing platform product-line pages should show `payment` beside `reservation_queue` and `pos`.

Platform admins can:

- Create or update `payment` product-line metadata.
- Set monthly/yearly store unit prices for `payment`.
- Open, renew, suspend, or cancel payment billing by tenant/store through existing store-level billing workflows.

Platform billing must not inspect customer payment revenue in Phase 1. Store payment revenue reports are tenant admin payment reports.

## Phase Plan

### Phase 0: Product-Line Contract

Scope:

- Design and seed plan for `app_key = payment`.
- Payment permissions.
- App Gate visible-app entry permission fix or config-driven design.
- Tenant/store activation through existing platformbilling.
- No executable migration from this document.

Deliverable:

```text
Payment appears as a product-line concept in design and implementation plan, ready for controlled seed/migration work.
```

### Phase 1: Quick Pay PayNow MVP

Scope:

- PayNow method profile.
- Intent creation for `quick_pay` and `generic_merchant`.
- PayNow SGQR payload generation.
- Session creation and expiration.
- Display number allocation.
- Store payment terminal page.
- Payment audit list.
- Manual confirm/reject without OCR.

Deferred:

- OCR.
- POS adapter.
- Reservation deposit adapter.
- Bank reconciliation.

### Phase 2: Shared Product-Line Consumption

Scope:

- `PaymentSourceAdapter` implementation for POS order.
- `PaymentSourceAdapter` implementation for reservation deposit if product scope approves.
- Source validation and paid callbacks.
- Source-specific integration tests.
- UI entry from POS and reservation workflows.

Deferred:

- Platform billing self-payment.
- Provider webhooks.

### Phase 3: Proof OCR And Review Workbench

Scope:

- Proof upload storage.
- OCR extraction using a Java-compatible service or isolated internal adapter selected during implementation planning.
- Reference and amount matching.
- Tenant admin verification workbench.
- Confidence and raw text access controls.

### Phase 4: Reconciliation And Future Providers

Scope:

- Bank statement CSV import and matching.
- Payment disputes.
- Refund and post-confirmation correction model.
- Non-PayNow provider interfaces.
- Outbox for asynchronous provider callbacks.
- Store self-payment for platform billing if required.

## Risk Matrix

| Risk | Impact | Mitigation |
|---|---|---|
| Duplicate tenant/auth systems from sidecar | High operational and security risk | Do not use sidecar. Build RPB-native module. |
| Cross-tenant payment leakage | Severe data breach | Every table and query includes tenant scope; store-scoped APIs validate store ownership. |
| Store A payment used for Store B source | Severe financial mismatch | Source adapters validate tenant/store/source ownership before intent creation and confirmation. |
| Duplicate payment after retry | Customer overpayment or false paid state | Idempotency on create, proof, confirm, reject; unique reference and source guards. |
| Float rounding errors | Incorrect amount matching | PostgreSQL `numeric(12,2)` and Java `BigDecimal`. |
| QR session expires while customer pays | Staff confusion | Intent and session states are separate; proof can match active intent only according to documented expiry policy. |
| Manual confirmation abuse | Fraud risk | Dedicated permissions, audit events, actor identity, proof file access controls. |
| PayNow profile misconfiguration | Failed collection | Profile validation and inactive state; terminal shows profile-missing state. |
| Raw OCR text contains sensitive info | Privacy risk | Restrict raw text endpoint/fields to review permission and avoid frontend overexposure. |
| App Gate entry permission hardcoding blocks product visibility | Product not discoverable | Fix entry permission registry during product-line implementation. |
| POS and payment transaction coupling | Partial paid/order state | Source adapter must be transactional or outbox-backed, with clear failure handling. |

## Testing Matrix

### Database Review Matrix

| Scenario | Required Coverage |
|---|---|
| Payment tables migration | Tables, checks, scoped FKs, unique keys, indexes, created/updated timestamps. |
| Tenant/store isolation | Cross-tenant profile, intent, session, proof, and verification references are rejected. |
| Amount safety | Negative or zero amount, invalid currency, and invalid status fail. |
| Display number concurrency | Concurrent sessions allocate unique tenant/store/business-date display numbers. |
| Reference uniqueness | Duplicate payment reference cannot be inserted for same tenant. |
| Idempotency uniqueness | Duplicate idempotency key cannot create duplicate intent/session/confirmation. |

### API Review Matrix

| Scenario | Required Coverage |
|---|---|
| App Gate enabled | Payment endpoints pass when app, tenant entitlement, store setting, store access, and permission are valid. |
| App Gate disabled | Product disabled, tenant not entitled, expired entitlement, or store app disabled returns 403. |
| Permission denied | Missing endpoint permission returns stable forbidden response. |
| Stable errors | Documented payment error codes map to HTTP statuses. |
| DTO boundary | No domain entity or persistence row leaks through API response. |
| Replay | Idempotent commands return replay responses. |
| Source validation | Source-backed intents cannot reference another tenant/store source. |

### TDD Review Matrix

| Scenario | Required Coverage |
|---|---|
| Create quick pay intent | Creates intent, reference, session, display number, QR payload, and audit event. |
| Missing PayNow profile | Create intent returns `PAYMENT_PROFILE_NOT_FOUND` or `PAYMENT_PROFILE_DISABLED`. |
| Expired session | Session display returns expired state and cannot be confirmed as active. |
| Manual confirm | Marks verification, proof, intent, and sessions paid. |
| Manual reject | Marks verification rejected and intent failed when not paid. |
| Duplicate create | Same idempotency key returns replay and does not allocate a second display number. |
| Amount mismatch | Proof or confirm path rejects mismatched amount. |
| Reference mismatch | Proof path rejects mismatched reference. |
| Cross-store access | Store A actor cannot see or mutate Store B payment. |
| Frontend states | Loading, missing profile, permission denied, active QR, awaiting verification, paid, expired, error. |

### Manual Smoke

1. Platform admin opens `payment` for one tenant and one store.
2. Tenant admin configures PayNow UEN and merchant name.
3. Staff opens `/stores/{storeId}/payments`.
4. Staff creates a quick payment for SGD 18.80.
5. QR payload and display number appear.
6. Staff submits proof or manually confirms according to Phase 1 scope.
7. Intent and session show paid.
8. Tenant admin sees the payment in audit.
9. Disable store app setting for `payment` and confirm endpoint and entry are denied.
10. Enable sibling store without profile and confirm it shows missing profile, not sibling configuration.

## Review Notes

### Architecture Review Notes

- No local `architecture-review` skill file was found in this repository during design drafting.
- This design follows the existing RPB module shape: `api`, `application`, `domain`, `persistence`, with an added `provider` package for payment channel adapters.
- Product-line commercial state remains in `platformbilling`; runtime authorization remains in App Gate; customer payment state lives in `payment`.
- Payment source updates are isolated behind adapters so POS, reservation, and platform billing boundaries are not collapsed into payment services.

### API Review Notes

- Paths use `/api/v1`.
- Store-scoped payment endpoints include `stores/{storeId}`.
- Request and response DTOs are explicit.
- Error codes and HTTP statuses are documented.
- App Gate permission is specified by endpoint group.
- Commands that can be retried require idempotency.
- Replay behavior is documented.
- Frontend states include empty, loading, error, and permission-denied handling.

### Database Review Notes

- Tenant-scoped operational data includes `tenant_id`.
- Phase 1 store payment data includes `store_id`.
- Unique constraints include tenant/store scope where required.
- Monetary fields use `numeric(12,2)`.
- Enum-like fields require check constraints.
- Display number allocation requires a locked counter row.
- No executable migration is created by this document.

### TDD Review Notes

- The test matrix covers happy path, permission failure, App Gate denial, duplicate command replay, cross-tenant/store access, invalid profile, amount mismatch, reference mismatch, and frontend states.
- Implementation must write tests before or alongside code for every Phase 1 behavior.

### Code Review Notes

- Controllers must not access repositories directly.
- Provider code must not load business source entities.
- Source adapter code must validate tenant/store/source ownership.
- Payment confirmation must not bypass App Gate or source state rules.
- Payment audit and idempotency are required for high-risk commands.

## Implementation Notes For Later

- Before runtime or migration validation, use `target/local-postgres-current.txt` according to `AGENTS.md`.
- Do not create executable migrations from this document without a separate database review.
- Do not add payment APIs without a separate implementation plan and API contract review.
- Do not broaden local runtime allowlists except for explicitly tested payment routes.
- Add release notes only after implementation is complete.
