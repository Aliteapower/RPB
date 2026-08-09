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

## Frontend-Only Follow-Up: Proof Auto-Close

- Deployed commit: `677db79a fix: auto close paynow proof confirmations`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-09.
- Scope: frontend-only; backend JAR, Flyway, App Gate permissions, and API contracts were not changed.
- Behavior:
  - 回单校验页在 `auto_confirmed` / `already_confirmed` 后播放“收款 N 成功”语音。
  - 扫描模式下自动清理成功结果并继续扫下一笔。
  - 自动确认成功会清理对应 Quick Payment 本地展示记录，顾客展示屏/收银页不再保留已确认记录或 pending 倒计时。
  - QuickPay 最近列表只保留 `pending` / `awaiting_verification`，人工 `确认` 按钮仍作为待核验单兜底，不会把自动确认误记为手工确认。
- Production frontend backup: `/opt/rpb/backups/20260809-1756-677db79a-paynow-proof-auto-close-frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260809-1756-677db79a-paynow-proof-auto-close`.
- Clean deploy worktree: `target/deploy-worktree-677db79a`.
- Validation:
  - `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - `npm ci`: completed.
  - `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Production `rpb-backend`: `active / running`, PID `439209`, recent `ERROR` count: `0`.
  - `https://booking.yumstone.sg/login`: `200`, loaded `/assets/index-DWXWf2pH.js`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `PaymentProofReviewPage-h5YBDsQt.js`, `PaymentQuickPayPage-CnsYZJoV.js`, `PaymentPresentPage-CdJqFuyV.js`, and `paymentPresentBridge-Gm3BQeSq.js`: `200`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260809-1756-677db79a-paynow-proof-auto-close-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260809-1756-677db79a-paynow-proof-auto-close`, then reload nginx.
