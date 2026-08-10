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

## Frontend-Only Follow-Up: Amount Voice And QR Clear

- Deployed commit: `eb76e65b fix: speak paynow amount and clear confirmed qr`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-10.
- Scope: frontend-only; backend JAR, Flyway, App Gate permissions, and API contracts were not changed.
- Behavior:
  - 回单校验自动确认后，语音优先播报识别/应收金额，例如“收款 0.1 元成功”，不再只播“收款成功”。
  - 回单校验确认成功时，若候选单没有 session 编号，会按 Ref 清理 Quick Payment 本地展示记录。
  - 手动确认和自动确认后都从展示屏 active/recent 本地记录中删除对应二维码，不再显示 `paid` 或继续等倒计时过期。
- Production frontend backup: `/opt/rpb/backups/20260810-1509-eb76e65b-paynow-amount-voice-clear-qr-frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260810-1509-eb76e65b-paynow-amount-voice-clear-qr`.
- Clean deploy worktree: `target/deploy-worktree-eb76e65b`.
- Validation:
  - Main worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Main worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Clean deploy worktree `npm ci`: completed; npm audit reported the existing 3 high severity findings.
  - Clean deploy worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Clean deploy worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Production `rpb-backend`: `active`, recent 5-minute `ERROR` count: `0`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `PaymentProofReviewPage-D7COEeS0.js`, `PaymentQuickPayPage-BeEfAaD3.js`, `PaymentPresentPage-Mxf21bz9.js`, `paymentPresentBridge-CDPRPkDm.js`, and `i18n-wAlLMB7z.js`: `200`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-1509-eb76e65b-paynow-amount-voice-clear-qr-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260810-1509-eb76e65b-paynow-amount-voice-clear-qr`, then reload nginx.

## Frontend-Only Follow-Up: Continuous Proof Scanner

- Deployed commit: `3417d01d fix: keep paynow proof scanner continuous`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-10.
- Scope: frontend-only; backend JAR, Flyway, App Gate permissions, and API contracts were not changed.
- Behavior:
  - 回单校验 live 扫描在自动确认成功后不再关闭摄像头流。
  - 成功提示期间暂停后续帧提交，避免同一张回单在语音/成功卡片展示期间重复触发。
  - 成功卡片自动关闭后继续使用当前摄像头流识别下一笔，减少下一单重新开启相机导致无法识别的问题。
- Production frontend backup: `/opt/rpb/backups/20260810-1522-3417d01d-paynow-proof-continuous-scanner-frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260810-1522-3417d01d-paynow-proof-continuous-scanner`.
- Clean deploy worktree: `target/deploy-worktree-3417d01d`.
- Validation:
  - Main worktree red test: `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test` failed before the fix because live scanner success did not pause auto-advance frames.
  - Main worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Main worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Clean deploy worktree `npm ci`: completed; npm audit reported the existing 3 high severity findings.
  - Clean deploy worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Clean deploy worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Production `rpb-backend`: `active`, recent 5-minute `ERROR` count: `0`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `PaymentProofReviewPage-Dt6Nuk8z.js`, `PaymentQuickPayPage-D6qpTRA7.js`, `PaymentPresentPage-CRyQejiS.js`, `paymentPresentBridge-CDPRPkDm.js`, and `i18n-wAlLMB7z.js`: `200`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-1522-3417d01d-paynow-proof-continuous-scanner-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260810-1522-3417d01d-paynow-proof-continuous-scanner`, then reload nginx.

## Frontend-Only Follow-Up: Rescan Manual Review Card

- Deployed commit: `58bfa2d7 fix: keep proof review visible while rescanning`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-10.
- Scope: frontend-only; backend JAR, Flyway, App Gate permissions, and API contracts were not changed.
- Behavior:
  - 回单校验 live 扫描在 `needs_review` 时不再关闭摄像头流。
  - 扫码读不到金额或金额匹配不成功时，结果卡片显示“请人工确认”，并保留已读到的 Ref / 金额 / 应收金额 / 校验标签。
  - 员工可以继续移动顾客手机或重新对准回单，系统继续读取下一帧；只有自动确认成功才自动进入下一笔。
