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
        Path shareApiPath = Path.of("src", "api", "reservationShareInfoApi.ts");
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

        String routerSource = Files.readString(routerPath);
        String navSource = Files.readString(navPath);
        String profilePageSource = Files.readString(profilePagePath);
        String adminPageSource = Files.readString(adminPagePath);
        String createDialogSource = Files.readString(createDialogPath);
        String todayItemSource = Files.readString(todayItemPath);
        String copyPanelSource = Files.readString(copyPanelPath);
        String shareApiSource = Files.readString(shareApiPath);
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
            .contains("updateTenantAdminShareProfileTemplate")
            .contains("previewTenantAdminShareProfile")
            .contains("resetTenantAdminShareProfileTemplate")
            .contains("reservationShareTemplate")
            .contains("availableVariables")
            .doesNotContain("googleMapUrl")
            .doesNotContain("shareAddress")
            .doesNotContain("shareContactPhone")
            .doesNotContain("reservationShareNote")
            .doesNotContain("<span>分享显示名称</span>")
            .doesNotContain("<span>分享地址</span>")
            .doesNotContain("<span>Google Map 链接</span>")
            .doesNotContain("<span>到店提示</span>")
            .doesNotContain("v-model.trim=\"form.shareDisplayName\"")
            .doesNotContain("v-model.trim=\"form.shareAddress\"")
            .doesNotContain("v-model.trim=\"form.googleMapUrl\"")
            .doesNotContain("v-model.trim=\"form.shareContactPhone\"")
            .doesNotContain("v-model.trim=\"form.reservationShareNote\"")
            .doesNotContain("【订位确认】");
        assertThat(createDialogSource)
            .contains("getReservationShareInfo")
            .contains("ReservationShareCopyPanel")
            .contains("shareCreatedReservationLink")
            .contains("navigator.share")
            .contains("sharePath")
            .contains("转发订位链接");
        assertThat(todayItemSource)
            .contains("getReservationShareInfo")
            .contains("ReservationShareCopyPanel")
            .contains("shareReservationLink")
            .contains("navigator.share")
            .contains("sharePath")
            .contains("转发订位链接");
        assertThat(copyPanelSource)
            .contains("share-requested")
            .contains("buttonText: '转发订位链接'")
            .contains("已准备链接")
            .doesNotContain("copy-requested");
        assertThat(shareApiSource)
            .contains("/share-info")
            .contains("shareText")
            .contains("shareToken")
            .contains("sharePath")
            .doesNotContain("send")
            .doesNotContain("webhook");
        assertThat(publicShareApiSource)
            .contains("/api/v1/public/reservation-shares")
            .contains("ReservationPublicShareApiError")
            .contains("TOKEN_EXPIRED")
            .doesNotContain("Authorization")
            .doesNotContain("currentActor");
        assertThat(publicShareTypeSource)
            .contains("ReservationPublicShare")
            .contains("tablePending")
            .contains("shareTitle")
            .contains("shareSummary")
            .doesNotContain("tenantId")
            .doesNotContain("customerPhone");
        assertThat(publicSharePageSource)
            .contains("getReservationPublicShare")
            .contains("route.params.token")
            .contains("预约信息")
            .contains("桌位待确认")
            .contains("链接已失效")
            .contains("navigator.share")
            .doesNotContain("StaffBottomNav")
            .doesNotContain("TenantAdminNav");
        assertThat(localSecuritySource)
            .contains(".requestMatchers(HttpMethod.GET, \"/api/v1/public/reservation-shares/*\").permitAll()");
        assertThat(adminApiSource)
            .contains("/tenant-admin/share-profile")
            .contains("/template")
            .contains("/preview")
            .contains("/default-template");
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
