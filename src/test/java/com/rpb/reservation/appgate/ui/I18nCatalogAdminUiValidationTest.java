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
        String platformPage = FrontendSourceSupport.readString(Path.of("src", "pages", "PlatformI18nCatalogPage.vue"));
        String tenantPage = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminI18nCatalogPage.vue"));
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(router)
            .contains("PlatformI18nCatalogPage")
            .contains("TenantAdminI18nCatalogPage")
            .contains("path: '/platform/i18n/catalog'")
            .contains("name: 'platform-i18n-catalog'")
            .contains("path: '/stores/:storeId/admin/i18n-catalog'")
            .contains("name: 'tenant-admin-i18n-catalog'");

        assertThat(platformNav)
            .contains("/platform/i18n/catalog")
            .contains("nav.platform.i18nCatalog");
        assertThat(tenantNav)
            .contains("/admin/i18n-catalog")
            .contains("nav.tenant.i18nCatalog");

        assertThat(api)
            .contains("getPlatformI18nCatalog")
            .contains("updatePlatformI18nCatalog")
            .contains("getTenantAdminI18nCatalog")
            .contains("updateTenantAdminI18nCatalog")
            .contains("/api/v1/platform/i18n/catalog")
            .contains("/tenant-admin/i18n/catalog");

        assertThat(types)
            .contains("I18nCatalogScopeLevel")
            .contains("effectiveSource")
            .contains("platformMessage")
            .contains("tenantOverride")
            .contains("storeOverride");

        assertThat(platformPage)
            .contains("platform.i18nCatalog.page.note")
            .contains("locale.platformMessage")
            .contains("updatePlatformI18nCatalog")
            .doesNotContain("generated.");
        assertThat(tenantPage)
            .contains("tenant.i18nCatalog.page.note")
            .contains("scopeLevel")
            .contains("locale.storeOverride")
            .contains("locale.tenantOverride")
            .contains("clear: true")
            .doesNotContain("generated.");

        assertThat(zh)
            .contains("i18nCatalog: '国际化字典'")
            .contains("平台默认业务文案")
            .contains("门店覆盖、租户覆盖、平台默认、前端兜底");
        assertThat(en)
            .contains("i18nCatalog: 'I18n catalog'")
            .contains("platform default business copy")
            .contains("store override to tenant override, platform default, then frontend fallback");
    }
}