- Production frontend backup: `/opt/rpb/backups/20260810-1535-58bfa2d7-paynow-proof-rescan-review-card-frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260810-1535-58bfa2d7-paynow-proof-rescan-review-card`.
- Clean deploy worktree: `target/deploy-worktree-58bfa2d7`.
- Validation:
  - Main worktree red test: `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test` failed before the fix because live scanner `needs_review` still stopped the scanner.
  - Main worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Main worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Clean deploy worktree `npm ci`: completed; npm audit reported the existing 3 high severity findings.
  - Clean deploy worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Clean deploy worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Production `rpb-backend`: `active`, recent 5-minute `ERROR` count: `0`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `PaymentProofReviewPage-BHcX_uZ2.js`, `PaymentProofReviewPage--IwEetT2.css`, `PaymentQuickPayPage-CdkAojnk.js`, `PaymentPresentPage-9XaIspnS.js`, `paymentPresentBridge-CDPRPkDm.js`, and `i18n-DC9r3MIA.js`: `200`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-1535-58bfa2d7-paynow-proof-rescan-review-card-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260810-1535-58bfa2d7-paynow-proof-rescan-review-card`, then reload nginx.

## Frontend-Only Follow-Up: Proof Voice Amount Source

- Deployed commit: `1d5395a0 fix: speak paynow proof amount from candidate`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-10.
- Scope: frontend-only; backend JAR, Flyway, App Gate permissions, and API contracts were not changed.
- Behavior:
  - 回单校验自动确认/已确认后的语音播报金额改为优先使用匹配候选单的 `amount`。
  - 没有候选单时才使用 OCR 识别金额；不再用 `expectedAmount` 作为语音兜底，避免单号/展示号被读成金额。
  - 文案仍为“收款 {amount} 元成功”，金额格式继续去掉无意义尾零。
- Production frontend backup: `/opt/rpb/backups/20260810-1544-1d5395a0-paynow-proof-voice-amount-frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260810-1544-1d5395a0-paynow-proof-voice-amount`.
- Clean deploy worktree: `target/deploy-worktree-1d5395a0`.
- Validation:
  - Main worktree red test: `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test` failed before the fix because `resolveSuccessfulAmount` still preferred `expectedAmount`.
  - Main worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Main worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Clean deploy worktree `npm ci`: completed; npm audit reported the existing 3 high severity findings.
  - Clean deploy worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Clean deploy worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Production `rpb-backend`: `active`, recent 5-minute `ERROR` count: `0`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `PaymentProofReviewPage-D5rNIIzD.js`, `PaymentProofReviewPage-Cp9h0IKT.css`, `PaymentQuickPayPage-oda3e4Zi.js`, `PaymentPresentPage-D84Qh1R2.js`, `paymentPresentBridge-CDPRPkDm.js`, and `i18n-DC9r3MIA.js`: `200`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-1544-1d5395a0-paynow-proof-voice-amount-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260810-1544-1d5395a0-paynow-proof-voice-amount`, then reload nginx.

## Frontend-Only Follow-Up: Proof Voice Ref-Digit Guard

- Deployed commit: `0ac5e210 fix: guard paynow proof voice against ref digits`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment date: 2026-08-10.
- Scope: frontend-only; backend JAR, Flyway, App Gate permissions, and API contracts were not changed.
- Behavior:
  - 回单校验成功播报金额优先使用匹配候选单金额，其次使用服务端返回的应收金额。
  - OCR 金额只在 `checks.amount === 'match'` 时作为最后兜底，避免从 Ref 后缀如 `0019` 误读出“收款 19 元成功”。
  - 若前端候选列表没有及时匹配到成功单，也能通过服务端应收金额播报正确金额，例如 `SGD 0.10` 播为“收款 0.1 元成功”。
- Production frontend backup: `/opt/rpb/backups/20260810-1552-0ac5e210-paynow-proof-voice-ref-guard-frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260810-1552-0ac5e210-paynow-proof-voice-ref-guard`.
- Clean deploy worktree: `target/deploy-worktree-0ac5e210`.
- Validation:
  - Main worktree red test: `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test` failed before the fix because `resolveSuccessfulAmount` could still use OCR amount directly after candidate miss.
  - Main worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Main worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Clean deploy worktree `npm ci`: completed; npm audit reported the existing 3 high severity findings.
  - Clean deploy worktree `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`: passed, 5 tests with 0 failures and 0 errors.
  - Clean deploy worktree `npm run build`: passed (`vue-tsc --noEmit && vite build`).
  - Production `rpb-backend`: `active`, recent 5-minute `ERROR` count: `0`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `PaymentProofReviewPage-Cx8PQNZ7.js`, `PaymentProofReviewPage-4473QVaG.css`, `PaymentQuickPayPage-ByY0Gomp.js`, `PaymentPresentPage-C7DyjLX3.js`, `paymentPresentBridge-CDPRPkDm.js`, and `i18n-DC9r3MIA.js`: `200`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-1552-0ac5e210-paynow-proof-voice-ref-guard-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260810-1552-0ac5e210-paynow-proof-voice-ref-guard`, then reload nginx.
