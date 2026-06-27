package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QueueDisplayCallScreenUiImplementationValidationTest {
    @Test
    void queueDisplayTerminalUsesStateApiAndApprovedDisplayStates() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueDisplayPage.vue");
        Path listPagePath = Path.of("src", "pages", "QueueTicketListPage.vue");
        Path apiPath = Path.of("src", "api", "queueDisplayApi.ts");
        Path typesPath = Path.of("src", "types", "queueDisplay.ts");
        Path routerPath = Path.of("src", "router", "index.ts");

        assertThat(pagePath).exists();
        assertThat(listPagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String page = Files.readString(pagePath);
        String listPage = Files.readString(listPagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);

        assertThat(router)
            .contains("QueueDisplayPage")
            .contains("path: '/stores/:storeId/queue-display'")
            .contains("name: 'queue-display'");

        assertThat(apiClient)
            .contains("fetchQueueDisplayState")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/queue-display/state")
            .contains("method: 'GET'")
            .contains("queue.display.unknown_error")
            .doesNotContain("mock")
            .doesNotContain("callQueueTicket")
            .doesNotContain("skipQueueTicket")
            .doesNotContain("seatCalledQueueTicket");

        assertThat(types)
            .contains("interface QueueDisplayStateResponse")
            .contains("currentCall: QueueDisplayCurrentCall | null")
            .contains("interface QueueDisplayAds")
            .contains("slideDurationSeconds")
            .contains("statePollSeconds")
            .contains("displayNumber")
            .contains("customerDisplayName");

        assertThat(page)
            .contains("screenMode")
            .contains("'loading'")
            .contains("'calling'")
            .contains("'advertising'")
            .contains("'error'")
            .contains("当前叫号")
            .contains("waitingCount")
            .contains("textSlides")
            .contains("state.value?.ads")
            .contains("fetchQueueDisplayState")
            .contains("返回管理")
            .contains("mediaSlides")
            .contains("<video")
            .contains("<img")
            .doesNotContain("今日推荐")
            .doesNotContain("特惠活动")
            .doesNotContain("会员专享")
            .doesNotContain("callQueueTicket")
            .doesNotContain("skipQueueTicket")
            .doesNotContain("seatCalledQueueTicket");

        assertThat(listPage)
            .contains("queueDisplayRoute")
            .contains("name: 'queue-display'")
            .contains("target=\"_blank\"")
            .contains("rel=\"noopener noreferrer\"")
            .contains("大屏");
    }

    @Test
    void queueDisplayTerminalSupportsPersistentFullscreenPresentation() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueDisplayPage.vue");

        String page = Files.readString(pagePath);

        assertThat(page)
            .contains("QUEUE_DISPLAY_FULLSCREEN_PREFERENCE_KEY")
            .contains("isQueueDisplayFullscreen")
            .contains("isFullscreenPreferred")
            .contains("fullscreenErrorMessage")
            .contains("requestQueueDisplayFullscreen")
            .contains("exitQueueDisplayFullscreen")
            .contains("toggleQueueDisplayFullscreen")
            .contains("document.fullscreenElement")
            .contains("requestFullscreen")
            .contains("exitFullscreen")
            .contains("fullscreenchange")
            .contains("localStorage")
            .contains("const FULLSCREEN_CONTROLS_HIDE_MS = 5000")
            .contains("areFullscreenControlsVisible")
            .contains("handleFullscreenPointerActivity")
            .contains("blurFullscreenControlFocus")
            .contains("queue-display-terminal--controls-visible")
            .contains(".queue-display-terminal--fullscreen:not(.queue-display-terminal--controls-visible) .terminal-actions")
            .contains("全屏展示")
            .contains("退出全屏")
            .contains("无法自动进入全屏")
            .contains("queue-display-terminal--fullscreen")
            .doesNotContain("isFullscreenControlHovered");
    }

    @Test
    void tenantAdminCallScreenPageUsesTenantAdminApiAndSupportsMediaGroups() throws Exception {
        Path pagePath = Path.of("src", "pages", "TenantAdminCallScreenPage.vue");
        Path apiPath = Path.of("src", "api", "callScreenAdminApi.ts");
        Path typesPath = Path.of("src", "types", "callScreenAdmin.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue");
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
            .contains("TenantAdminCallScreenPage")
            .contains("path: '/stores/:storeId/admin/call-screen'")
            .contains("name: 'tenant-admin-call-screen'")
            .contains("meta: { requiresTenantAdmin: true }");

        assertThat(nav)
            .contains("/admin/call-screen")
            .contains("叫号屏配置");

        assertThat(apiClient)
            .contains("getCallScreenSettings")
            .contains("updateCallScreenSettings")
            .contains("listCallScreenAdSets")
            .contains("updateCallScreenAdSet")
            .contains("/tenant-admin/call-screen")
            .contains("/settings")
            .contains("/ad-sets")
            .contains("uploadCallScreenMedia")
            .contains("/media")
            .doesNotContain("tenantId")
            .doesNotContain("mock");

        assertThat(types)
            .contains("interface CallScreenSettings")
            .contains("interface CallScreenAdSet")
            .contains("interface CallScreenTextSlide")
            .contains("interface CallScreenMediaSlide")
            .contains("mediaKind")
            .contains("mediaUrl")
            .contains("interface CallScreenAdSetMutation")
            .doesNotContain("tenantId")
            .contains("mediaAssetId");

        assertThat(page)
            .contains("叫号屏配置")
            .contains("CallScreenAdModeSwitch")
            .contains("文案轮播")
            .contains("图片/视频轮播")
            .contains("type=\"file\"")
            .contains("accept=\"image/jpeg,image/png,image/webp,video/mp4,video/webm\"")
            .contains("文案编辑")
            .contains("媒体编辑")
            .contains("mediaSlides")
            .contains("sortOrder")
            .contains("title")
            .contains("subtitle")
            .contains("tagline")
            .contains("updateCallScreenSettings")
            .contains("updateCallScreenAdSet")
            .contains("uploadCallScreenMedia")
            .contains("addSlide")
            .contains("editableAdSet.value.slides.push")
            .contains("nextSortOrder")
            .contains("新增一组")
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
            .doesNotContain("creatingAdSet")
            .doesNotContain("tenantId");

        assertThat(modeSwitch)
            .contains("defineProps")
            .contains("modelValue")
            .contains("update:modelValue")
            .contains("文案轮播")
            .contains("图片/视频轮播")
            .contains("call-screen-ad-mode-switch");
    }

    @Test
    void platformCallScreenSeedPageSupportsTextAndMediaSeedTemplates() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformCallScreenSeedPage.vue");
        Path apiPath = Path.of("src", "api", "platformCallScreenSeedApi.ts");
        Path typesPath = Path.of("src", "types", "platformCallScreenSeed.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");
        Path modeSwitchPath = Path.of("src", "components", "call-screen", "CallScreenAdModeSwitch.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(modeSwitchPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String nav = Files.readString(navPath);

        assertThat(nav)
            .contains("叫号模板");

        assertThat(apiClient)
            .contains("getPlatformCallScreenTextSeed")
            .contains("getPlatformCallScreenMediaSeed")
            .contains("updatePlatformCallScreenMediaSeed")
            .contains("uploadPlatformCallScreenMedia")
            .contains("/api/v1/platform/call-screen/media-seed")
            .contains("/api/v1/platform/call-screen/media");

        assertThat(types)
            .contains("interface PlatformCallScreenMediaSeedSlide")
            .contains("mediaKind")
            .contains("mediaUrl")
            .contains("mediaAssetId")
            .contains("'image' | 'video'");

        assertThat(page)
            .contains("CallScreenAdModeSwitch")
            .contains("selectedSeedMode")
            .contains("文案模板")
            .contains("图片/视频模板")
            .contains("type=\"file\"")
            .contains("accept=\"image/jpeg,image/png,image/webp,video/mp4,video/webm\"")
            .contains("uploadPlatformCallScreenMedia")
            .contains("getPlatformCallScreenMediaSeed")
            .contains("updatePlatformCallScreenMediaSeed")
            .contains("<video")
            .contains("<img")
            .contains("大屏预览")
            .doesNotContain(".mode-control")
            .doesNotContain(".mode-option")
            .doesNotContain("图片模板（Phase 2）")
            .doesNotContain("图片模板需要先");
    }
}
