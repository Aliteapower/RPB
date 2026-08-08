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
