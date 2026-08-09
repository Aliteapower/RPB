# PayNow Proof Template Platform Library

## New

- Platform admins can maintain shared PayNow receipt templates.
- Tenants can use platform templates by default and submit missing bank layouts for review.
- Uploaded/captured receipt samples can generate draft JSON rule suggestions.

## Migration

- Adds `V055__paynow_platform_proof_template_contributions.sql`.
- Adds platform permission `platform.payment_proof_template.manage`.

## Safety

- Auto-confirmation still requires unique Ref and exact amount match.
- Uploaded sample image bytes are not stored.

## Rollback Notes

- Restore backend jar and frontend bundle.
- Remove `platform.payment_proof_template.manage` rows from `auth_account_permissions` so the rolled-back application does not retain the retired platform capability.
- If schema rollback is required, delete contribution rows and drop `payment_proof_template_contributions` after the application rollback.

## Validation

- `mvn "-Dtest=PaymentProofTemplatePlatformMigrationTest,PaymentProofTemplateRuleSuggestionTest,PaymentProofTemplateContributionServiceTest,PlatformPaymentProofTemplateControllerTest,PaymentProofTemplateContributionControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`: passed, 27 tests with 0 failures and 0 errors.
- `mvn "-Dtest=PaymentProofTemplateServiceTest,PaymentProofReviewServiceTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewControllerTest" test`: passed, 28 tests with 0 failures and 0 errors.
- `mvn "-Dtest=PaymentMigrationTest,PaymentProofTemplateMigrationTest,PaymentProofTemplatePlatformMigrationTest" test`: passed, 8 tests with 0 failures and 0 errors.
- `npm run build`: passed (`vue-tsc --noEmit && vite build`).
- `git diff --check`: passed with no whitespace errors.

## Production Deployment

- Deployed commit: `4bc9e5d2 chore: ignore local superpowers scratch`.
- Functional hardening commit: `f90de6c4 fix: isolate malformed proof template patterns`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-09.
- Backend artifact built from clean worktree `target/deploy-worktree-4bc9e5d2`.
- Frontend artifact built from clean worktree `target/deploy-worktree-4bc9e5d2`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-4bc9e5d2.jar`
  - `/home/ubuntu/rpb-4bc9e5d2-frontend.tgz`
- Production backup: `/opt/rpb/backups/20260809-1656-4bc9e5d2-paynow-proof-template-platform-library`.
- Backend JAR SHA-256: `450F79C9D702A6E82957F53C637154A5D14E275C4A7AB4E545E4974B81256E2C`.
- Frontend bundle SHA-256: `A8383874BCF8EB22A99CC5F98C3F3BBF4DA2479A11368D1EB04FE300D71089C6`.
- Flyway latest: `055|paynow platform proof template contributions|t`.
- `rpb-backend`: `active / running`, PID `439209`, recent `ERROR` count: `0`.
- Clean deploy worktree `mvn -DskipTests package`: passed.
- Clean deploy worktree `npm ci && npm run build`: passed.
- Online smoke:
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/platform/payment/proof-templates`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/proof-templates`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/settings`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `https://booking.yumstone.sg/api/v1/platform/payment/proof-templates`: `401`.
  - `PlatformPaymentProofTemplatesPage-CTDGNnjM.js`, `TenantAdminPaymentProofTemplatesPage-COdhbrCv.js`, `PaymentProofReviewPage-9A4yJzuR.js`, `PaymentQuickPayPage-BBB4VFjV.js`, `api-Cb-eKb-b.js`, `i18n-tHxvIumk.js`, and `index-CPHhLHfg.js`: `200`.
