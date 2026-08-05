# PayNow Payment Staging UAT Runbook

Use this guide to deploy and smoke-test the RPB-native `payment` product line in a staging or UAT environment.

## Scope

This runbook covers the backend-only PayNow payment foundation from branch `codex/paynow-payment-product-line-design`.

Included:

- Flyway migration `V047__payment_product_line_foundation.sql`
- `payment` platform app seed
- PayNow payment profile API
- Quick Pay intent/session API

Not included:

- Production rollout
- Payment frontend workbench
- POS or reservation adapters
- OCR or bank reconciliation
- Runtime calls to `D:\payment_runtime`

## Pre-Deploy Gates

- Confirm the deployment target is staging/UAT, not production.
- Confirm database backup or restore point exists before applying V047.
- Confirm the branch or PR includes commit `24f404da` or later.
- Confirm targeted tests passed:

```powershell
mvn '-Dtest=AppGateServiceTest,PaymentMigrationTest,PayNowQrPayloadBuilderTest,PaymentMethodProfileServiceTest,PaymentIntentServiceTest,PaymentProfileControllerTest,PaymentIntentControllerTest' test
```

- Confirm Spring context smoke passed after the constructor-selection fix:

```powershell
mvn -Dtest=PlatformTenantApiIntegrationTest test
```

## Deploy

Build from a clean worktree at the exact commit being deployed:

```powershell
$sha = git rev-parse --short HEAD
$deployWorktree = "target/deploy-worktree-$sha"
git worktree add --detach $deployWorktree HEAD
Push-Location $deployWorktree
mvn -DskipTests package
Pop-Location
```

Deploy the backend artifact using the existing staging backend deployment procedure. Flyway must apply V047 on application startup.

## Database Checks

Run these checks against the staging database after startup:

```sql
select app_key, app_name, status, default_entry_route, config_json
from platform_apps
where app_key = 'payment';

select app_key, billing_cycle, amount, currency, status
from platform_product_line_prices
where app_key = 'payment'
order by billing_cycle;

select to_regclass('payment_method_profiles') as payment_method_profiles,
       to_regclass('payment_intents') as payment_intents,
       to_regclass('payment_sessions') as payment_sessions,
       to_regclass('payment_display_counters') as payment_display_counters;
```

Expected:

- `platform_apps.payment` exists and is `active`.
- Monthly and yearly `platform_product_line_prices` rows exist for `payment`.
- Payment operational tables resolve to non-null regclass values.

## Tenant And Store Activation

For the staging test tenant/store, enable the payment product line through normal platform administration where available.

Minimum required state:

- Tenant entitlement for `app_key = 'payment'` is enabled and not expired.
- Store app setting for `app_key = 'payment'` is enabled and entry-visible.
- Test actor can access the target `storeId`.
- Test actor has:
  - `payment.settings.manage` for PayNow profile setup.
  - `payment.intent.create` for Quick Pay intent creation.
  - Optional: `payment.intent.view` for app entry visibility.

## API Smoke

Set local variables for the staging API host and authenticated session/token according to the environment:

```powershell
$baseUrl = "https://<staging-host>"
$storeId = "<store-uuid>"
$token = "<staging-auth-token>"
```

### 1. Configure PayNow Profile

```powershell
Invoke-RestMethod `
  -Method Patch `
  -Uri "$baseUrl/api/v1/stores/$storeId/tenant-admin/payment/profile" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{
    "method": "paynow",
    "status": "active",
    "paynowType": "uen",
    "paynowUen": "202012345A",
    "merchantName": "RPB Staging Restaurant",
    "currency": "SGD",
    "configJson": "{}",
    "version": 0
  }'
```

Expected:

- HTTP 200
- Response `method = paynow`
- Response `status = active`
- Response `merchantName = RPB Staging Restaurant`

### 2. Create Quick Pay Intent

Use a unique idempotency key per smoke run:

```powershell
$idempotencyKey = "uat-paynow-" + (Get-Date -Format "yyyyMMddHHmmss")
$body = @{
  idempotencyKey = $idempotencyKey
  sourceType = "quick_pay"
  method = "paynow"
  amount = 18.80
  currency = "SGD"
  terminalCode = "COUNTER-1"
  cashierName = "UAT Cashier"
  metadataJson = "{}"
} | ConvertTo-Json

$first = Invoke-RestMethod `
  -Method Post `
  -Uri "$baseUrl/api/v1/stores/$storeId/payments/intents" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

Expected:

- HTTP 200
- `success = true`
- `replayed = false`
- `intent.intentNo` starts with `PIT-`
- `intent.paymentReference` starts with `QP-`
- `session.sessionNo` starts with `PRS-`
- `session.qrPayloadsJson` contains the payment reference

### 3. Verify Idempotency Replay

```powershell
$second = Invoke-RestMethod `
  -Method Post `
  -Uri "$baseUrl/api/v1/stores/$storeId/payments/intents" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body

$second
```

Expected:

- HTTP 200
- `success = true`
- `replayed = true`
- `intent.id` equals the first response intent id
- `session.id` equals the first response session id

### 4. Database Smoke

```sql
select intent_no, source_type, method, amount, currency, payment_reference, status, idempotency_key
from payment_intents
where idempotency_key = '<idempotency-key>';

select session_no, display_number, business_date, status, qr_payloads_json
from payment_sessions
where idempotency_key = '<idempotency-key>';
```

Expected:

- Exactly one intent row.
- Exactly one session row.
- Session QR payload JSON includes the PayNow payload and reference.

## Rollback

If staging smoke fails before real payment usage:

- Redeploy the previous backend artifact.
- Restore the staging database backup if V047 must be removed.

If V047 has already been used to create payment rows:

- Do not drop payment tables blindly.
- Disable `payment` entitlement/store setting for affected staging tenants.
- Keep payment rows for audit/debug unless the staging database can be safely restored wholesale.

## Production Readiness Decision

Staging/UAT decision: GO after all API smoke checks pass.

Production decision: NO_GO until:

- Full-suite frontend validation failures are either fixed or explicitly accepted as unrelated.
- Frontend payment settings and Quick Pay workbench are implemented or the release is approved as API-only.
- Support/audit expectations for payment operations are finalized.
