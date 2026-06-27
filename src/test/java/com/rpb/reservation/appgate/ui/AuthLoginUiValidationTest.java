package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthLoginUiValidationTest {

    @Test
    void loginPageUsesSliderAuthApiAndDoesNotRenderRegisterFlow() throws Exception {
        Path pagePath = Path.of("src", "pages", "LoginPage.vue");
        Path apiPath = Path.of("src", "api", "authApi.ts");
        Path storePath = Path.of("src", "stores", "authSession.ts");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(storePath).exists();

        String source = Files.readString(pagePath) + Files.readString(apiPath) + Files.readString(storePath);

        assertThat(source)
            .contains("/api/v1/auth/captcha/slider")
            .contains("/api/v1/auth/login")
            .contains("/api/v1/auth/me")
            .contains("/api/v1/auth/logout")
            .contains("credentials: 'include'")
            .contains("captchaX")
            .contains("sliderScale")
            .contains("ResizeObserver")
            .contains("type=\"range\"")
            .contains("ref=\"sliderCanvas\"")
            .contains("refreshSlider")
            .contains("useAuthSessionStore")
            .contains("密码为 6 位数字或英文字母")
            .doesNotContain("/register")
            .doesNotContain("注册");
    }

    @Test
    void routerAddsLoginRouteAndGuardsExistingErpPagesWithAuthSession() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        String routerSource = Files.readString(routerPath);

        assertThat(routerSource)
            .contains("LoginPage")
            .contains("path: '/login'")
            .contains("meta: { public: true }")
            .contains("router.beforeEach")
            .contains("useAuthSessionStore")
            .contains("ensureCurrentUser")
            .contains("name: 'login'")
            .contains("query: { redirect: to.fullPath }")
            .contains("if (to.meta.public) {\n    return true\n  }")
            .doesNotContain("return typeof to.query.redirect === 'string' ? to.query.redirect : auth.defaultStoreRoute");
    }

    @Test
    void loginPageSeparatesPlatformTenantAndStaffEntrancesWithFutureStoreSelection() throws Exception {
        Path pagePath = Path.of("src", "pages", "LoginPage.vue");
        String pageSource = Files.readString(pagePath);

        assertThat(pageSource)
            .contains("platform-admin")
            .contains("tenant-admin")
            .contains("tenant-staff")
            .contains("平台后台")
            .contains("租户后台")
            .contains("租户员工")
            .contains("tenantCode")
            .contains("employeeUsername")
            .contains("loginUsername: '1000'")
            .contains("presetPassword: '393930'")
            .contains("20000000")
            .contains("1000")
            .contains("pendingStoreSelection")
            .contains("selectedStoreId")
            .contains("授权店面")
            .contains("切换店面")
            .contains("selectStoreAndContinue")
            .contains("loginPayloadUsername");
    }

    @Test
    void platformAdminEntryRoutesToPlatformTenantErpPage() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path storePath = Path.of("src", "stores", "authSession.ts");
        Path pagePath = Path.of("src", "pages", "LoginPage.vue");
        Path platformPagePath = Path.of("src", "pages", "PlatformTenantsPage.vue");
        Path platformApiPath = Path.of("src", "api", "platformApi.ts");
        Path erpToolbarPath = Path.of("src", "components", "erp", "ErpQueryToolbar.vue");
        Path platformTablePath = Path.of("src", "components", "platform", "PlatformTenantTable.vue");
        Path platformFormPath = Path.of("src", "components", "platform", "PlatformTenantForm.vue");

        assertThat(platformPagePath).exists();
        assertThat(platformApiPath).exists();
        assertThat(erpToolbarPath).exists();
        assertThat(platformTablePath).exists();
        assertThat(platformFormPath).exists();

        String source = Files.readString(routerPath)
            + Files.readString(storePath)
            + Files.readString(pagePath)
            + Files.readString(platformPagePath)
            + Files.readString(platformApiPath)
            + Files.readString(erpToolbarPath)
            + Files.readString(platformTablePath)
            + Files.readString(platformFormPath);

        assertThat(source)
            .contains("PlatformTenantsPage")
            .contains("path: '/platform/tenants'")
            .contains("requiresPlatformAdmin: true")
            .contains("platformHomeRoute")
            .contains("roles.includes('platform_admin')")
            .contains("/api/v1/platform/tenants")
            .contains("createTenant")
            .contains("updateTenant")
            .contains("deleteTenant")
            .contains("restoreTenant")
            .contains("租户管理")
            .contains("新增租户")
            .contains("恢复")
            .contains("已删除");
    }

    @Test
    void platformTenantErpUsesSplitListFormRoutesAndReusablePagingComponents() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path listPagePath = Path.of("src", "pages", "PlatformTenantsPage.vue");
        Path formPagePath = Path.of("src", "pages", "PlatformTenantFormPage.vue");
        Path platformApiPath = Path.of("src", "api", "platformApi.ts");
        Path erpPaginationPath = Path.of("src", "components", "erp", "ErpPagination.vue");
        Path erpToolbarPath = Path.of("src", "components", "erp", "ErpQueryToolbar.vue");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");
        Path tablePath = Path.of("src", "components", "platform", "PlatformTenantTable.vue");
        Path formPath = Path.of("src", "components", "platform", "PlatformTenantForm.vue");

        assertThat(formPagePath).exists();
        assertThat(erpPaginationPath).exists();
        assertThat(erpToolbarPath).exists();
        assertThat(navPath).exists();

        String source = Files.readString(routerPath)
            + Files.readString(listPagePath)
            + Files.readString(formPagePath)
            + Files.readString(platformApiPath)
            + Files.readString(erpPaginationPath)
            + Files.readString(erpToolbarPath)
            + Files.readString(navPath)
            + Files.readString(tablePath)
            + Files.readString(formPath);

        assertThat(source)
            .contains("path: '/platform/tenants/new'")
            .contains("path: '/platform/tenants/:tenantId/edit'")
            .contains("PlatformTenantFormPage")
            .contains("ErpPagination")
            .contains("ErpQueryToolbar")
            .contains("resetFilters")
            .contains("limit")
            .contains("offset")
            .contains("safePage.total")
            .contains(":limit=\"safePage.limit\"")
            .contains("contactPhone")
            .contains("address")
            .contains("principalName")
            .contains("initialPassword")
            .contains("password")
            .contains("router.push({ name: 'platform-tenant-create' })")
            .contains("router.push({ name: 'platform-tenant-edit'")
            .contains("退出登录")
            .contains("logoutCurrentUser")
            .contains("name: 'login'")
            .contains(":readonly=\"mode === 'edit'\"")
            .contains("tenantCode: mode.value === 'create'")
            .doesNotContain("v-if=\"formOpen\"");
    }

    @Test
    void tenantAdminErpUsesScopedStaffTableAndSettingsPages() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path storePath = Path.of("src", "stores", "authSession.ts");
        Path loginPagePath = Path.of("src", "pages", "LoginPage.vue");
        Path tenantApiPath = Path.of("src", "api", "tenantAdminApi.ts");
        Path profilePagePath = Path.of("src", "pages", "TenantAdminProfilePage.vue");
        Path staffPagePath = Path.of("src", "pages", "TenantAdminStaffPage.vue");
        Path staffFormPath = Path.of("src", "pages", "TenantAdminStaffFormPage.vue");
        Path tablesPagePath = Path.of("src", "pages", "TenantAdminTablesPage.vue");
        Path tableFormPath = Path.of("src", "pages", "TenantAdminTableFormPage.vue");
        Path settingsPagePath = Path.of("src", "pages", "TenantAdminSettingsPage.vue");
        Path navPath = Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue");

        assertThat(tenantApiPath).exists();
        assertThat(profilePagePath).exists();
        assertThat(staffPagePath).exists();
        assertThat(staffFormPath).exists();
        assertThat(tablesPagePath).exists();
        assertThat(tableFormPath).exists();
        assertThat(settingsPagePath).exists();
        assertThat(navPath).exists();

        String source = Files.readString(routerPath)
            + Files.readString(storePath)
            + Files.readString(loginPagePath)
            + Files.readString(tenantApiPath)
            + Files.readString(profilePagePath)
            + Files.readString(staffPagePath)
            + Files.readString(staffFormPath)
            + Files.readString(tablesPagePath)
            + Files.readString(tableFormPath)
            + Files.readString(settingsPagePath)
            + Files.readString(navPath);

        assertThat(source)
            .contains("tenantAdminHomeRoute")
            .contains("requiresTenantAdmin: true")
            .contains("path: '/stores/:storeId/admin/profile'")
            .contains("path: '/stores/:storeId/admin/staff'")
            .contains("path: '/stores/:storeId/admin/staff/new'")
            .contains("path: '/stores/:storeId/admin/staff/:staffId/edit'")
            .contains("path: '/stores/:storeId/admin/tables'")
            .contains("path: '/stores/:storeId/admin/tables/new'")
            .contains("path: '/stores/:storeId/admin/tables/:tableId/edit'")
            .contains("path: '/stores/:storeId/admin/settings'")
            .contains("/api/v1/stores/")
            .contains("`${baseEndpoint(storeId)}/profile`")
            .contains("/tenant-admin/staff")
            .contains("/tenant-admin/tables")
            .contains("/tenant-admin/settings")
            .contains("getTenantProfile")
            .contains("updateTenantProfile")
            .contains("uploadTenantProfileLogo")
            .contains("clearTenantProfileLogo")
            .contains("ErpPagination")
            .contains("ErpQueryToolbar")
            .contains("resetFilters")
            .contains("租户资料")
            .contains("员工管理")
            .contains("桌号管理")
            .contains("基础设置")
            .contains("employeeNo")
            .contains("areaName")
            .contains("tableCode")
            .contains("logoMediaUrl")
            .contains("principalName")
            .contains("reservationHoldMinutes")
            .contains("退出登录")
            .contains("auth.defaultHomeRoute")
            .doesNotContain("v-if=\"formOpen\"");
    }
}
