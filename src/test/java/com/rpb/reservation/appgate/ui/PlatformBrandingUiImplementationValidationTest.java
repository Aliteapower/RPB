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

        String page = Files.readString(pagePath);
        String form = Files.readString(formPath);
        String api = Files.readString(apiPath);
        String ui = Files.readString(uiPath);

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
            .contains("租户 LOGO")
            .contains("type=\"file\"")
            .contains("accept=\"image/jpeg,image/png,image/webp\"")
            .contains("logoMediaUrl")
            .contains("logoFile")
            .contains("选择图片")
            .contains("清空 LOGO")
            .doesNotContain("accept=\"image/jpeg,image/png,image/webp,video/mp4,video/webm\"");
    }

    @Test
    void platformProfilePageMaintainsPlatformInfoAndSocialMediaLinks() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformProfilePage.vue");
        Path apiPath = Path.of("src", "api", "platformProfileApi.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();

        String page = Files.readString(pagePath);
        String api = Files.readString(apiPath);
        String router = Files.readString(routerPath);
        String nav = Files.readString(navPath);

        assertThat(router)
            .contains("PlatformProfilePage")
            .contains("path: '/platform/settings/profile'")
            .contains("name: 'platform-profile'")
            .contains("meta: { requiresPlatformAdmin: true }");

        assertThat(nav)
            .contains("/platform/settings/profile")
            .contains("平台资料");

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
            .contains("平台资料")
            .contains("平台名称")
            .contains("UEN")
            .contains("地址")
            .contains("电话")
            .contains("电邮")
            .contains("网址")
            .contains("平台 LOGO")
            .contains("社交媒体")
            .contains("社媒 LOGO")
            .contains("uploadPlatformSocialLinkLogo")
            .contains("createPlatformSocialLink")
            .contains("updatePlatformSocialLink")
            .contains("deletePlatformSocialLink");
    }

    @Test
    void queueDisplayUsesTenantLogoImageBeforeFallbackTextLogo() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueDisplayPage.vue");
        Path apiPath = Path.of("src", "api", "queueDisplayApi.ts");
        Path typesPath = Path.of("src", "types", "queueDisplay.ts");

        String page = Files.readString(pagePath);
        String api = Files.readString(apiPath);
        String types = Files.readString(typesPath);

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
