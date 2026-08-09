# PayNow Proof Template Library

## Version / Date

2026-08-09

## New

- Added the PayNow proof template library for tenant admin payment settings.
- Added platform seed templates for OCBC Chinese PayNow and a generic English PayNow receipt shape.
- Added tenant custom template CRUD endpoints and a diagnostic sample scan endpoint that does not mutate payment state.
- Added template-aware OCR enhancement before the existing Ref and amount verification flow.

## Changed

- Payment Proof Review now attempts to enrich OCR fields through active tenant/platform proof templates before matching a Quick Pay candidate.
- The tenant admin PayNow navigation now includes `回单样式库` / Proof Templates.

## Migration

- Added `V054__paynow_payment_proof_template_library.sql`.
- Creates `payment_proof_templates` and `payment_proof_template_samples`.
- Seeds platform templates and backfills `payment.proof_template.manage` for tenant admins.

## Permission

- Added App Gate permission `payment.proof_template.manage`.
- The proof scan staff flow continues to use `payment.proof.review`.

## Risk

- Template samples can contain bank receipt text; sample retention and image storage should stay scoped to tenant tuning and avoid unnecessary customer data exposure.
- Template matching improves extraction only. Auto confirmation still requires a unique RPB Ref and exact amount match.

## Rollback Notes

- Roll back frontend assets and backend code together if the template admin page or endpoint has issues.
- If migration rollback is required, remove tenant permissions for `payment.proof_template.manage`, delete seed/template sample rows, then drop `payment_proof_template_samples` before `payment_proof_templates`.
