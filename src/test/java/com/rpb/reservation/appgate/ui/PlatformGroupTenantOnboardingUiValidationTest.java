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

    @Test
    void platformTenantManagementMobileUiFocusesOnBusinessStructure() throws Exception {
        Path tablePath = Path.of("src", "components", "platform", "PlatformTenantTable.vue");
        Path structurePath = Path.of("src", "components", "platform", "PlatformTenantStructurePanel.vue");
        Path pagePath = Path.of("src", "pages", "PlatformTenantFormPage.vue");
        Path apiPath = Path.of("src", "api", "platformApi.ts");
        Path uiModelPath = Path.of("src", "components", "platform", "platformTenantUi.ts");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");
        Path enPath = Path.of("src", "i18n", "locales", "en-SG.ts");

        String table = FrontendSourceSupport.readString(tablePath);
        String structure = FrontendSourceSupport.readString(structurePath);
        String page = FrontendSourceSupport.readString(pagePath);
        String api = FrontendSourceSupport.readString(apiPath);
        String uiModel = FrontendSourceSupport.readString(uiModelPath);
        String zh = FrontendSourceSupport.readString(zhPath);
        String en = FrontendSourceSupport.readString(enPath);

        assertThat(table)
            .contains("tenant-card-list")
            .contains("tenant-card__primary")
            .contains("formatTenantUpdatedAt")
            .contains("@click=\"emit('structure', tenant)\"")
            .contains("@media (max-width: 760px)")
            .contains("platform.tenants.table.structure");

        assertThat(structure)
            .contains("activePanel")
            .contains("entityFormOpen")
            .contains("storeFormOpen")
            .contains("structure-summary")
            .contains("structure-tabs")
            .contains("platform.tenants.structure.summary.operatingEntities")
            .contains("platform.tenants.structure.guide.noEntities")
            .contains("v-if=\"entityFormOpen\"")
            .contains("v-if=\"storeFormOpen\"")
            .contains("advanced-fields")
            .contains("storeForm.adminUsername")
            .contains("storeForm.adminPassword")
            .contains("platform.tenants.structure.fields.branchAdminAccount")
            .contains("platform.tenants.structure.fields.branchAdminUsername")
            .contains("platform.tenants.structure.fields.branchAdminPassword");

        assertThat(api)
            .contains("adminUsername?: string | null")
            .contains("adminPassword?: string | null");
        assertThat(uiModel)
            .contains("adminUsername: string")
            .contains("adminPassword: string");
        assertThat(page)
            .contains("adminUsername: optionalValue(submittedForm.adminUsername)")
            .contains("adminPassword: optionalValue(submittedForm.adminPassword)");

        assertThat(zh)
            .contains("structure: '门店结构'")
            .contains("operatingEntities: '经营主体'")
            .contains("activeStores: '启用门店'")
            .contains("noEntities: '集团会自动准备默认经营主体，请继续新增分店。'")
            .contains("newStore: '新增分店'")
            .contains("supplementalInfo: '补充资料'")
            .contains("operationDefaults: '运营默认值'")
            .contains("branchAdminAccount: '分店管理员账号'")
            .contains("branchAdminUsername: '分店管理员登录账号'")
            .contains("branchAdminPassword: '分店管理员密码'");
        assertThat(en)
            .contains("structure: 'Store structure'")
            .contains("operatingEntities: 'Operating entities'")
            .contains("activeStores: 'Active stores'")
            .contains("noEntities: 'The group prepares a default operating entity automatically. Add a branch next.'")
            .contains("newStore: 'Add branch'")
            .contains("supplementalInfo: 'Supplemental details'")
            .contains("operationDefaults: 'Operating defaults'")
            .contains("branchAdminAccount: 'Branch admin account'")
            .contains("branchAdminUsername: 'Branch admin username'")
            .contains("branchAdminPassword: 'Branch admin password'");
    }
}
