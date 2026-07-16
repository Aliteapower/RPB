package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffWorkbenchBrandIdentityUiValidationTest {
    private static final List<Path> TOP_BAR_PAGES = List.of(
        Path.of("src", "pages", "StoreStaffHomePage.vue"),
        Path.of("src", "pages", "ReservationTodayViewPage.vue"),
        Path.of("src", "pages", "QueueTicketListPage.vue"),
        Path.of("src", "pages", "TableResourceListPage.vue"),
        Path.of("src", "pages", "WalkInQueuePage.vue"),
        Path.of("src", "pages", "WalkInDirectSeatingPage.vue"),
        Path.of("src", "pages", "ReservationArrivedToQueuePage.vue"),
        Path.of("src", "pages", "SeatingFromCalledQueuePage.vue")
    );

    @Test
    void authorizedStoreContractAndResolverProvideDynamicBrandFallbacks() throws Exception {
        Path resolverPath = Path.of("src", "components", "staff-home", "staffBrandIdentity.ts");
        assertThat(resolverPath).exists();

        String authTypes = FrontendSourceSupport.readString(Path.of("src", "types", "auth.ts"));
        String resolver = FrontendSourceSupport.readString(resolverPath);

        assertThat(authTypes)
            .contains("shareDisplayName: string | null")
            .contains("tenantLogoMediaUrl: string | null");
        assertThat(resolver)
            .contains("export function resolveStaffBrandIdentity")
            .contains("store?.shareDisplayName")
            .contains("store?.storeName")
            .contains("fallbackLabel")
            .contains("genericLabel")
            .contains("Array.from(displayName)[0]")
            .contains("store?.tenantLogoMediaUrl");
    }

    @Test
    void topBarComposesReusableBrandForAnExplicitStoreScope() throws Exception {
        Path brandPath = Path.of("src", "components", "staff-home", "StaffBrandIdentity.vue");
        assertThat(brandPath).exists();

        String topBar = FrontendSourceSupport.readString(Path.of(
            "src", "components", "staff-home", "StaffHomeTopBar.vue"
        ));
        String brand = FrontendSourceSupport.readString(brandPath);

        assertThat(topBar)
            .contains("storeId: string")
            .contains("useAuthSessionStore")
            .contains("auth.ensureAuthorizedStores()")
            .contains("store.storeId === props.storeId")
            .contains("resolveStaffBrandIdentity")
            .contains("<StaffBrandIdentity")
            .doesNotContain("t('staffHome.topbar.brandMark')")
            .doesNotContain("t('staffHome.topbar.title')");
        assertThat(brand)
            .contains("@error=\"handleLogoError\"")
            .contains("watch(() => props.logoMediaUrl")
            .contains("object-fit: contain;")
            .contains("text-overflow: ellipsis;")
            .contains("{{ fallbackMark }}");
    }

    @Test
    void everyStaffTopBarPassesItsExistingStoreId() throws Exception {
        for (Path page : TOP_BAR_PAGES) {
            assertThat(FrontendSourceSupport.readString(page))
                .as(page.toString())
                .contains("<StaffHomeTopBar")
                .contains(":store-id=\"storeId\"");
        }
    }

    @Test
    void genericFallbackAndLogoTextAreLocalizedWithoutFixedProductBrand() throws Exception {
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(zh)
            .contains("fallbackTitle: '门店管理'")
            .contains("logoAlt: '{name} 品牌标志'")
            .doesNotContain("title: '食刻 · 管理'");
        assertThat(en)
            .contains("fallbackTitle: 'Store management'")
            .contains("logoAlt: '{name} brand logo'")
            .doesNotContain("title: 'Shike Ops'");
    }
}
