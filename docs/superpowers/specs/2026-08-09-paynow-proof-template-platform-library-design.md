# PayNow Proof Template Platform Library Design

## Scope

This design extends the existing PayNow proof template MVP from tenant-managed templates into a platform-governed receipt style library.

The goal is to improve Payment Proof Review OCR accuracy across banks without forcing every tenant to rebuild the same rules. Platform admins maintain approved bank receipt templates. Tenants automatically use active platform templates, and only add their own template when a bank or receipt layout is missing. Tenant-created templates can be submitted back to the platform for review and promotion into the shared library.

This design affects platform admin UI, tenant admin UI, API, database schema, and OCR template tuning. It does not change the final payment confirmation safety rule: automatic confirmation still requires one unique system Ref and exact amount match.

## Current Baseline

Existing implementation already provides:

- `payment_proof_templates` with platform seed and tenant custom rows.
- `payment_proof_template_samples` for tenant-scoped diagnostic samples.
- Tenant admin page `/stores/:storeId/admin/payment/proof-templates`.
- Template-aware OCR enhancement before Payment Proof Review matches a Quick Pay candidate.
- Active platform templates are included in tenant effective matching.

Current gaps:

- Platform templates are seeded by SQL only and cannot be maintained in platform backend UI.
- Uploading a sample only tests OCR; it does not produce a draft rule suggestion.
- Tenant templates cannot be submitted for platform reuse.
- There is no platform review queue for tenant-contributed bank receipt styles.
- Tenants can still duplicate platform templates unnecessarily.

## Recommended Product Flow

### Platform Admin

Platform admin opens `/platform/payment/proof-templates`.

The page supports:

- List active/draft/inactive platform bank receipt templates.
- Create or edit platform templates.
- Upload or capture a receipt sample.
- Run OCR on the sample and generate a JSON rule suggestion.
- Review extracted Ref, amount, success keywords, match keywords, and raw OCR text.
- Save the generated rule as draft or active platform template.
- Review tenant-submitted templates and either:
  - accept as a new platform template,
  - merge/update an existing platform template,
  - reject with a short reason.

### Tenant Admin

Tenant admin PayNow proof template page changes from "copy platform seed" toward "use platform first".

The page supports:

- Show platform templates as "已引用平台模板" when active.
- Hide or de-emphasize copying when a matching active platform template already exists.
- Allow tenant custom templates only for missing bank/layout coverage.
- Let tenant upload/capture a sample to generate a tenant draft rule.
- Let tenant submit a custom template to platform review.
- Show contribution status: `not_submitted`, `submitted`, `accepted`, `rejected`.

### Staff Proof Review

No workflow change for staff:

- Camera/upload OCR reads the proof.
- Template selection checks tenant custom templates first, then active platform templates.
- Auto-confirm only happens when unique Ref and exact amount match.
- If template rules improve extraction but amount remains missing, the UI still shows manual review.

## Data Model

### Existing Table Reuse

Keep `payment_proof_templates` as the source of effective templates.

Platform rows:

- `tenant_id = null`
- `source = 'platform_seed'` or, in the new model, `source = 'platform_library'`

Tenant rows:

- `tenant_id = tenant`
- `source = 'tenant_custom'`

To avoid broad changes, `platform_seed` can continue to represent platform-owned templates, including UI-maintained ones. A future migration may rename this to `platform_library`, but that is not required for this slice.

### New Contribution Table

Add `payment_proof_template_contributions`.

Recommended columns:

