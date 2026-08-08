# PayNow Payment Proof Review

## Summary

- Added the PayNow product line's Payment Proof Review flow for store staff mobile usage.
- Staff can open `/stores/:storeId/payments/proof-review`, capture or choose a PayNow bank receipt image, and scan it against active Quick Pay candidates in the same tenant/store.
- Auto-confirm only happens when the OCR result contains one RPB-generated Ref such as `PIT-202608-0021`, `QP-202608-0040-87D0`, or another valid `PREFIX-YYYYMM-SEQUENCE[-CHECK]` value and the amount matches the pending intent.
- Added `payment.proof.review` App Gate permission, migration backfill, new-staff default grants, and API endpoints for candidates and multipart proof scanning.
- Added a Tesseract-backed OCR adapter with receipt text parsing for Chinese OCBC-style receipts and English PayNow success screens.

## Verification

- `mvn -q "-Dtest=PaymentReferencePatternTest,PaymentReferenceGeneratorTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,JdbcPaymentProofReviewRepositoryTest,PaymentProofReviewControllerTest,PaymentProofReviewMigrationTest,PaymentMigrationTest,PaymentIntentServiceTest,PaymentIntentControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
- `npm run build`

## Production Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `09190935`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend artifact built from clean worktree `target/deploy-worktree-09190935`.
- Frontend artifact built from clean worktree `target/deploy-worktree-09190935`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-09190935.jar`
  - `/home/ubuntu/rpb-09190935-frontend.tgz`
- Backend backup: `/opt/rpb/backups/20260808-1114-09190935-paynow-proof-review/reservation-platform.jar`.
- Frontend backup: `/opt/rpb/backups/20260808-1114-09190935-paynow-proof-review/frontend`.
- Backend JAR SHA-256: `570EC2C51E796210FF75D74A5D551F0CFED2A777135FC041FAD803CE1111894B`.
- Flyway latest: `053|paynow payment proof review permissions|true`.
- `rpb-backend`: `active / running`, PID `4187696`.
- OCR runtime installed on production host:
  - `/usr/bin/tesseract`
  - `tesseract 5.3.4`
  - languages: `eng`, `chi_sim`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200` and loaded `/assets/index-B8R5LVlL.js`.
  - `https://booking.yumstone.sg/assets/index-B8R5LVlL.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentQuickPayPage-DeKdfmR8.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentPresentPage-VPj0iUIs.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentProofReviewPage-BthRpO9S.js` returned `200`.
  - `https://booking.yumstone.sg/assets/TenantAdminPaymentRecordsPage-fPeRA0jh.js` returned `200`.
  - `https://booking.yumstone.sg/assets/api-C-7tmKP9.js` returned `200`.
  - `https://booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments` returned `200`.
  - `https://booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `https://booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments/proof-review` returned `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records` returned `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me` returned `401`.
  - Unauthenticated `GET /api/v1/stores/20000000-0000-0000-0000-000000000983/payments/proof-review/candidates` returned `403`, confirming the proof-review API is protected.
  - Host-prefix proof-review route returned `200`: `https://20000000.booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments/proof-review`.
- Backend startup logs show Flyway applied V053 and `ReservationPlatformApplication` started.

## 2026-08-08 Scan Accuracy Patch

- Reproduced the failed mobile photo sample against the production OCR runtime.
- Root cause:
  - The upload flow encouraged taking a photo of the browser/page, so address bar, RPB controls, bottom nav, tilt, and screen moire entered the image.
  - Tesseract `--psm 6` read the amount but misread the Ref check code in the sample as `QP-202608-0013-2K7`.
  - Tesseract `--psm 11` read the correct check code but inserted a space inside the serial: `QP-202608-001 3-2KZ6`.
- Patch:
  - Payment Proof Review now includes a camera scanner that opens the environment camera, captures the centered scan frame, and submits frames automatically until a receipt is matched or needs review.
  - File upload remains available as fallback.
  - OCR extraction now retries multiple page segmentation modes and chooses the best parsed fields.
  - Ref parsing accepts OCR spaces inside a system Ref without joining unrelated amount or page text.

