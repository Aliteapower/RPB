package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class I18nCatalogAdminUiValidationTest {
    @Test
    void platformAndTenantAdminExposeControlledI18nCatalogPages() throws Exception {
        String router = FrontendSourceSupport.readString(Path.of("src", "router", "index.ts"));
        String platformNav = FrontendSourceSupport.readString(Path.of("src", "components", "platform", "PlatformAdminNav.vue"));
        String tenantNav = FrontendSourceSupport.readString(Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue"));
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "i18nCatalogApi.ts"));
        String types = FrontendSourceSupport.readString(Path.of("src", "types", "i18nCatalog.ts"));
        String displayLabels = FrontendSourceSupport.readString(Path.of("src", "utils", "i18nCatalogDisplayLabels.ts"));
        String platformPage = FrontendSourceSupport.readString(Path.of("src", "pages", "PlatformI18nCatalogPage.vue"));
        String tenantPage = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminI18nCatalogPage.vue"));
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(router)
            .contains("PlatformI18nCatalogPage")
            .contains("TenantAdminI18nCatalogPage")
            .contains("path: '/platform/i18n/catalog'")
            .contains("name: 'platform-i18n-catalog'")
            .contains("path: '/stores/:storeId/admin/payment/i18n-catalog'")
            .contains("name: 'tenant-admin-payment-i18n-catalog'")
            .contains("path: '/stores/:storeId/admin/reservation-queue/i18n-catalog'")
            .contains("name: 'tenant-admin-reservation-queue-i18n-catalog'")
            .contains("path: '/stores/:storeId/admin/i18n-catalog'")
            .contains("name: 'tenant-admin-i18n-catalog-legacy'");

        assertThat(platformNav)
            .contains("/platform/i18n/catalog")
            .contains("nav.platform.i18nCatalog");
        assertThat(tenantNav)
            .contains("/admin/payment/i18n-catalog")
            .contains("/admin/reservation-queue/i18n-catalog")
            .contains("nav.tenant.paymentI18nCatalog")
            .contains("nav.tenant.reservationQueueI18nCatalog")
            .doesNotContain("labelKey: 'nav.tenant.i18nCatalog'");

        assertThat(api)
            .contains("getPlatformI18nCatalog")
            .contains("updatePlatformI18nCatalog")
            .contains("getTenantAdminI18nCatalog")
            .contains("updateTenantAdminI18nCatalog")
            .contains("I18nCatalogProductLineScope")
            .contains("productLine")
            .contains("tenantEndpoint(storeId, productLine)")
            .contains("/api/v1/platform/i18n/catalog")
            .contains("/tenant-admin/i18n/catalog");

        assertThat(types)
            .contains("I18nCatalogScopeLevel")
            .contains("I18nCatalogProductLineScope")
            .contains("effectiveSource")
            .contains("platformMessage")
            .contains("tenantOverride")
            .contains("storeOverride");

        assertThat(platformPage)
            .contains("platform.i18nCatalog.page.note")
            .contains("i18nCatalogNamespaceLabel(namespace, t)")
            .contains("i18nCatalogEntryMetaLabel(entry.source.key, t)")
            .contains("locale.platformMessage")
            .contains("updatePlatformI18nCatalog")
            .doesNotContain("generated.")
            .doesNotContain("{{ namespace }}")
            .doesNotContain("entry.source.key.namespace }} /");
        assertThat(tenantPage)
            .contains("tenant.i18nCatalog.page.note")
            .contains("productLineScope")
            .contains("tenant-admin-payment-i18n-catalog")
            .contains("tenant-admin-reservation-queue-i18n-catalog")
            .contains("getTenantAdminI18nCatalog(storeId.value, productLineScope.value)")
            .contains("productLineScope.value")
            .contains("i18nCatalogNamespaceLabel(namespace, t)")
            .contains("i18nCatalogEntryMetaLabel(entry.source.key, t)")
            .contains("scopeLevel")
            .contains("locale.storeOverride")
            .contains("locale.tenantOverride")
            .contains("clear: true")
            .doesNotContain("generated.")
            .doesNotContain("{{ namespace }}")
            .doesNotContain("entry.source.key.namespace }} /");
        assertThat(displayLabels)
            .contains("i18nCatalog.namespaces.payment")
            .contains("i18nCatalog.namespaces.call_screen")
            .contains("i18nCatalog.categories.display")
            .contains("i18nCatalog.textKinds.prompt");

        assertThat(zh)
            .contains("i18nCatalog: '国际化字典'")
            .contains("paymentI18nCatalog: 'PayNow 国际化字典'")
            .contains("reservationQueueI18nCatalog: '预约排队国际化字典'")
            .contains("平台默认业务文案")
            .contains("门店覆盖、租户覆盖、平台默认、前端兜底")
            .contains("payment: 'PayNow 收款'")
            .contains("quick_pay: '快速收款'")
            .contains("call_screen: '叫号大屏'")
            .contains("reservation_share: '预约分享'")
            .contains("prompt: '提示文案'");
        assertThat(en)
            .contains("i18nCatalog: 'I18n catalog'")
            .contains("paymentI18nCatalog: 'PayNow I18n'")
            .contains("reservationQueueI18nCatalog: 'Reservation Queue I18n'")
            .contains("platform default business copy")
            .contains("store override to tenant override, platform default, then frontend fallback")
            .contains("payment: 'PayNow payment'")
            .contains("quick_pay: 'Quick payment'")
            .contains("call_screen: 'Call screen'")
            .contains("reservation_share: 'Reservation share'")
            .contains("prompt: 'Prompt copy'");
    }
}