- `id uuid primary key`
- `tenant_id uuid not null references tenants(id)`
- `store_id uuid null references stores(id)`
- `source_template_id uuid null references payment_proof_templates(id)`
- `platform_template_id uuid null references payment_proof_templates(id)`
- `bank_code text not null`
- `bank_name text not null`
- `locale text not null`
- `template_name text not null`
- `layout_json jsonb not null`
- `sample_file_name text null`
- `sample_content_type text null`
- `sample_file_digest text null`
- `sample_raw_text text null`
- `sample_ocr_reference text null`
- `sample_ocr_amount numeric(12,2) null`
- `status text not null default 'submitted'`
- `review_note text null`
- `submitted_by uuid null`
- `reviewed_by uuid null`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`
- `reviewed_at timestamptz null`
- `version integer not null default 0`

Status values:

- `submitted`
- `accepted`
- `rejected`
- `withdrawn`

Indexes:

- `(status, created_at desc)` for platform review queue.
- `(tenant_id, created_at desc)` for tenant contribution history.
- `(bank_code, locale, status)` for duplicate review.

Tenant isolation:

- Tenant APIs can only read their own contribution rows.
- Platform APIs can read all contribution rows.

## Rule Generation

Rule generation should be deterministic and conservative. It should propose JSON, not silently publish it.

Input:

- OCR raw text from uploaded/captured proof.
- Optional expected Ref and amount supplied by admin UI, if available.
- Optional bank code/name selected by the admin.

Output:

```json
{
  "matchKeywords": ["OCBC", "您已支付"],
  "successKeywords": ["您已支付", "Payment successful"],
  "referencePatterns": ["(?:讯息|信息|Message|Comment|Ref)\\s*[:：]?\\s*\"?([A-Z0-9.-]{10,32})\"?"],
  "amountPatterns": ["(?:您已支付|sent|paid)\\s*(?:S\\$|SGD)?\\s*([0-9OoIl,.]+)"],
  "referenceRoi": [0.0, 0.30, 1.0, 0.66],
  "amountRoi": [0.0, 0.15, 1.0, 0.42]
}
```

Generation rules:

- Prefer known labels near the extracted system Ref: `讯息`, `信息`, `Message`, `Comment`, `Ref`, `Reference`.
- Prefer amount labels near successful payment phrases: `您已支付`, `You've sent`, `Payment successful`, `paid`, `sent`, `SGD`, `S$`.
- Include bank/app-specific match keywords from OCR text only when stable, such as `OCBC`, `DBS`, `UOB`, `POSB`, `PayNow`.
- Avoid generating patterns from transaction IDs because they may be unrelated to RPB Ref.
- Never allow generated rules to bypass Ref format validation.
- Keep generated template status as `draft` until a platform admin activates it.

## API Design

### Platform Template Library

Base path:

`/api/v1/platform/payment/proof-templates`

Endpoints:

- `GET /api/v1/platform/payment/proof-templates`
  - Lists all platform templates.
- `POST /api/v1/platform/payment/proof-templates`
  - Creates a platform template.
- `PATCH /api/v1/platform/payment/proof-templates/{templateId}`
  - Updates a platform template with optimistic `version`.
- `POST /api/v1/platform/payment/proof-templates/rule-suggestions`
  - Multipart upload/capture sample.
  - Returns OCR fields, raw text, generated `layoutJson`, and confidence hints.

Permission:

- Platform admin role required.
- Permission: `platform.payment_proof_template.manage`.

### Tenant Contributions

Tenant base path:

`/api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`

Endpoints:

- `GET /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
  - Lists current tenant contribution history.
- `POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
  - Submits a tenant custom template for platform review.
- `POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-rule-suggestions`
  - Multipart upload/capture sample.
  - Returns generated JSON suggestion for a tenant draft.

Permission:

- Existing tenant permission `payment.proof_template.manage`.

### Platform Contribution Review

Base path:

`/api/v1/platform/payment/proof-template-contributions`

Endpoints:

- `GET /api/v1/platform/payment/proof-template-contributions?status=submitted`
  - Lists review queue.
- `POST /api/v1/platform/payment/proof-template-contributions/{contributionId}/accept`
  - Creates a new platform template or updates a selected existing platform template.
- `POST /api/v1/platform/payment/proof-template-contributions/{contributionId}/reject`
  - Marks contribution rejected with review note.

Permission:

- Platform admin role required.
- Permission: `platform.payment_proof_template.manage`.

Error behavior:

