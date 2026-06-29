package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformCallScreenSeedUiImplementationValidationTest {
    @Test
    void platformSeedPageUsesPlatformApisForTextAndMediaTemplates() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformCallScreenSeedPage.vue");
        Path apiPath = Path.of("src", "api", "platformCallScreenSeedApi.ts");
        Path typesPath = Path.of("src", "types", "platformCallScreenSeed.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");
        Path modeSwitchPath = Path.of("src", "components", "call-screen", "CallScreenAdModeSwitch.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(modeSwitchPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);
        String nav = Files.readString(navPath);
        String modeSwitch = Files.readString(modeSwitchPath);

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
            .contains("getPlatformCallScreenMediaSeed")
            .contains("updatePlatformCallScreenMediaSeed")
            .contains("uploadPlatformCallScreenMedia")
            .contains("/api/v1/platform/call-screen/text-seed")
            .contains("/api/v1/platform/call-screen/media-seed")
            .contains("/api/v1/platform/call-screen/media")
            .contains("method: 'GET'")
            .contains("method: 'PATCH'")
            .doesNotContain("tenantId")
            .doesNotContain("storeId")
            .doesNotContain("mock");

        assertThat(types)
            .contains("interface PlatformCallScreenSeedSet")
            .contains("interface PlatformCallScreenSeedSlide")
            .contains("interface PlatformCallScreenMediaSeedSet")
            .contains("interface PlatformCallScreenMediaSeedSlide")
            .contains("mediaKind")
            .contains("mediaUrl")
            .contains("interface PlatformCallScreenSeedMutation")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");

        assertThat(page)
            .contains("平台叫号模板")
            .contains("getPlatformProfile")
            .contains("previewLogoUrl")
            .contains("showPreviewLogoImage")
            .contains("preview-logo-image")
            .contains("handlePreviewLogoError")
            .contains("CallScreenAdModeSwitch")
            .contains("selectedSeedMode")
            .contains("文案种子模板")
            .contains("图片/视频模板")
            .contains("type=\"file\"")
            .contains("accept=\"image/jpeg,image/png,image/webp,video/mp4,video/webm\"")
            .contains("图片不超过 10MB，视频不超过 80MB")
            .contains("MAX_CALL_SCREEN_IMAGE_BYTES")
            .contains("MAX_CALL_SCREEN_VIDEO_BYTES")
            .contains("validateMediaUploadFile")
            .contains("seedSet")
            .contains("mediaSeedSet")
            .contains("slides")
            .contains("mediaSlides")
            .contains("sortOrder")
            .contains("title")
            .contains("subtitle")
            .contains("tagline")
            .contains("updatePlatformCallScreenTextSeed")
            .contains("updatePlatformCallScreenMediaSeed")
            .contains("uploadPlatformCallScreenMedia")
            .contains("removeMediaSlide")
            .contains("@click=\"removeMediaSlide")
            .contains("删除")
            .contains("<video")
            .contains("<img")
            .contains("preview-screen")
            .contains("previewSlides")
            .contains("previewSlideIndex")
            .contains("startPreviewCarousel")
            .contains("selectPreviewSlide")
            .contains("window.setInterval")
            .contains("切换预览文案")
            .contains("previewFullscreenOpen")
            .contains("openPreviewFullscreen")
            .contains("closePreviewFullscreen")
            .contains("preview-fullscreen")
            .contains("大屏预览")
            .contains("关闭预览")
            .doesNotContain(".mode-control")
            .doesNotContain(".mode-option")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");

        assertThat(modeSwitch)
            .contains("modelValue")
            .contains("update:modelValue")
            .contains("textLabel")
            .contains("mediaLabel")
            .contains("call-screen-ad-mode-switch");
    }
}
