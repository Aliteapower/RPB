package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationShareInfoUiValidationTest {

    @Test
    void tenantAdminRouteAndWorkbenchUseBackendGeneratedManualCopyShareText() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue");
        Path profilePagePath = Path.of("src", "pages", "TenantAdminProfilePage.vue");
        Path adminPagePath = Path.of("src", "pages", "TenantAdminReservationSharePage.vue");
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path todayItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path copyPanelPath = Path.of("src", "components", "reservation-workbench", "ReservationShareCopyPanel.vue");
        Path shareLauncherPath = Path.of("src", "utils", "reservationShareLauncher.ts");
        Path channelSharePayloadPath = Path.of("src", "utils", "reservationChannelSharePayload.ts");
        Path shareApiPath = Path.of("src", "api", "reservationShareInfoApi.ts");
        Path shareInfoTypePath = Path.of("src", "types", "reservationShareInfo.ts");
        Path adminApiPath = Path.of("src", "api", "tenantAdminShareProfileApi.ts");
        Path publicShareApiPath = Path.of("src", "api", "reservationPublicShareApi.ts");
        Path publicShareTypePath = Path.of("src", "types", "reservationPublicShare.ts");
        Path publicSharePagePath = Path.of("src", "pages", "ReservationPublicSharePage.vue");
        Path localSecurityPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "walkin", "auth", "LocalRuntimeSecurityConfiguration.java");

        assertThat(adminPagePath).exists();
        assertThat(shareApiPath).exists();
        assertThat(adminApiPath).exists();
        assertThat(publicShareApiPath).exists();
        assertThat(publicShareTypePath).exists();
        assertThat(publicSharePagePath).exists();
        assertThat(shareLauncherPath).exists();
        assertThat(channelSharePayloadPath).exists();

        String routerSource = Files.readString(routerPath);
        String navSource = Files.readString(navPath);
        String profilePageSource = Files.readString(profilePagePath);
        String adminPageSource = Files.readString(adminPagePath);
        String createDialogSource = Files.readString(createDialogPath);
        String todayItemSource = Files.readString(todayItemPath);
        String copyPanelSource = Files.readString(copyPanelPath);
        String shareLauncherSource = Files.readString(shareLauncherPath);
        String channelSharePayloadSource = Files.readString(channelSharePayloadPath);
        String shareApiSource = Files.readString(shareApiPath);
        String shareInfoTypeSource = Files.readString(shareInfoTypePath);
        String adminApiSource = Files.readString(adminApiPath);
        String publicShareApiSource = Files.readString(publicShareApiPath);
        String publicShareTypeSource = Files.readString(publicShareTypePath);
        String publicSharePageSource = Files.readString(publicSharePagePath);
        String localSecuritySource = Files.readString(localSecurityPath);

        assertThat(routerSource)
            .contains("ReservationPublicSharePage")
            .contains("path: '/reservation-share/:token'")
            .contains("name: 'reservation-public-share'")
            .contains("TenantAdminReservationSharePage")
            .contains("path: '/stores/:storeId/admin/share-template'")
            .contains("name: 'tenant-admin-reservation-share'")
            .contains("meta: { public: true }");
        assertThat(navSource)
            .contains("/stores/${storeId}/admin/share-template")
            .contains("订位分享");
        assertThat(profilePageSource)
            .contains("getTenantAdminShareProfile")
            .contains("updateTenantAdminShareProfile")
            .contains("门店分享资料")
            .contains("Google Map 链接")
            .contains("到店提示")
            .contains("googleMapUrl")
            .contains("usesDefaultReservationShareTemplate")
            .doesNotContain("shareAddress: optionalValue(form.address)")
            .doesNotContain("shareContactPhone: optionalValue(form.contactPhone)")
            .doesNotContain("v-model.trim=\"shareForm.shareAddress\"")
            .doesNotContain("v-model.trim=\"shareForm.shareContactPhone\"");
        assertThat(adminPageSource)
            .contains("getTenantAdminShareProfile")
            .contains("updateTenantAdminShareProfile")
            .contains("previewTenantAdminShareProfile")
            .contains("resetTenantAdminShareProfileTemplate")
            .contains("reservationShareTemplate")
            .contains("availableVariables")
            .contains("公网预约设置")
            .contains("<span>分享显示名称</span>")
            .contains("<span>Google Map 链接</span>")
            .contains("<span>对外邮箱</span>")
            .contains("<span>WhatsApp 固定号码</span>")
            .contains("<span>到店提示</span>")
            .contains("v-model.trim=\"form.shareDisplayName\"")
            .contains("v-model.trim=\"form.googleMapUrl\"")
            .contains("v-model.trim=\"form.shareEmail\"")
            .contains("v-model.trim=\"form.whatsappBusinessPhoneE164\"")
            .contains("v-model.trim=\"form.reservationShareNote\"")
            .contains("点击字段会把 &#123;&#123;字段名&#125;&#125; 加入到分享模板中")
            .contains("保存后的模板会在新的订位分享中生效")
            .doesNotContain("ReservationPublicSharePage")
            .doesNotContain("getReservationPublicShare")
            .doesNotContain("/reservation-share/")
            .doesNotContain("/api/v1/public/reservation-shares")
            .doesNotContain("食刻")
            .doesNotContain("上海市")
            .doesNotContain("shareAddress")
            .doesNotContain("shareContactPhone")
            .doesNotContain("<span>分享地址</span>")
            .doesNotContain("v-model.trim=\"form.shareAddress\"")
            .doesNotContain("v-model.trim=\"form.shareContactPhone\"")
            .doesNotContain("【订位确认】");
        assertThat(createDialogSource)
            .contains("getReservationShareInfo")
            .contains("recordReservationShareIntent")
            .contains("ReservationShareCopyPanel")
            .contains("shareCreatedReservationLink")
            .contains("shareLinkOrCopy")
            .contains("openWhatsApp")
            .contains("openWechat")
            .contains("reservationWechatShareText")
            .contains("sharePath")
            .doesNotContain("const text = info?.wechatShareText?.trim()")
            .doesNotContain("navigator.share");
        assertThat(todayItemSource)
            .contains("getReservationShareInfo")
            .contains("recordReservationShareIntent")
            .contains("ReservationShareCopyPanel")
            .contains("shareReservationLink")
            .contains("shareLinkOrCopy")
            .contains("openWhatsApp")
            .contains("openWechat")
            .contains("reservationWechatShareText")
            .contains("sharePath")
            .doesNotContain("const text = info?.wechatShareText?.trim()")
            .doesNotContain("navigator.share");
        assertThat(copyPanelSource)
            .contains("whatsapp-requested")
            .contains("wechat-requested")
            .contains("system-share-requested")
            .contains("copy-requested")
            .contains("WhatsApp发送")
            .contains("微信发送")
            .contains("系统转发")
            .contains("复制链接")
            .contains("statusText: '已准备链接'")
            .contains("已准备链接");
        assertThat(shareLauncherSource)
            .contains("shareLinkOrCopy")
            .contains("canUseNativeShare")
            .contains("navigator.share")
            .contains("navigator.canShare")
            .contains("maxTouchPoints")
            .contains("matchMedia('(pointer: coarse)')")
            .contains("copyPlainText(payload.url)");
        assertThat(channelSharePayloadSource)
            .contains("reservationWechatShareText")
            .contains("info.wechatShareText?.trim()")
            .contains("info.shareText?.trim()")
            .contains("shareUrl.trim()")
            .contains("return text || url");
        assertThat(shareApiSource)
            .contains("/share-info")
            .contains("shareText")
            .contains("shareToken")
            .contains("sharePath")
            .contains("recordReservationShareIntent")
            .contains("}/intent`")
            .doesNotContain("send")
            .doesNotContain("webhook");
        assertThat(shareInfoTypeSource)
            .contains("wechatShareText?: string | null");
        assertThat(publicShareApiSource)
            .contains("/api/v1/public/reservation-shares")
            .contains("ReservationPublicShareApiError")
            .contains("TOKEN_EXPIRED")
            .doesNotContain("Authorization")
            .doesNotContain("currentActor");
        assertThat(publicShareTypeSource)
            .contains("ReservationPublicShare")
            .contains("tablePending")
            .contains("shareText")
            .contains("shareTitle")
            .contains("shareSummary")
            .doesNotContain("tenantId")
            .doesNotContain("customerPhone");
        assertThat(publicSharePageSource)
            .contains("getReservationPublicShare")
            .contains("route.params.token")
            .contains("预约信息")
            .contains("链接已失效")
            .contains("reservation-public-share__intro")
            .contains("customerIntroText")
            .contains("reservation-public-share__focus")
            .contains("reservation-public-share__datetime")
            .contains("reservation-public-share__table")
            .contains("customerVisibleShareText")
            .contains("tableLabel")
            .contains("share.reservationDate")
            .contains("share.reservationTime")
            .contains("share.partySize")
            .contains("日期")
            .contains("时间")
            .contains("桌位")
            .contains("reservation-public-share__template")
            .contains("reservation-public-share__contact-actions")
            .contains("share.googleMapUrl")
            .contains("打开地图")
            .contains("share.storePhone")
            .contains(":href=\"`tel:${share.storePhone}`\"")
            .contains("拨打电话")
            .contains("share.storeEmail")
            .contains(":href=\"`mailto:${share.storeEmail}`\"")
            .contains("发送邮件")
            .contains("share.storeWhatsappPhone")
            .contains("whatsappContactUrl")
            .contains("WhatsApp")
            .contains("shareLinkOrCopy")
            .contains("reservation-public-share__actions")
            .doesNotContain("reservation-public-share__details")
            .doesNotContain("reservation-public-share__store")
            .doesNotContain("{{ share.shareText }}")
            .doesNotContain("share.reservationNo")
            .doesNotContain("预订编号")
            .doesNotContain("aria-label=\"订位摘要\"")
            .doesNotContain("aria-label=\"门店信息\"")
            .doesNotContain("navigator.share")
            .doesNotContain("StaffBottomNav")
            .doesNotContain("TenantAdminNav");
        assertThat(publicSharePageSource.indexOf("reservation-public-share__intro"))
            .isLessThan(publicSharePageSource.indexOf("reservation-public-share__focus"));
        assertThat(publicSharePageSource.indexOf("reservation-public-share__focus"))
            .isLessThan(publicSharePageSource.indexOf("reservation-public-share__template"));
        assertThat(localSecuritySource)
            .contains(".requestMatchers(HttpMethod.GET, \"/api/v1/public/reservation-shares/*\").permitAll()");
        assertThat(adminApiSource)
            .contains("/tenant-admin/share-profile")
            .contains("/template")
            .contains("/preview")
            .contains("/default-template")
            .doesNotContain("/reservation-share");
    }

    @Test
    void frontendDoesNotUseWhatsAppAutomationOrGoogleMapsSdk() throws Exception {
        String frontendSource = Files.walk(Path.of("src"))
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".vue") || path.toString().endsWith(".ts"))
            .map(ReservationShareInfoUiValidationTest::readUnchecked)
            .reduce("", String::concat);

        assertThat(frontendSource)
            .doesNotContain("WhatsApp Cloud API")
            .doesNotContain("webhook")
            .doesNotContain("accessToken")
            .doesNotContain("google.maps")
            .doesNotContain("maps.googleapis.com");
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