- `REQUEST_INVALID` for invalid JSON, unsupported image type, missing required fields, or impossible status transitions.
- `VERSION_CONFLICT` for stale template or contribution versions.
- `PERMISSION_DENIED`/403 for role or permission failure.
- 401 for unauthenticated access.

## UI Design

### Platform Page

Route:

`/platform/payment/proof-templates`

Navigation:

Add to platform admin nav as `PayNow 回单样式库`.

Layout:

- Left panel: bank/template list with filters for status and bank.
- Main panel: template editor.
- Right or lower panel: sample upload/capture, OCR result, generated rule preview.
- Contribution review tab: pending tenant submissions.

Controls:

- Bank code/name, locale, template name, status, priority.
- JSON editor for generated rules.
- Upload sample and, on mobile/tablet, capture sample.
- Buttons: generate rule, save draft, activate, accept contribution, reject contribution.

### Tenant Page

Route remains:

`/stores/:storeId/admin/payment/proof-templates`

Changes:

- Split list into:
  - Platform templates in use.
  - Tenant custom templates.
  - Submitted contributions.
- Platform templates are read-only and shown as globally maintained.
- Tenant custom template has `提交给平台` action.
- Upload/capture sample can generate JSON rule suggestion before saving tenant custom template.

## Recognition Behavior

Template matching order remains:

1. Tenant active custom template.
2. Platform active template.

Accepted platform templates benefit all tenants without requiring a copy.

Tenant custom templates remain useful when a single tenant sees a new bank layout before the platform has reviewed it.

The proof review confirmation gate remains unchanged:

- Ref must normalize to a valid RPB system reference.
- Ref must resolve to one active candidate in the same tenant/store/business-day context.
- Extracted amount must equal expected amount.

## Security And Privacy

- Uploaded receipt samples may contain customer names, phone numbers, transaction IDs, and bank text.
- Store OCR raw text and file digest only. Do not store original image bytes in this slice.
- Tenant contribution samples are visible to platform admins for review.
- Tenant admins can only see their own contributions.
- Platform templates must not expose another tenant's store/customer-specific data.
- Generated rules should be edited before activation when OCR text includes masked names or phone numbers.

## Testing Strategy

Backend tests:

- Platform admin permission required for platform template endpoints.
- Tenant admin cannot create platform templates.
- Tenant contribution create/list is tenant scoped.
- Accepting a contribution creates or updates a platform template.
- Rejected contribution cannot be accepted without a valid transition.
- Rule suggestion returns valid JSON and does not mutate payment intents/sessions.
- Effective OCR matching still prefers tenant custom over platform template.

Migration tests:

- New contribution table exists with tenant/store/template references.
- Status check constraint exists.
- Platform permission is seeded for platform admin accounts.
- Indexes support review queue and tenant history.

Frontend source tests:

- Platform route/nav/API client/type entries exist.
- Tenant page contains contribution actions and platform-use language.
- Generated i18n keys exist in zh-CN and en-SG.

Build verification:

- Focused Maven tests for payment template platform library.
- PayNow UI acceptance validation.
- `npm run build`.

## Rollout Plan

1. Add schema and platform permission.
2. Add backend platform template CRUD and rule suggestion service.
3. Add tenant contribution APIs.
4. Add platform admin page.
5. Update tenant admin page.
6. Run focused tests and frontend build.
7. Deploy backend and frontend together because the slice includes Flyway migration and new routes.

## Rollback Plan

- Restore previous backend jar and frontend bundle from deployment backup.
- If database rollback is required:
  - Delete contribution rows.
  - Remove `platform.payment_proof_template.manage` from platform admin accounts.
  - Drop `payment_proof_template_contributions`.
- Existing V054 platform and tenant templates remain compatible with the previous deployed proof template library.

## Out Of Scope

- Storing original receipt images.
- Machine-learning layout classification.
- Auto-publishing tenant templates without platform review.
- Editing real payment confirmation rules beyond OCR field extraction.
- Backfilling historical receipt scans into templates.
