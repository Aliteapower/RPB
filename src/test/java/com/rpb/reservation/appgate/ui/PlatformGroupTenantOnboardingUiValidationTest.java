package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformGroupTenantOnboardingUiValidationTest {
    @Test
    void platformTenantCreateFormExposesSingleStoreAndGroupOnboardingModes() throws Exception {
        Path formPath = Path.of("src", "components", "platform", "PlatformTenantForm.vue");
        Path listPagePath = Path.of("src", "pages", "PlatformTenantsPage.vue");
        Path pagePath = Path.of("src", "pages", "PlatformTenantFormPage.vue");
        Path apiPath = Path.of("src", "api", "platformApi.ts");
        Path uiModelPath = Path.of("src", "components", "platform", "platformTenantUi.ts");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");
        Path enPath = Path.of("src", "i18n", "locales", "en-SG.ts");

        String form = FrontendSourceSupport.readString(formPath);
        String listPage = FrontendSourceSupport.readString(listPagePath);
        String page = FrontendSourceSupport.readString(pagePath);
        String api = FrontendSourceSupport.readString(apiPath);
        String uiModel = FrontendSourceSupport.readString(uiModelPath);
        String zh = FrontendSourceSupport.readString(zhPath);
        String en = FrontendSourceSupport.readString(enPath);

        assertThat(api)
            .contains("onboardingMode?: PlatformTenantOnboardingMode")
            .contains("single_store")
            .contains("group_multi_store");
        assertThat(uiModel).contains("onboardingMode: PlatformTenantOnboardingMode");
        assertThat(listPage)
            .contains("type PlatformTenantOnboardingMode")
            .contains("openCreatePage(onboardingMode: PlatformTenantOnboardingMode)")
            .contains("router.push({ name: 'platform-tenant-create', query: { onboardingMode } })")
            .contains("openCreatePage('group_multi_store')")
            .contains("openCreatePage('single_store')")
            .contains("platform.tenants.list.createGroup")
            .contains("platform.tenants.list.createSingle");
        assertThat(form)
            .contains("platform.tenants.form.onboardingMode.title")
            .contains("platform.tenants.form.onboardingMode.singleStore")
            .contains("platform.tenants.form.onboardingMode.groupMultiStore")
            .contains("v-model=\"localForm.onboardingMode\"");
        assertThat(page)
            .contains("normalizeCreateOnboardingMode(route.query.onboardingMode)")
            .contains("onboardingMode: submittedForm.onboardingMode")
            .contains("router.push({ name: 'platform-tenant-edit'")
            .contains("hash: '#tenant-structure'");
        assertThat(zh)
            .contains("title: '集团 / 租户管理'")
            .contains("createGroup: '新增集团'")
            .contains("createSingle: '新增单店'")
            .contains("createGroupTitle: '新增集团'")
            .contains("createSingleTitle: '新增单店'")
            .contains("singleStore: '单店快速开户'")
            .contains("groupMultiStore: '集团多店开户'");
        assertThat(en)
            .contains("title: 'Group / tenant management'")
            .contains("createGroup: 'Add group'")
            .contains("createSingle: 'Add single store'")
            .contains("createGroupTitle: 'Add group'")
            .contains("createSingleTitle: 'Add single store'")
            .contains("singleStore: 'Single-store quick setup'")
            .contains("groupMultiStore: 'Group multi-store setup'");
    }
}
