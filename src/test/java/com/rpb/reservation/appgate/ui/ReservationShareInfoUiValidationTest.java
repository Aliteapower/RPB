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
        Path shareApiPath = Path.of("src", "api", "reservationShareInfoApi.ts");
        Path adminApiPath = Path.of("src", "api", "tenantAdminShareProfileApi.ts");

        assertThat(adminPagePath).exists();
        assertThat(shareApiPath).exists();
        assertThat(adminApiPath).exists();

        String routerSource = Files.readString(routerPath);
        String navSource = Files.readString(navPath);
        String profilePageSource = Files.readString(profilePagePath);
        String adminPageSource = Files.readString(adminPagePath);
        String createDialogSource = Files.readString(createDialogPath);
        String todayItemSource = Files.readString(todayItemPath);
        String shareApiSource = Files.readString(shareApiPath);
        String adminApiSource = Files.readString(adminApiPath);

        assertThat(routerSource)
            .contains("TenantAdminReservationSharePage")
            .contains("path: '/stores/:storeId/admin/share-template'")
            .contains("name: 'tenant-admin-reservation-share'");
        assertThat(navSource)
            .contains("/stores/${storeId}/admin/share-template")
            .contains("订位分享");
        assertThat(profilePageSource)
            .contains("getTenantAdminShareProfile")
            .contains("updateTenantAdminShareProfile")
            .contains("门店分享资料")
            .contains("分享地址")
            .contains("Google Map 链接")
            .contains("shareAddress")
            .contains("googleMapUrl")
            .contains("usesDefaultReservationShareTemplate");
        assertThat(adminPageSource)
            .contains("getTenantAdminShareProfile")
            .contains("updateTenantAdminShareProfile")
            .contains("previewTenantAdminShareProfile")
            .contains("resetTenantAdminShareProfileTemplate")
            .contains("reservationShareTemplate")
            .contains("availableVariables")
            .contains("googleMapUrl")
            .doesNotContain("【订位确认】");
        assertThat(createDialogSource)
            .contains("getReservationShareInfo")
            .contains("ReservationShareCopyPanel")
            .contains("复制订位信息");
        assertThat(todayItemSource)
            .contains("getReservationShareInfo")
            .contains("ReservationShareCopyPanel")
            .contains("复制订位信息");
        assertThat(shareApiSource)
            .contains("/share-info")
            .contains("shareText")
            .doesNotContain("send")
            .doesNotContain("webhook")
            .doesNotContain("token");
        assertThat(adminApiSource)
            .contains("/tenant-admin/share-profile")
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
