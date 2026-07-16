package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontendI18nFoundationValidationTest {

    @Test
    void frontendRegistersVueI18nWithExplicitDefaultLocaleStrategy() throws Exception {
        Path packagePath = Path.of("package.json");
        Path appPath = Path.of("src", "App.vue");
        Path mainPath = Path.of("src", "main.ts");
        Path i18nPath = Path.of("src", "i18n", "index.ts");
        Path switcherPath = Path.of("src", "components", "common", "FrontendLocaleSwitcher.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");
        Path enPath = Path.of("src", "i18n", "locales", "en-SG.ts");

        assertThat(i18nPath).exists();
        assertThat(switcherPath).exists();
        assertThat(zhPath).exists();
        assertThat(enPath).exists();

        String packageSource = FrontendSourceSupport.readString(packagePath);
        String appSource = FrontendSourceSupport.readString(appPath);
        String mainSource = FrontendSourceSupport.readString(mainPath);
        String i18nSource = FrontendSourceSupport.readString(i18nPath);
        String switcherSource = FrontendSourceSupport.readString(switcherPath);
        String localeSource = FrontendSourceSupport.readString(zhPath) + FrontendSourceSupport.readString(enPath);

        assertThat(packageSource).contains("\"vue-i18n\"");
        assertThat(appSource)
            .contains("FrontendLocaleSwitcher")
            .contains("<FrontendLocaleSwitcher");
        assertThat(mainSource)
            .contains("import { i18n } from './i18n'")
            .contains(".use(i18n)");
        assertThat(i18nSource)
            .contains("DEFAULT_FRONTEND_LOCALE = 'zh-CN'")
            .contains("SUPPORTED_FRONTEND_LOCALES")
            .contains("localStorage")
            .contains("navigator.languages")
            .contains("document.documentElement.lang");
        assertThat(switcherSource)
            .contains("SUPPORTED_FRONTEND_LOCALES")
            .contains("setFrontendLocale")
            .contains("common.localeSwitcher.aria")
            .contains("common.localeSwitcher.zhCN")
            .contains("common.localeSwitcher.enSG");
        assertThat(localeSource)
            .contains("platformAdmin")
            .contains("localeSwitcher")
            .contains("tenantAppNotEnabled")
            .contains("reservations: '今日预约'")
            .contains("reservation: '预约'");
    }

    @Test
    void firstRoundCommonCopyUsesI18nKeysInsteadOfHardcodedChinese() throws Exception {
        List<Path> migratedPaths = List.of(
            Path.of("src", "components", "common", "PasswordInput.vue"),
            Path.of("src", "components", "platform", "PlatformAdminNav.vue"),
            Path.of("src", "components", "staff", "StaffBottomNav.vue"),
            Path.of("src", "components", "staff", "staffBottomNavItems.ts"),
            Path.of("src", "components", "staff-home", "StaffHomeTopBar.vue"),
            Path.of("src", "components", "staff-home", "useCurrentClock.ts"),
            Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue"),
            Path.of("src", "pages", "LoginPage.vue"),
            Path.of("src", "pages", "StoreStaffHomePage.vue"),
            Path.of("src", "utils", "appGateErrorMessages.ts"),
            Path.of("src", "utils", "reservationCreateMessages.ts")
        );

        StringBuilder source = new StringBuilder();
        for (Path path : migratedPaths) {
            assertThat(path).exists();
            source.append(FrontendSourceSupport.readString(path)).append('\n');
        }

        assertThat(source)
            .contains("useI18n")
            .contains("translate(")
            .contains("labelKey")
            .contains("login.entries.platformAdmin.title")
            .contains("staffHome.kpis.reservations")
            .contains("nav.platform.tenants")
            .contains("appGate.errors.permissionDenied.message")
            .contains("reservationCreate.errors.capacityInsufficient")
            .doesNotContain("后台入口")
            .doesNotContain("平台后台")
            .doesNotContain("租户后台")
            .doesNotContain("租户员工")
            .doesNotContain("账号或密码不正确")
            .doesNotContain("滑块校验加载失败")
            .doesNotContain("预约排队叫号系统未开通")
            .doesNotContain("当前账号没有此功能权限")
            .doesNotContain("今日概览")
            .doesNotContain("今日预约")
            .doesNotContain("当前排队")
            .doesNotContain("桌台状态")
            .doesNotContain("员工工作台导航")
            .doesNotContain("显示密码")
            .doesNotContain("隐藏密码");
    }

    @Test
    void mobileLocaleSwitcherAvoidsTopbarActionControls() throws Exception {
        Path switcherPath = Path.of("src", "components", "common", "FrontendLocaleSwitcher.vue");

        String switcherSource = FrontendSourceSupport.readString(switcherPath);

        assertThat(switcherSource)
            .contains("@media (max-width: 520px)")
            .contains("top: auto")
            .contains("bottom: max(86px, calc(78px + env(safe-area-inset-bottom)))")
            .contains("right: max(8px, env(safe-area-inset-right))");
    }

    @Test
    void defaultLocaleStrategyAndCatalogBoundaryAreDocumented() throws Exception {
        Path architecturePath = Path.of("docs", "architecture", "ARCHITECTURE.md");
        Path frontendDocPath = Path.of("docs", "frontend", "I18N_FRONTEND_FOUNDATION.md");

        assertThat(frontendDocPath).exists();

        String architectureSource = FrontendSourceSupport.readString(architecturePath);
        String frontendDocSource = FrontendSourceSupport.readString(frontendDocPath);

        assertThat(architectureSource)
            .contains("Frontend shell fallback is zh-CN")
            .contains("Store operational locale may still be en-SG");
        assertThat(frontendDocSource)
            .contains("Frontend fallback locale: `zh-CN`")
            .contains("Supported first-round locales: `zh-CN`, `en-SG`")
            .contains("Store locale remains the operational display context")
            .contains("Do not move all static UI labels into `i18n_message_catalog`")
            .contains("`i18n_message_key_registry` defines which keys are maintainable")
            .contains("store override -> tenant override -> platform default -> frontend fallback")
            .contains("Runtime consumers of configurable copy must use the backend i18n resolver")
            .contains("store override -> tenant override -> platform default -> zh-CN fallback")
            .contains("/api/v1/platform/i18n/catalog")
            .contains("/api/v1/stores/{storeId}/tenant-admin/i18n/catalog");
    }
}
