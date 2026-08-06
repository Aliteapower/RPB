# Troubleshooting Report

## Symptom

Store staff Quick Pay at `/stores/{storeId}/payments` displayed `收款创建失败，请稍后重试` when creating a PayNow payment.

## Evidence

- Screenshot showed the staff Quick Pay page after pressing `生成收款码`.
- Nginx access logs around `2026-08-06 10:49-10:50 +0800` showed `POST /api/v1/stores/20000000-0000-0000-0000-000000000983/payments/intents` returning `403`.
- Backend application logs had no unhandled payment exception around the failure window.
- Previous V048 troubleshooting explicitly noted that existing ordinary staff accounts were not granted PayNow quick-pay permissions.

## Root Cause

The PayNow V048 permission backfill granted PayNow permissions to existing tenant admin accounts only. Existing store staff and store manager accounts could still open the staff payment UI through product-line navigation, but App Gate denied `POST /payments/intents` because they lacked `payment.intent.create`. Quick Pay also treated the App Gate `PERMISSION_DENIED` envelope as an unknown payment error, causing the generic red banner.

## Affected Files

- `src/main/resources/db/migration/V049__paynow_existing_store_staff_permissions.sql`
- `src/main/java/com/rpb/reservation/tenantadmin/persistence/TenantAdminStaffRepository.java`
- `src/pages/PaymentQuickPayPage.vue`
- `src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`

## Fix Plan

1. Add V049 to grant existing active `store_staff` and `store_manager` accounts:
   - `payment.intent.view`
   - `payment.intent.create`
2. Add the same two permissions to future staff accounts created by tenant admin staff management.
3. Reuse App Gate error-message mapping on Quick Pay so permission denials show a clear permission message.

## Verification

- PASS: `mvn -q "-Dtest=PaymentMigrationTest#grantsPaymentIntentPermissionsToExistingStoreStaff" test`
- PASS: `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest#payNowPagesUseRpbNativePaymentApiAndQrComponent" test`
- PASS: `mvn -q "-Dtest=PaymentMigrationTest,PayNowPaymentUiAcceptanceValidationTest,PaymentIntentControllerTest,PaymentIntentServiceTest" test`
- PASS: `npm run build`

## Remaining Risk

V049 grants quick-pay creation to active staff and store manager accounts globally at account level. Store-level access and App Gate store app enablement still constrain which stores they can use.
