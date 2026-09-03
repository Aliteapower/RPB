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

## Deployment

- 功能提交：`a57b66b9 feat: add paynow proof template library`
- 部署日期：2026-08-09
- 分支：`codex/paynow-payment-product-line-staging`
- Backend artifact built from clean worktree `target/deploy-worktree-a57b66b9`.
- Frontend artifact built from clean worktree `target/deploy-worktree-a57b66b9`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-a57b66b9.jar`
  - `/home/ubuntu/rpb-a57b66b9-frontend.tgz`
- 生产备份：`/opt/rpb/backups/20260809-1205-a57b66b9-paynow-proof-template-library`
- Backend JAR SHA-256：`36A9F5205C34D9E5CFB63EE1DE6CB18A700C47A43B8A14C7ABAACCBB17EC8CAD`
- Flyway latest：`054|paynow payment proof template library|t`
- `rpb-backend`：`active / running`，PID `366803`，recent `ERROR` count：`0`
- 验证：
  - `mvn "-Dtest=PaymentProofTemplateServiceTest,PaymentProofTemplateMigrationTest,PaymentProofTemplateControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`：8 tests，0 failures，0 errors
  - `mvn "-Dtest=PaymentProofReviewServiceTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewControllerTest" test`：24 tests，0 failures，0 errors
  - `mvn "-Dtest=PaymentMigrationTest,PaymentProofTemplateMigrationTest,PayNowPaymentUiAcceptanceValidationTest" test`：10 tests，0 failures，0 errors
  - `npm run build`：通过
  - clean deploy worktree `mvn -q -DskipTests package`：通过
  - clean deploy worktree `npm ci && npm run build`：通过
- 线上 smoke：
  - `https://booking.yumstone.sg/login`：200
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/proof-templates`：200
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/settings`：200
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`：200
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`：200
  - `TenantAdminPaymentProofTemplatesPage-Yg-oUrXv.js`、`TenantAdminPaymentProofTemplatesPage-DpWnQdkk.css`、`PaymentProofReviewPage-BipOl1oy.js`、`PaymentQuickPayPage-C0RuYs_I.js`、`api-ZmgbUFYc.js`、`i18n-B221imF3.js`：200
  - `https://booking.yumstone.sg/api/v1/auth/me`：401
  - unauthenticated proof template list endpoint：403