### Scan Accuracy Patch Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `7af0bf9d`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend artifact built from clean worktree `target/deploy-worktree-7af0bf9d`.
- Frontend artifact built from clean worktree `target/deploy-worktree-7af0bf9d`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-7af0bf9d.jar`
  - `/home/ubuntu/rpb-7af0bf9d-frontend.tgz`
- Production backup: `/opt/rpb/backups/20260808-1156-7af0bf9d-paynow-proof-scan-accuracy`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260808-1156-7af0bf9d-paynow-proof-scan-accuracy`.
- Backend JAR SHA-256: `B908E7AAF4BEE9C254C21C4AFE5785DDA18E54DDB82A6D0C18C1914269935767`.
- `rpb-backend`: `active / running`, PID `6393`.
- Flyway current schema: `053`; no migration necessary.
- OCR runtime confirmed:
  - `tesseract 5.3.4`
  - languages: `eng`, `chi_sim`, `osd`
- Verification:
  - `mvn -q "-Dtest=PaymentReferencePatternTest,PaymentReferenceGeneratorTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,PaymentProofReviewControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm ci`
  - `npm run build`
  - `mvn -q -DskipTests package`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200` and loaded `/assets/index-DSIu7fNL.js`.
  - `https://booking.yumstone.sg/assets/PaymentQuickPayPage-DvwNsFO0.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentPresentPage-BatqzUOB.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentProofReviewPage-By4r_Ef4.js` returned `200`.
  - `https://booking.yumstone.sg/assets/TenantAdminPaymentRecordsPage-BXjHHGeB.js` returned `200`.
  - `https://booking.yumstone.sg/assets/api-DlUiuggT.js` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/proof-review` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records` returned `200`.
  - `/api/v1/auth/me` returned `401`.
  - unauthenticated proof candidates endpoint returned `403`.
  - Host-prefix proof-review route returned `200`.

## 2026-08-08 Upload And Dot-Separator OCR Patch

- Reproduced the new samples against production Tesseract:
  - Direct bank screenshot sample reads `QP-202608-0013-2KZ6` and `1.00 SGD`.
  - Browser/photo sample can read the same Ref as `QP-202608-0013.2Kz6` or `QP-202608.0013.2Kz6`.
- Patch:
  - Ref parser now treats `.` as an OCR separator only inside a valid system Ref structure, normalizing it back to `-`.
  - Payment Proof Review now has separate actions for camera capture and photo upload, so staff can explicitly upload a bank receipt screenshot from the phone album.

### Upload And Dot-Separator OCR Patch Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `86d302f7`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend artifact built from clean worktree `target/deploy-worktree-86d302f7`.
- Frontend artifact built from clean worktree `target/deploy-worktree-86d302f7`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-86d302f7.jar`
  - `/home/ubuntu/rpb-86d302f7-frontend.tgz`
- Production backup: `/opt/rpb/backups/20260808-1239-86d302f7-paynow-proof-upload-dot-ref`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260808-1239-86d302f7-paynow-proof-upload-dot-ref`.
- Backend JAR SHA-256: `AE5D6CFC183E1DFB10A5C1731922DC9BF4DC2F3DAE55F4ABAC4CE24CB7A8FE1D`.
- `rpb-backend`: `active / running`, PID `18320`.
- OCR runtime confirmed:
  - `tesseract 5.3.4`
  - languages: `eng`, `chi_sim`, `osd`
- Verification:
  - `mvn -q "-Dtest=TesseractPaymentProofOcrAdapterTest" test`
  - `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`
  - `mvn -q "-Dtest=PaymentReferencePatternTest,PaymentReferenceGeneratorTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,PaymentProofReviewControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm ci`
  - `npm run build`
  - `mvn -q -DskipTests package`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentQuickPayPage-DRDQb2oP.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentPresentPage-DB30Z1Kk.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentProofReviewPage-BKzYuXt6.js` returned `200`.
  - `https://booking.yumstone.sg/assets/PaymentProofReviewPage-CsLbw1mw.css` returned `200`.
  - `https://booking.yumstone.sg/assets/TenantAdminPaymentRecordsPage-Caxa8AMM.js` returned `200`.
  - `https://booking.yumstone.sg/assets/api-QY9D9Ijj.js` returned `200`.
  - `https://booking.yumstone.sg/assets/index-BJ2xox1m.js` returned `200`.
  - `https://booking.yumstone.sg/assets/i18n-B4EanLIg.js` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/proof-review` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records` returned `200`.
  - `/api/v1/auth/me` returned `401`.
  - unauthenticated proof candidates endpoint returned `403`.
  - `GET /api/v1/stores/20000000-0000-0000-0000-000000000983/payments/proof-review/scan` returned `405`.
  - Live frontend bundle contains `photoInputRef`, `albumInputRef`, `拍照识别`, `上传照片识别`, `Take photo`, and `Upload photo`.

