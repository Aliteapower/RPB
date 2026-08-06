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
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(router)
            .contains("path: '/stores/:storeId/admin/payment/settings'")
            .contains("name: 'tenant-admin-payment-settings'")
            .contains("path: '/stores/:storeId/payments'")
            .contains("name: 'payment-quick-pay'")
            .contains("path: '/stores/:storeId/payments/display/:sessionNo'")
            .contains("name: 'payment-display'");

        assertThat(tenantNav)
            .contains("/admin/payment/settings")
            .contains("nav.tenant.paymentSettings");
        assertThat(staffHome)
            .contains("payment.intent.create")
            .contains("staffHome.actions.quickPay.label")
            .contains("name: 'payment-quick-pay'");
        assertThat(bottomNav)
            .contains("'payment'")
            .contains("nav.staff.payment")
            .contains("payment-quick-pay");
        assertThat(zh)
            .contains("paymentSettings: '收款设置'")
            .contains("payment: '收款'")
            .contains("quickPay");
        assertThat(en)
            .contains("paymentSettings: 'Payment settings'")
            .contains("payment: 'Pay'")
            .contains("quickPay");
    }

    @Test
    void payNowPagesUseRpbNativePaymentApiAndQrComponent() throws Exception {
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "paymentApi.ts"));
        String types = FrontendSourceSupport.readString(Path.of("src", "types", "payment.ts"));
        String settings = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentSettingsPage.vue"));
        String quickPay = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentQuickPayPage.vue"));
        String display = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentDisplayPage.vue"));

        assertThat(api)
            .contains("/tenant-admin/payment/profile")
            .contains("/payments/intents")
            .contains("/sessions/")
            .contains("credentials: 'include'");
        assertThat(types)
            .contains("PaymentProfileMutation")
            .contains("PaymentIntentCreateRequest")
            .contains("PaymentSession");
        assertThat(settings)
            .contains("getPaymentProfile")
            .contains("updatePaymentProfile")
            .contains("PAYMENT_PROFILE_NOT_FOUND")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("TenantAdminNav")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(quickPay)
            .contains("createPaymentIntent")
            .contains("sourceType: 'quick_pay'")
            .contains("DownloadableQrCode")
            .contains("active-tab=\"payment\"");
        assertThat(display)
            .contains("getPaymentSession")
            .contains("DownloadableQrCode")
            .contains("active-tab=\"payment\"");
    }
}
