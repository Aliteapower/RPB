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

        String source = FrontendSourceSupport.readString(pagePath) + FrontendSourceSupport.readString(apiPath) + FrontendSourceSupport.readString(storePath);

        assertThat(source)
            .contains("/api/v1/auth/captcha/slider")
            .contains("/api/v1/auth/login")
            .contains("/api/v1/auth/me")
            .contains("/api/v1/auth/logout")
            .contains("credentials: 'include'")
            .contains("captchaX")
            .contains("sliderScale")
            .contains("ResizeObserver")
            .contains("ref=\"sliderCanvas\"")
            .contains("slider-piece-handle")
            .contains("role=\"slider\"")
            .contains(":aria-valuenow=\"Math.round(captchaX)\"")
            .contains("@pointerdown=\"startSliderDrag\"")
            .contains("@pointermove=\"moveSliderDrag\"")
            .contains("@keydown=\"handleSliderKeydown\"")
            .contains("slider-refresh")
            .contains("refreshSlider")
            .contains("useAuthSessionStore")
            .contains("login.passwordPolicy")
            .doesNotContain("type=\"range\"")
            .doesNotContain("/register")
            .doesNotContain("注册");
    }

    @Test
    void authApiTimesOutNetworkRequestsSoLoginCannotStayLoadingForever() throws Exception {
        Path apiPath = Path.of("src", "api", "authApi.ts");
        String apiSource = FrontendSourceSupport.readString(apiPath);

        assertThat(apiSource)
            .contains("AUTH_REQUEST_TIMEOUT_MS")
            .contains("AbortController")
            .contains("window.setTimeout")
            .contains("window.clearTimeout")
            .contains("signal: controller.signal")
            .contains("xhr.timeout = AUTH_REQUEST_TIMEOUT_MS");
    }

    @Test
    void loginStopsMissingStoreScopeInsteadOfRoutingToValidationStore() throws Exception {
        Path pagePath = Path.of("src", "pages", "LoginPage.vue");
        Path storePath = Path.of("src", "stores", "authSession.ts");
        String source = FrontendSourceSupport.readString(pagePath) + FrontendSourceSupport.readString(storePath);

        assertThat(source)
            .contains("missingStoreScopeText")
            .contains("login.errors.missingStoreScope")
            .contains("user.storeIds.length === 0")
            .contains("storeScope=missing")
            .doesNotContain("state.user?.defaultStoreId || state.user?.storeIds[0] || localValidationStoreId");
    }

    @Test
    void routerAddsLoginRouteAndGuardsExistingErpPagesWithAuthSession() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        String routerSource = FrontendSourceSupport.readString(routerPath).replace("\r\n", "\n");

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
        Path authTypePath = Path.of("src", "types", "auth.ts");
        Path hostContextPath = Path.of("src", "utils", "hostContext.ts");
        String pageSource = FrontendSourceSupport.readString(pagePath);
        String source = pageSource
            + FrontendSourceSupport.readString(authTypePath)
            + FrontendSourceSupport.readString(hostContextPath);

        assertThat(source)
            .contains("platform-admin")
            .contains("tenant-admin")
            .contains("tenant-staff")
            .contains("login.entries.platformAdmin.title")
            .contains("login.entries.tenantAdmin.title")
            .contains("login.entries.tenantStaff.title")
            .contains("tenantCode")
            .contains("employeeUsername")
            .contains("loginUsername: '1000'")
            .contains("20000000")
            .contains("1000")
            .contains("pendingStoreSelection")
            .contains("selectedStoreId")
            .contains("login.store.authorized")
            .contains("login.store.switch")
            .contains("selectStoreAndContinue")
            .contains("loginPayloadUsername")
            .contains("export type AuthLoginEntry = 'platform_admin' | 'tenant_admin' | 'staff'")
            .contains("loginEntry?: AuthLoginEntry")
            .contains("tenantCode?: string | null")
            .contains("resolveLoginHostContext")
            .contains("availableLoginEntries")
            .contains("hostContext.kind === 'platform'")
            .contains("hostContext.kind === 'tenant'")
            .contains("tenantCodeVisible")
            .contains("authLoginEntry")
            .contains("rememberAccount")
            .contains("rememberAccountStorageKey")
            .contains("login.remember.account")
            .doesNotContain("presetPassword: '393930'")
            .doesNotContain("password = ref('393930')");
    }

    @Test
    void tenantSubdomainLoginHidesPlatformEntryTenantCodeAndSeedAccounts() throws Exception {
        Path pagePath = Path.of("src", "pages", "LoginPage.vue");
        Path hostContextPath = Path.of("src", "utils", "hostContext.ts");
        String pageSource = FrontendSourceSupport.readString(pagePath);
        String hostContextSource = FrontendSourceSupport.readString(hostContextPath);

        assertThat(hostContextSource)
            .contains("NUMERIC_TENANT_PREFIX_PATTERN")
            .contains("TENANT_PREFIX_PATTERN")
            .contains("__RPB_HOST_PREFIX_BASE_HOST__")
            .contains("VITE_RPB_HOST_PREFIX_BASE_HOST")
            .contains("resolveConfiguredBaseHost")
            .contains("labels.length > 3")
            .contains("return { kind: 'tenant', tenantCode: prefix");

        assertThat(pageSource)
            .contains("availableLoginEntries.value.find")
            .contains("entry.id === 'tenant-admin' || entry.id === 'tenant-staff'")
            .contains("entryTargetVisible")
            .contains("v-if=\"entryTargetVisible\"")
            .contains("tenantCodeFieldVisible")
            .contains("hostContext.kind !== 'tenant'")
            .contains("function defaultLoginUsername")
            .contains("function defaultEmployeeUsername")
            .contains("hostContext.kind === 'legacy' ? entry.loginUsername : ''")
            .doesNotContain("const username = ref(initialEntry.loginUsername)")
            .doesNotContain("const employeeUsername = ref(initialEntry.employeeUsername ?? initialEntry.loginUsername)");
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

        String source = FrontendSourceSupport.readString(routerPath)
            + FrontendSourceSupport.readString(storePath)
            + FrontendSourceSupport.readString(pagePath)
            + FrontendSourceSupport.readString(platformPagePath)
            + FrontendSourceSupport.readString(platformApiPath)
            + FrontendSourceSupport.readString(erpToolbarPath)
            + FrontendSourceSupport.readString(platformTablePath)
            + FrontendSourceSupport.readString(platformFormPath);

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
            .contains("platform.tenants.list.title")
            .contains("platform.tenants.list.create")
            .contains("common.actions.restore")
            .contains("platform.tenants.status.deleted");
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

        String source = FrontendSourceSupport.readString(routerPath)
            + FrontendSourceSupport.readString(listPagePath)
            + FrontendSourceSupport.readString(formPagePath)
            + FrontendSourceSupport.readString(platformApiPath)
            + FrontendSourceSupport.readString(erpPaginationPath)
            + FrontendSourceSupport.readString(erpToolbarPath)
            + FrontendSourceSupport.readString(navPath)
            + FrontendSourceSupport.readString(tablePath)
            + FrontendSourceSupport.readString(formPath);

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
            .contains("common.actions.logout")
            .contains("logoutCurrentUser")
            .contains("name: 'login'")
            .contains(":readonly=\"mode === 'edit'\"")
            .contains("tenantCode: mode.value === 'create'")
            .doesNotContain("v-if=\"formOpen\"");
    }

    @Test
    void platformTenantEditFormMaintainsTenantAdminStoreAccess() throws Exception {
        Path formPagePath = Path.of("src", "pages", "PlatformTenantFormPage.vue");
        Path formPath = Path.of("src", "components", "platform", "PlatformTenantForm.vue");
        Path structurePath = Path.of("src", "components", "platform", "PlatformTenantStructurePanel.vue");
        Path apiPath = Path.of("src", "api", "platformApi.ts");
        Path uiPath = Path.of("src", "components", "platform", "platformTenantUi.ts");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");
        Path enPath = Path.of("src", "i18n", "locales", "en-SG.ts");

        String source = FrontendSourceSupport.readString(formPagePath)
            + FrontendSourceSupport.readString(formPath)
            + FrontendSourceSupport.readString(structurePath)
            + FrontendSourceSupport.readString(apiPath)
            + FrontendSourceSupport.readString(uiPath);
        String zh = FrontendSourceSupport.readString(zhPath);
        String en = FrontendSourceSupport.readString(enPath);

        assertThat(source)
            .contains("getTenantAdminStoreAccess")
            .contains("/admin-store-access")
            .contains("adminStoreOptions")
            .contains("adminStoreIds")
            .contains("defaultAdminStoreId")
            .contains("admin-store-access-panel")
            .contains("toggleAdminStoreFromEvent")
            .contains("selectedAdminStoreOptions")
            .contains("platform.tenants.form.adminStoreAccess.title")
            .contains("platform.tenants.form.adminStoreAccess.defaultStore")
            .contains("PlatformTenantStructurePanel")
            .contains("listOperatingEntities")
            .contains("createOperatingEntity")
            .contains("updateOperatingEntity")
            .contains("listTenantStores")
            .contains("createTenantStore")
            .contains("updateTenantStore")
            .contains("saveOperatingEntity")
            .contains("saveStore")
            .contains("platform.tenants.structure.operatingEntities.title")
            .contains("platform.tenants.structure.stores.title")
            .contains("PlatformOperatingEntityFormModel")
            .contains("PlatformStoreFormModel");

        assertThat(zh)
            .contains("title: '授权门店'")
            .contains("defaultStore: '默认门店'")
            .contains("title: '经营主体与门店'")
            .contains("newEntity: '新增经营主体'")
            .contains("newStore: '新增门店'");
        assertThat(en)
            .contains("title: 'Authorised stores'")
            .contains("defaultStore: 'Default store'")
            .contains("title: 'Operating entities and stores'")
            .contains("newEntity: 'Add entity'")
            .contains("newStore: 'Add store'");
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

        String source = FrontendSourceSupport.readString(routerPath)
            + FrontendSourceSupport.readString(storePath)
            + FrontendSourceSupport.readString(loginPagePath)
            + FrontendSourceSupport.readString(tenantApiPath)
            + FrontendSourceSupport.readString(profilePagePath)
            + FrontendSourceSupport.readString(staffPagePath)
            + FrontendSourceSupport.readString(staffFormPath)
            + FrontendSourceSupport.readString(tablesPagePath)
            + FrontendSourceSupport.readString(tableFormPath)
            + FrontendSourceSupport.readString(settingsPagePath)
            + FrontendSourceSupport.readString(navPath);

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
            .contains("common.actions.logout")
            .contains("auth.defaultHomeRoute")
            .doesNotContain("v-if=\"formOpen\"");
    }

    @Test
    void tenantAdminProfileSaveAlsoUploadsSelectedLogo() throws Exception {
        Path profilePagePath = Path.of("src", "pages", "TenantAdminProfilePage.vue");

        String profilePageSource = FrontendSourceSupport.readString(profilePagePath);

        assertThat(profilePageSource)
            .contains("async function uploadSelectedLogo(successMessage: string): Promise<void>")
            .contains("await uploadSelectedLogo('资料和 LOGO 已保存')")
            .contains("await uploadSelectedLogo('LOGO 已更新')")
            .contains("savedText.value = successMessage")
            .contains(":disabled=\"!logoFile || logoSaving || saving\"")
            .contains(":disabled=\"logoSaving || saving\"");
    }

    @Test
    void tenantAdminStaffManagementSupportsProtectedSelfAdminMaintenance() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path tenantApiPath = Path.of("src", "api", "tenantAdminApi.ts");
        Path staffPagePath = Path.of("src", "pages", "TenantAdminStaffPage.vue");
        Path staffFormPath = Path.of("src", "pages", "TenantAdminStaffFormPage.vue");

        String source = FrontendSourceSupport.readString(routerPath)
            + FrontendSourceSupport.readString(tenantApiPath)
            + FrontendSourceSupport.readString(staffPagePath)
            + FrontendSourceSupport.readString(staffFormPath);

        assertThat(source)
            .contains("path: '/stores/:storeId/admin/staff/me/edit'")
            .contains("name: 'tenant-admin-staff-self-edit'")
            .contains("getCurrentTenantAdminStaff")
            .contains("updateCurrentTenantAdminStaff")
            .contains("getTenantProfile")
            .contains("updateTenantProfile")
            .contains("uploadTenantProfileLogo")
            .contains("clearTenantProfileLogo")
            .contains("`${baseEndpoint(storeId)}/staff/me`")
            .contains("accountType: 'staff' | 'tenant_admin'")
            .contains("statusEditable")
            .contains("selfAdminStaff")
            .contains("visibleStaff")
            .contains("管理员")
            .contains("mode.value === 'self'")
            .contains("statusFieldVisible")
            .contains("mode.value !== 'self'")
            .contains("租户资料")
            .contains("租户名称")
            .contains("默认语言")
            .contains("负责人")
            .contains("租户地址")
            .contains("租户 LOGO")
            .contains("管理员账号")
            .contains("updateTenantProfile(storeId.value, tenantProfilePayload())")
            .contains("uploadSelectedLogo")
            .contains("tenant-admin-staff-self-edit");
    }

    @Test
    void tenantAdminSelfMaintenanceShowsReadonlyStoreAuthorization() throws Exception {
        Path staffFormPath = Path.of("src", "pages", "TenantAdminStaffFormPage.vue");

        String staffFormSource = FrontendSourceSupport.readString(staffFormPath);

        assertThat(staffFormSource)
            .contains("void loadAssignableStores()")
            .contains("readonlyStoreAccessSummary")
            .contains("readonlyDefaultStoreSummary")
            .contains("class=\"store-access-edit-panel wide-field\"")
            .contains("store-access-readonly-panel")
            .contains("class=\"readonly-store-access\"")
            .contains("tenant.staffForm.storeAccess.empty")
            .contains("tenant.staffForm.storeAccess.title")
            .contains("tenant.staffForm.storeAccess.defaultStore")
            .contains("toggleStoreFromEvent(store.storeId, $event)")
            .contains("v-model=\"defaultStoreId\"")
            .contains("mode.value !== 'self' && !validateStoreAccessForm()")
            .contains("adminAccountPayload()")
            .doesNotContain("if (storeAccessVisible.value)");
    }

    @Test
    void tenantAdminStaffListShowsTenantProfilePhoneForProtectedAdmin() throws Exception {
        Path staffPagePath = Path.of("src", "pages", "TenantAdminStaffPage.vue");

        String staffPageSource = FrontendSourceSupport.readString(staffPagePath);

        assertThat(staffPageSource)
            .contains("getTenantProfile")
            .contains("tenantContactPhone")
            .contains("getTenantProfile(storeId.value)")
            .contains("displayStaffPhone")
            .contains("item.accountType === 'tenant_admin'")
            .contains("{{ displayStaffPhone(item) }}");
    }

    @Test
    void tenantAdminStaffListShowsStoreAuthorizationSummary() throws Exception {
        Path staffPagePath = Path.of("src", "pages", "TenantAdminStaffPage.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");
        Path enPath = Path.of("src", "i18n", "locales", "en-SG.ts");

        String staffPageSource = FrontendSourceSupport.readString(staffPagePath);
        String zhSource = FrontendSourceSupport.readString(zhPath);
        String enSource = FrontendSourceSupport.readString(enPath);

        assertThat(staffPageSource)
            .contains("useI18n")
            .contains("auth.ensureAuthorizedStores()")
            .contains("storeAccessSummary(item)")
            .contains("defaultStoreSummary(item)")
            .contains("tenant.staffList.storeAccess.authorized")
            .contains("tenant.staffList.storeAccess.defaultStore")
            .contains("class=\"store-access-cell\"")
            .contains("item.storeIds")
            .contains("item.defaultStoreId");

        assertThat(zhSource)
            .contains("staffList")
            .contains("authorized: '授权门店'")
            .contains("defaultStore: '默认门店'");

        assertThat(enSource)
            .contains("staffList")
            .contains("authorized: 'Authorised stores'")
            .contains("defaultStore: 'Default store'");
    }
}
