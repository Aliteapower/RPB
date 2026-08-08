package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PayNowPaymentUiAcceptanceValidationTest {

    @Test
    void quickPayCreationUsesPaymentReferenceGenerator() throws Exception {
        assertThat(Files.readString(Path.of("src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java")))
            .contains("PaymentReferenceGenerator.generate");
    }

    @Test
    void tenantAdminStaffQuickPayAndDisplayEntriesAreWired() throws Exception {
        String router = FrontendSourceSupport.readString(Path.of("src", "router", "index.ts"));
        String tenantNav = FrontendSourceSupport.readString(Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue"));
        String staffHome = FrontendSourceSupport.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"));
        String bottomNav = FrontendSourceSupport.readString(Path.of("src", "components", "staff", "staffBottomNavItems.ts"));
        String bottomNavComponent = FrontendSourceSupport.readString(Path.of("src", "components", "staff", "StaffBottomNav.vue"));
        String quickPayPopup = FrontendSourceSupport.readString(Path.of("src", "utils", "paymentQuickPayPopup.ts"));
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(router)
            .contains("path: '/stores/:storeId/admin/payment/settings'")
            .contains("name: 'tenant-admin-payment-settings'")
            .contains("path: '/stores/:storeId/admin/payment/records'")
            .contains("name: 'tenant-admin-payment-records'")
            .contains("path: '/stores/:storeId/admin/payment/i18n-catalog'")
            .contains("name: 'tenant-admin-payment-i18n-catalog'")
            .contains("path: '/stores/:storeId/payments'")
            .contains("name: 'payment-quick-pay'")
            .contains("path: '/stores/:storeId/payments/proof-review'")
            .contains("name: 'payment-proof-review'")
            .contains("path: '/stores/:storeId/payments/present/:terminalCode'")
            .contains("name: 'payment-present'")
            .contains("path: '/stores/:storeId/payments/display/:sessionNo'")
            .contains("name: 'payment-display'");

        assertThat(tenantNav)
            .contains("useStoreVisibleApps")
            .contains("hasPaymentProductLine")
            .contains("hasReservationQueueProductLine")
            .contains("/admin/payment/settings")
            .contains("/admin/payment/records")
            .contains("/admin/payment/i18n-catalog")
            .contains("/admin/reservation-queue/i18n-catalog")
            .contains("nav.tenant.paymentProductLine")
            .contains("nav.tenant.reservationQueueProductLine")
            .contains("nav.tenant.paymentSettings")
            .contains("nav.tenant.paymentRecords")
            .contains("nav.tenant.paymentI18nCatalog")
            .contains("nav.tenant.reservationQueueI18nCatalog");
        assertThat(staffHome)
            .contains("useStoreVisibleApps")
            .contains("paymentEntry")
            .contains("hasReservationQueue")
            .contains("v-if=\"hasReservationQueue\"")
            .contains("staffHome.hints.paymentOnly")
            .contains("openMode: 'popup'")
            .contains("openQuickPaymentPopup(href)")
            .doesNotContain("router.replace(paymentQuickPayRoute.value)")
            .contains("payment.intent.create")
            .contains("staffHome.actions.quickPay.label")
            .contains("name: 'payment-quick-pay'");
        assertThat(bottomNav)
            .contains("'payment'")
            .contains("appKey: 'payment'")
            .contains("appKey: 'reservation_queue'")
            .contains("nav.staff.payment")
            .contains("payment-quick-pay")
            .contains("openMode: 'popup'");
        assertThat(bottomNavComponent)
            .contains("useStoreVisibleApps")
            .contains("hasVisibleApp(item.appKey)")
            .contains("openQuickPaymentPopup(href)")
            .contains(":target=\"item.openMode === 'popup' ? '_blank' : undefined\"")
            .contains("--staff-nav-items");
        assertThat(quickPayPopup)
            .contains("QUICK_PAYMENT_POPUP_TARGET")
            .contains("QUICK_PAYMENT_POPUP_FEATURES")
            .contains("popup=yes")
            .contains("openQuickPaymentPopup");
        assertThat(zh)
            .contains("paymentProductLine: '收款 / PayNow'")
            .contains("paymentSettings: '基础设置'")
            .contains("paymentRecords: 'Quick Payment Records'")
            .contains("paymentI18nCatalog: 'PayNow 国际化字典'")
            .contains("reservationQueueProductLine: '预约排队叫号'")
            .contains("reservationQueueI18nCatalog: '预约排队国际化字典'")
            .contains("payment: '收款'")
            .contains("paymentOnly")
            .contains("quickPay");
        assertThat(en)
            .contains("paymentProductLine: 'Pay / PayNow'")
            .contains("paymentSettings: 'Settings'")
            .contains("paymentRecords: 'Quick Payment Records'")
            .contains("paymentI18nCatalog: 'PayNow I18n'")
            .contains("reservationQueueProductLine: 'Reservation Queue'")
            .contains("reservationQueueI18nCatalog: 'Reservation Queue I18n'")
            .contains("payment: 'Pay'")
            .contains("paymentOnly")
            .contains("quickPay");
    }

    @Test
    void payNowPagesUseRpbNativePaymentApiAndQrComponent() throws Exception {
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "paymentApi.ts"));
        String types = FrontendSourceSupport.readString(Path.of("src", "types", "payment.ts"));
        String settings = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentSettingsPage.vue"));
        String records = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentRecordsPage.vue"));
        String quickPay = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentQuickPayPage.vue"));
        String proofReview = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentProofReviewPage.vue"));
        String display = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentDisplayPage.vue"));
        String present = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentPresentPage.vue"));
        String presentBridge = FrontendSourceSupport.readString(Path.of("src", "utils", "paymentPresentBridge.ts"));
        String generatedZh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-zh-CN.ts"));
        String generatedEn = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-en-SG.ts"));
        String staffRepository = FrontendSourceSupport.readString(Path.of("src", "main", "java", "com", "rpb", "reservation", "tenantadmin", "persistence", "TenantAdminStaffRepository.java"));

        assertThat(api)
            .contains("/tenant-admin/payment/profile")
            .contains("/payments/intents")
            .contains("/payments/business-day")
            .contains("/payments/proof-review")
            .contains("/quick-pay-records")
            .contains("/sessions/")
            .contains("getPaymentProofCandidates")
            .contains("scanPaymentProof")
            .contains("requestMultipart")
            .contains("getPaymentBusinessDay")
            .contains("openPaymentBusinessDay")
            .contains("endPaymentBusinessDay")
            .contains("`${businessDayEndpoint(storeId)}/end-day`")
            .contains("credentials: 'include'");
        assertThat(types)
            .contains("PaymentProfileMutation")
            .contains("PaymentIntentCreateRequest")
            .contains("PaymentBusinessDayResponse")
            .contains("PaymentBusinessDayStatus")
            .contains("PaymentProofScanResponse")
            .contains("PaymentProofCandidate")
            .contains("PaymentSession");
        assertThat(settings)
            .contains("getPaymentProfile")
            .contains("updatePaymentProfile")
            .contains("PAYMENT_PROFILE_NOT_FOUND")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("TenantAdminNav")
            .contains("tenant-admin-payment-records")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(records)
            .contains("getQuickPayRecords")
            .contains("Quick Payment Records")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("TenantAdminNav")
            .contains("tenant-admin-payment-settings")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(quickPay)
            .contains("createPaymentIntent")
            .contains("getPaymentBusinessDay")
            .contains("openPaymentBusinessDay")
            .contains("endPaymentBusinessDay")
            .contains("businessDayOpen")
            .contains("openBusinessDayToday")
            .contains("endBusinessDay")
            .contains("response.session.businessDate")
            .contains("showOpenTodayButton")
            .contains("const showOpenTodayButton = computed(() => !businessDayOpen.value)")
            .contains("showEndDayButton")
            .doesNotContain("displayedBusinessDate.value !== currentBusinessDate.value")
            .contains("sourceType: 'quick_pay'")
            .contains("appendAmountToken")
            .contains("backspaceAmount")
            .contains("clearAmount")
            .contains("savePresetEditor")
            .contains("openPresentSettingsEditor")
            .contains("savePresentSettingsEditor")
            .contains("presentSettingsRecentExpiredHoldSeconds")
            .contains("recentExpiredHoldSeconds")
            .contains("openPresentWindow")
            .contains("publishPaymentPresentPayload")
            .contains("publishPaymentPresentSettings")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentSettings")
            .contains("isPaymentPresentPayloadActive")
            .contains("activePresentPayloadCount")
            .contains("presentCapacityFull")
            .contains("noticeText.value = gt('generated.payment-quick-pay.055')")
            .contains("Number(presentSettingsRecentExpiredHoldSeconds.value)")
            .contains("const presentMaxPaymentOptions = [1, 2, 3, 4, 5, 6] as const")
            .contains("MAX_PRESENT_PAYMENTS")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("payment-proof-review")
            .contains("active-tab=\"payment\"");
        assertThat(proofReview)
            .contains("getPaymentProofCandidates")
            .contains("scanPaymentProof")
            .contains("navigator.mediaDevices.getUserMedia")
            .contains("startScanner")
            .contains("captureFrameBlob")
            .contains("canvas.toBlob")
            .contains("setInterval")
            .contains("photoInputRef")
            .contains("albumInputRef")
            .contains("openPhotoCapture")
            .contains("openAlbumPicker")
            .contains("generated.payment-proof-review.006")
            .contains("accept=\"image/png,image/jpeg,image/webp\"")
            .contains("capture=\"environment\"")
            .contains("generated.payment-proof-review.037")
            .contains("generated.payment-proof-review.038")
            .contains("auto_confirmed")
            .contains("generated.payment-proof-review.028")
            .contains("active-tab=\"payment\"")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(staffRepository)
            .contains("\"payment.intent.view\"")
            .contains("\"payment.intent.create\"")
            .contains("\"payment.proof.review\"");
        assertThat(display)
            .contains("getPaymentSession")
            .contains("DownloadableQrCode")
            .contains("active-tab=\"payment\"");
        assertThat(present)
            .contains("activePayloads")
            .contains("subscribePaymentPresentPayloads")
            .contains("subscribePaymentPresentSettings")
            .contains("PAYMENT_PRESENT_TTL_SECONDS")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentRecent")
            .contains("Waiting for new payment")
            .contains("clearActivePayload")
            .contains("present-grid")
            .contains("DownloadableQrCode")
            .contains(":show-download=\"false\"")
            .doesNotContain(":download-label=\"gt('generated.payment-present.009')\"");
        assertThat(presentBridge)
            .contains("BroadcastChannel")
            .contains("localStorage")
            .contains("rpb-payment-present")
            .contains("PaymentPresentSettings")
            .contains("export const MAX_PRESENT_PAYMENTS = 6")
            .contains("export const DEFAULT_RECENT_EXPIRED_HOLD_SECONDS = 20")
            .contains("export type PresentMaxPayments = 1 | 2 | 3 | 4 | 5 | 6")
            .contains("recentExpiredHoldSeconds: DEFAULT_RECENT_EXPIRED_HOLD_SECONDS")
            .contains("recentVisibleUntilMs")
            .contains("prunePaymentPresentRecent")
            .contains("value === 5 || value === 6")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentSettings")
            .contains("savePaymentPresentSettings")
            .contains("publishPaymentPresentSettings")
            .contains("120");
        assertThat(generatedZh)
            .contains("\"generated.payment-quick-pay.055\": \"等顾客支付\"")
            .contains("\"generated.payment-quick-pay.056\": \"过期后保留秒数\"")
            .contains("\"generated.payment-proof-review.006\": \"Payment Proof Review\"");
        assertThat(generatedEn)
            .contains("\"generated.payment-quick-pay.055\": \"Waiting for customer payment\"")
            .contains("\"generated.payment-quick-pay.056\": \"Keep after expiry (seconds)\"")
            .contains("\"generated.payment-proof-review.006\": \"Payment Proof Review\"");
    }
}