## 2026-08-08 OCR-Safe Reference Format Patch

- New PayNow payment references use compact separator-free format such as `QP2026080013AAHP`.
- The 4-character check segment uses OCR-safe letters from `ACDEFGHJKMNPQRTVWXY`.
- The check segment excludes `O`, `I`, `L`, `B`, `S`, `Z`, and digits to reduce screen-to-screen OCR confusion.
- Compact OCR candidates are accepted only when their `yyyyMM`, four-digit sequence, and CRC-derived check segment are valid; invalid compact-shaped transaction IDs are skipped while extraction continues.
- Existing separated references remain accepted by Payment Proof Review through compact and separated lookup variants, with automatic matching limited to one unique active candidate.
- Requested or automatically allocated display numbers above `9999` now return the stable `REQUEST_INVALID` business error before reference generation.
- No database migration, permission change, or PayNow QR payload schema change is required.

### OCR-Safe Reference Format Patch Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `bb489acc`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend artifact built from clean worktree `target/deploy-worktree-bb489acc`.
- Frontend artifact built from clean worktree `target/deploy-worktree-bb489acc`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-bb489acc.jar`
  - `/home/ubuntu/rpb-bb489acc-frontend.tgz`
- Production backup: `/opt/rpb/backups/20260808-1442-bb489acc-paynow-ocr-safe-reference`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260808-1442-bb489acc-paynow-ocr-safe-reference`.
- Backend JAR SHA-256: `2CE773221286833F935B679A6435C17BB88FA1222B8A7B7C7B4C9E729E4687C4`.
- `rpb-backend`: `active / running`, PID `48978`.
- OCR runtime confirmed:
  - `tesseract 5.3.4`
  - languages: `eng`, `chi_sim`, `osd`
- Verification:
  - `mvn -q "-Dtest=PaymentReferenceGeneratorTest,PaymentReferencePatternTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,PaymentIntentServiceTest,PaymentProofReviewControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm run build`
  - `mvn -q -DskipTests package`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/proof-review` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records` returned `200`.
  - `/api/v1/auth/me` returned `401`.
  - unauthenticated proof candidates endpoint returned `403`.
  - `GET /api/v1/stores/20000000-0000-0000-0000-000000000983/payments/proof-review/scan` returned `405`.
  - Current QuickPay, Present, Proof Review, Records, API, index, and i18n assets returned `200`.

## 2026-08-08 Daily OCR-Safe Reference Patch

- New Quick Payment PayNow references now use `prefix + yyyyMMdd + 4-digit display number + 4-letter OCR-safe check`, for example `QP202608080013YGDN`.
- Present display Ref now shows the customer-facing `paymentReference`, not the internal `PIT-yyyyMM-sequence` intent number.
- Payment Proof Review continues to accept already-issued monthly compact references such as `QP2026080015AEDD` and separated legacy variants for active candidate matching.
- No database migration, permission change, or environment variable change is required.

### Daily OCR-Safe Reference Patch Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `2237df55`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend artifact built from clean worktree `target/deploy-worktree-2237df55`.
- Frontend artifact built from clean worktree `target/deploy-worktree-2237df55`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-2237df55.jar`
  - `/home/ubuntu/rpb-2237df55-frontend.tgz`
- Production backup: `/opt/rpb/backups/20260808-1503-2237df55-paynow-daily-reference`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260808-1503-2237df55-paynow-daily-reference`.
- Backend JAR SHA-256: `8984cf255ad4fd60886aa3deb17d5daf1a0a0b03f65c5195129c6d9865564d39`.
- `rpb-backend`: `active / running`, PID `54662`; recent deployment log `ERROR` count: `0`.
- Verification:
  - `mvn -q "-Dtest=PaymentReferenceGeneratorTest,PaymentReferencePatternTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,PaymentIntentServiceTest,PaymentProofReviewControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm run build`
  - `mvn -q -DskipTests package`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/proof-review` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records` returned `200`.
  - `/api/v1/auth/me` returned `401`.
  - unauthenticated proof candidates endpoint returned `403`.
  - `GET /api/v1/stores/20000000-0000-0000-0000-000000000983/payments/proof-review/scan` returned `405`.
  - Current QuickPay, Present, Proof Review, Records, paymentPresentBridge, API, index, and i18n assets returned `200`.
  - Live Present bundle contains `paymentReference` and no longer contains the old `payload.intentNo` Ref binding.
