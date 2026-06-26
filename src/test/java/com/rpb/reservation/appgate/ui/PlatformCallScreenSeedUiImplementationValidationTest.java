package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformCallScreenSeedUiImplementationValidationTest {
    @Test
    void platformSeedPageUsesPlatformApisForTextTemplates() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformCallScreenSeedPage.vue");
        Path apiPath = Path.of("src", "api", "platformCallScreenSeedApi.ts");
        Path typesPath = Path.of("src", "types", "platformCallScreenSeed.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);
        String nav = Files.readString(navPath);

        assertThat(router)
            .contains("PlatformCallScreenSeedPage")
            .contains("path: '/platform/call-screen/text-seed'")
            .contains("name: 'platform-call-screen-text-seed'")
            .contains("meta: { requiresPlatformAdmin: true }");

        assertThat(nav)
            .contains("/platform/call-screen/text-seed")
            .contains("叫号模板");

        assertThat(apiClient)
            .contains("getPlatformCallScreenTextSeed")
            .contains("updatePlatformCallScreenTextSeed")
            .contains("/api/v1/platform/call-screen/text-seed")
            .contains("method: 'GET'")
            .contains("method: 'PATCH'")
            .doesNotContain("tenantId")
            .doesNotContain("storeId")
            .doesNotContain("mock");

        assertThat(types)
            .contains("interface PlatformCallScreenSeedSet")
            .contains("interface PlatformCallScreenSeedSlide")
            .contains("interface PlatformCallScreenSeedMutation")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");

        assertThat(page)
            .contains("平台叫号模板")
            .contains("文案种子模板")
            .contains("seedSet")
            .contains("slides")
            .contains("sortOrder")
            .contains("title")
            .contains("subtitle")
            .contains("tagline")
            .contains("updatePlatformCallScreenTextSeed")
            .contains("preview-screen")
            .contains("previewSlides")
            .contains("previewSlideIndex")
            .contains("startPreviewCarousel")
            .contains("selectPreviewSlide")
            .contains("window.setInterval")
            .contains("切换预览文案")
            .contains("大屏预览")
            .contains("关闭预览")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");
    }
}
