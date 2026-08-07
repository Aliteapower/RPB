package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PayNowPaymentUiAcceptanceValidationTest {

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
        String display = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentDisplayPage.vue"));
        String present = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentPresentPage.vue"));
        String presentBridge = FrontendSourceSupport.readString(Path.of("src", "utils", "paymentPresentBridge.ts"));
        String staffRepository = FrontendSourceSupport.readString(Path.of("src", "main", "java", "com", "rpb", "reservation", "tenantadmin", "persistence", "TenantAdminStaffRepository.java"));

        assertThat(api)
            .contains("/tenant-admin/payment/profile")
            .contains("/payments/intents")
            .contains("/payments/business-day")
            .contains("/quick-pay-records")
            .contains("/sessions/")
            .contains("getPaymentBusinessDay")
            .contains("openPaymentBusinessDay")
            .contains("credentials: 'include'");
        assertThat(types)
            .contains("PaymentProfileMutation")
            .contains("PaymentIntentCreateRequest")
            .contains("PaymentBusinessDayResponse")
            .contains("PaymentBusinessDayStatus")
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
            .contains("businessDayOpen")
            .contains("openBusinessDayToday")
            .contains("response.session.businessDate")
            .contains("showOpenTodayButton")
            .contains("sourceType: 'quick_pay'")
            .contains("appendAmountToken")
            .contains("backspaceAmount")
            .contains("clearAmount")
            .contains("savePresetEditor")
            .contains("openPresentSettingsEditor")
            .contains("savePresentSettingsEditor")
            .contains("openPresentWindow")
            .contains("publishPaymentPresentPayload")
            .contains("publishPaymentPresentSettings")
            .contains("readPaymentPresentSettings")
            .contains("MAX_PRESENT_PAYMENTS")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("active-tab=\"payment\"");
        assertThat(staffRepository)
            .contains("\"payment.intent.view\"")
            .contains("\"payment.intent.create\"");
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
            .contains("Waiting for new payment")
            .contains("clearActivePayload")
            .contains("present-grid")
            .contains("DownloadableQrCode");
        assertThat(presentBridge)
            .contains("BroadcastChannel")
            .contains("localStorage")
            .contains("rpb-payment-present")
            .contains("PaymentPresentSettings")
            .contains("MAX_PRESENT_PAYMENTS")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentSettings")
            .contains("savePaymentPresentSettings")
            .contains("publishPaymentPresentSettings")
            .contains("120");
    }
}