- Rollback: restore `/opt/rpb/app/reservation-platform.jar` and `/opt/rpb/frontend` from `/opt/rpb/backups/20260808-1503-2237df55-paynow-daily-reference`, then restart `rpb-backend` and reload nginx.

## 2026-08-08 Already Confirmed Proof Review Patch

- Root cause confirmed from production data: receipt `QP202608080017GQVQ` was OCR-read successfully, matched amount `1.00`, and had already moved to `paid / paid`; subsequent scans no longer had an active pending candidate and therefore appeared as `no_match`.
- Payment Proof Review now performs a historical same-store, same-business-day, same-terminal lookup after active candidate matching fails.
- If the extracted Ref and amount match a unique already-paid Quick Payment, the scan result returns `already_confirmed` instead of `no_match`.
- The mobile Proof Review UI displays `已确认` / `Already confirmed` and allows the same close action as an auto-confirmed proof.
- No database migration, permission change, or environment variable change is required.

### Already Confirmed Proof Review Patch Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `4d3ed496`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Backend artifact built from clean worktree `target/deploy-worktree-4d3ed496`.
- Frontend artifact built from clean worktree `target/deploy-worktree-4d3ed496`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-4d3ed496.jar`
  - `/home/ubuntu/rpb-4d3ed496-frontend.tgz`
- Production backup: `/opt/rpb/backups/20260808-1523-4d3ed496-paynow-already-confirmed-proof`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260808-1523-4d3ed496-paynow-already-confirmed-proof`.
- Backend JAR SHA-256: `3ee0c6e6ef95ca82c3632ffd6f63307d68d60b68e8f4d18844970c71d482ee07`.
- `rpb-backend`: `active / running`, PID `60301`; recent deployment log `ERROR` count: `0`.
- Verification:
  - `mvn -q "-Dtest=PaymentReferenceGeneratorTest,PaymentReferencePatternTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,PaymentIntentServiceTest,PaymentProofReviewControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm run build`
  - `mvn -q -DskipTests package`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `/stores/20000000-0000-0000-0000-000000000983/payments/proof-review` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records` returned `200`.
  - `/api/v1/auth/me` returned `401`.
  - unauthenticated proof candidates endpoint returned `403`.
  - `GET /api/v1/stores/20000000-0000-0000-0000-000000000983/payments/proof-review/scan` returned `405`.
  - Current Proof Review, QuickPay, Present, Records, API, and i18n assets returned `200`.
  - Live Proof Review bundle contains `already_confirmed`.
- Rollback: restore `/opt/rpb/app/reservation-platform.jar` and `/opt/rpb/frontend` from `/opt/rpb/backups/20260808-1523-4d3ed496-paynow-already-confirmed-proof`, then restart `rpb-backend` and reload nginx.

## 2026-08-08 Proof Review Popup Label Patch

- Quick Payment now opens Payment Proof Review in a named popup window instead of navigating away from the terminal page.
- The Chinese label for Payment Proof Review is now `回单校验` in the Quick Payment entry, the Proof Review top status, and the Proof Review page title.
- English labels remain `Payment Proof Review`.
- No backend, API, database, permission, or environment variable change is required.

### Proof Review Popup Label Patch Deployment

- Deployment date: 2026-08-08.
- Deployed commit: `ada31ab1`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Frontend artifact built from clean worktree `target/deploy-worktree-ada31ab1`.
- Uploaded artifact: `/home/ubuntu/rpb-ada31ab1-frontend.tgz`.
- Production backup: `/opt/rpb/backups/20260808-1612-ada31ab1-paynow-proof-review-popup`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260808-1612-ada31ab1-paynow-proof-review-popup`.
- Backend JAR was not changed and `rpb-backend` was not restarted.
- Verification:
  - `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm run build`
  - `git diff --check`
- Production smoke:
  - `https://booking.yumstone.sg/login` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments` returned `200`.
  - `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review` returned `200`.
  - `/api/v1/auth/me` returned `401`.
  - Current QuickPay, Proof Review, and i18n assets returned `200`.
  - Live QuickPay bundle contains `rpb-paynow-proof-review` and `popup=yes,width=520,height=900`.
  - Live i18n bundle contains `回单校验`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260808-1612-ada31ab1-paynow-proof-review-popup/frontend` or switch back to `/opt/rpb/frontend.previous-20260808-1612-ada31ab1-paynow-proof-review-popup`, then reload nginx.
