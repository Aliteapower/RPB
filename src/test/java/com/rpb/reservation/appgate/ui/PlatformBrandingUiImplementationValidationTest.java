package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformBrandingUiImplementationValidationTest {
    @Test
    void platformTenantFormSupportsTenantLogoUploadPreviewAndClear() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformTenantFormPage.vue");
        Path formPath = Path.of("src", "components", "platform", "PlatformTenantForm.vue");
        Path apiPath = Path.of("src", "api", "platformApi.ts");
        Path uiPath = Path.of("src", "components", "platform", "platformTenantUi.ts");

        String page = FrontendSourceSupport.readString(pagePath);
        String form = FrontendSourceSupport.readString(formPath);
        String api = FrontendSourceSupport.readString(apiPath);
        String ui = FrontendSourceSupport.readString(uiPath);

        assertThat(api)
            .contains("logoMediaUrl")
            .contains("uploadTenantLogo")
            .contains("clearTenantLogo")
            .contains("/logo")
            .contains("FormData")
            .doesNotContain("logoBase64");

        assertThat(ui)
            .contains("logoMediaUrl")
            .contains("logoFile");

        assertThat(page)
            .contains("uploadTenantLogo")
            .contains("clearTenantLogo")
            .contains("response.tenant.logoMediaUrl")
            .contains("submitTenantLogo")
            .contains("removeTenantLogo");

        assertThat(form)
            .contains("platform.tenants.form.tenantLogo")
            .contains("type=\"file\"")
            .contains("accept=\"image/jpeg,image/png,image/webp\"")
            .contains("logoMediaUrl")
            .contains("logoFile")
            .contains("platform.tenants.form.chooseImage")
            .contains("platform.tenants.form.clearLogo")
            .doesNotContain("accept=\"image/jpeg,image/png,image/webp,video/mp4,video/webm\"");
    }

    @Test
    void platformProfilePageMaintainsPlatformInfoAndSocialMediaLinks() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformProfilePage.vue");
        Path apiPath = Path.of("src", "api", "platformProfileApi.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();

        String page = FrontendSourceSupport.readString(pagePath);
        String api = FrontendSourceSupport.readString(apiPath);
        String router = FrontendSourceSupport.readString(routerPath);
        String nav = FrontendSourceSupport.readString(navPath);
        String zh = FrontendSourceSupport.readString(zhPath);

        assertThat(router)
            .contains("PlatformProfilePage")
            .contains("path: '/platform/settings/profile'")
            .contains("name: 'platform-profile'")
            .contains("meta: { requiresPlatformAdmin: true }");

        assertThat(nav)
            .contains("/platform/settings/profile")
            .contains("nav.platform.profile");
        assertThat(zh)
            .contains("profile: '平台资料'");

        assertThat(api)
            .contains("getPlatformProfile")
            .contains("updatePlatformProfile")
            .contains("uploadPlatformProfileLogo")
            .contains("clearPlatformProfileLogo")
            .contains("createPlatformSocialLink")
            .contains("updatePlatformSocialLink")
            .contains("deletePlatformSocialLink")
            .contains("uploadPlatformSocialLinkLogo")
            .contains("/api/v1/platform/profile")
            .contains("FormData");

        assertThat(page)
            .contains("platform.profile.page.title")
            .contains("platform.profile.fields.platformName")
            .contains("UEN")
            .contains("platform.profile.fields.address")
            .contains("platform.profile.fields.phone")
            .contains("platform.profile.fields.email")
            .contains("platform.profile.fields.website")
            .contains("platform.profile.fields.platformLogo")
            .contains("platform.profile.fields.socialMedia")
            .contains("platform.profile.fields.socialLogo")
            .contains("platform.profile.social.createLogoAria")
            .contains("handleNewSocialLogoChange")
            .contains("newSocialLink.logoFile")
            .contains("uploadPlatformSocialLinkLogo")
            .contains("createdLink.id")
            .contains("createPlatformSocialLink")
            .contains("updatePlatformSocialLink")
            .contains("deletePlatformSocialLink");
    }

    @Test
    void queueDisplayUsesTenantLogoImageBeforeFallbackTextLogo() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueDisplayPage.vue");
        Path apiPath = Path.of("src", "api", "queueDisplayApi.ts");
        Path typesPath = Path.of("src", "types", "queueDisplay.ts");

        String page = FrontendSourceSupport.readString(pagePath);
        String api = FrontendSourceSupport.readString(apiPath);
        String types = FrontendSourceSupport.readString(typesPath);

        assertThat(types)
            .contains("tenantLogoUrl?: string | null");

        assertThat(api)
            .contains("isOptionalString(candidate.tenantLogoUrl)");

        assertThat(page)
            .contains("tenantLogoUrl")
            .contains("tenantLogoFailed")
            .contains("showTenantLogoImage")
            .contains("@error=\"handleTenantLogoError\"")
            .contains("tenant-logo-image")
            .contains("食");
    }
}
