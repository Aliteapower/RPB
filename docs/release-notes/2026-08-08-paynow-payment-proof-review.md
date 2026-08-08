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
