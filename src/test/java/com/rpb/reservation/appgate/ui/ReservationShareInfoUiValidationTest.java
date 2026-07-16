package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationShareInfoUiValidationTest {

    @Test
    void systemShareFallbackUsesDistinctCopiedFeedbackFromExplicitCopyAction() throws Exception {
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path todayItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");
        Path enPath = Path.of("src", "i18n", "locales", "en-SG.ts");

        String createDialogSource = FrontendSourceSupport.readString(createDialogPath);
        String todayItemSource = FrontendSourceSupport.readString(todayItemPath);
        String zhSource = FrontendSourceSupport.readString(zhPath);
        String enSource = FrontendSourceSupport.readString(enPath);

        assertThat(zhSource)
            .contains("systemFallbackCopied: '系统转发不可用，链接已复制'");
        assertThat(enSource)
            .contains("systemFallbackCopied: 'System share unavailable. Link copied.'");
        assertThat(todayItemSource)
            .contains("t('reservationWorkbench.share.systemFallbackCopied')")
            .contains("shareInfoStatusText.value = t('reservationWorkbench.share.copied')");
        assertThat(createDialogSource)
            .contains("t('reservationWorkbench.share.systemFallbackCopied')")
            .contains("createdShareStatusText.value = t('reservationWorkbench.share.copied')");
    }

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
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(adminPagePath).exists();
        assertThat(shareApiPath).exists();
        assertThat(adminApiPath).exists();
        assertThat(publicShareApiPath).exists();
        assertThat(publicShareTypePath).exists();
        assertThat(publicSharePagePath).exists();
        assertThat(shareLauncherPath).exists();
        assertThat(channelSharePayloadPath).exists();

        String routerSource = FrontendSourceSupport.readString(routerPath);
        String navSource = FrontendSourceSupport.readString(navPath);
        String profilePageSource = FrontendSourceSupport.readString(profilePagePath);
        String adminPageSource = FrontendSourceSupport.readString(adminPagePath);
        String createDialogSource = FrontendSourceSupport.readString(createDialogPath);
        String todayItemSource = FrontendSourceSupport.readString(todayItemPath);
        String copyPanelSource = FrontendSourceSupport.readString(copyPanelPath);
        String shareLauncherSource = FrontendSourceSupport.readString(shareLauncherPath);
        String channelSharePayloadSource = FrontendSourceSupport.readString(channelSharePayloadPath);
        String shareApiSource = FrontendSourceSupport.readString(shareApiPath);
        String shareInfoTypeSource = FrontendSourceSupport.readString(shareInfoTypePath);
        String adminApiSource = FrontendSourceSupport.readString(adminApiPath);
        String publicShareApiSource = FrontendSourceSupport.readString(publicShareApiPath);
        String publicShareTypeSource = FrontendSourceSupport.readString(publicShareTypePath);
        String publicSharePageSource = FrontendSourceSupport.readString(publicSharePagePath);
        String localSecuritySource = FrontendSourceSupport.readString(localSecurityPath);
        String zhSource = FrontendSourceSupport.readString(zhPath);

        assertThat(routerSource)
            .contains("ReservationPublicSharePage")
            .contains("path: '/reservation-share/:token'")
            .contains("name: 'reservation-public-share'")
            .contains("TenantAdminReservationSharePage")
            .contains("path: '/stores/:storeId/admin/share-template'")
            .contains("name: 'tenant-admin-reservation-share'")
            .contains("meta: { public: true }");
        assertThat(navSource)
            .contains("/admin/share-template")
            .contains("nav.tenant.shareTemplate");
        assertThat(zhSource)
            .contains("shareTemplate: '订位分享'");
        assertThat(profilePageSource)
            .contains("getTenantAdminShareProfile")
            .contains("updateTenantAdminShareProfile")
            .contains("useI18n({ useScope: 'global' })")
            .contains("activeLocale")
            .contains("getTenantAdminShareProfile(storeId.value, activeLocale.value")
            .contains("updateTenantAdminShareProfile(storeId.value, toSharePayload(), activeLocale.value")
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
            .contains("useI18n({ useScope: 'global' })")
            .contains("activeLocale")
            .contains("getTenantAdminShareProfile(storeId.value, activeLocale.value")
            .contains("updateTenantAdminShareProfile(storeId.value, toShareMutation(), activeLocale.value")
            .contains("previewTenantAdminShareProfile(storeId.value, toShareMutation(), activeLocale.value")
            .contains("resetTenantAdminShareProfileTemplate(storeId.value, activeLocale.value")
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
            .contains("generated.tenant-admin-reservation-share.018")
            .contains("generated.tenant-admin-reservation-share.019")
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
            .contains("reservationWorkbench.share.whatsapp")
            .contains("reservationWorkbench.share.wechat")
            .contains("reservationWorkbench.share.system")
            .contains("reservationWorkbench.share.copy")
            .contains("reservationWorkbench.share.prepared")
            .contains("resolvedStatusText");
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
            .contains("locale?: string")
            .contains("searchParams.set('locale'")
            .contains("suffix = ''")
            .contains("'/intent'")
            .doesNotContain("send")
            .doesNotContain("webhook");
        assertThat(shareInfoTypeSource)
            .contains("wechatShareText?: string | null");
        assertThat(publicShareApiSource)
            .contains("/api/v1/public/reservation-shares")
            .contains("ReservationPublicShareApiError")
            .contains("TOKEN_EXPIRED")
            .contains("locale?: string")
            .contains("searchParams.set('locale'")
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
            .contains("useI18n({ useScope: 'global' })")
            .contains("activeLocale")
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
            .contains("locale?: string")
            .contains("requestArgs(localeOrFetcher, fetcher)")
            .contains("shareProfileEndpoint(storeId, args.locale)")
            .contains("searchParams.set('locale'")
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
            return FrontendSourceSupport.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
