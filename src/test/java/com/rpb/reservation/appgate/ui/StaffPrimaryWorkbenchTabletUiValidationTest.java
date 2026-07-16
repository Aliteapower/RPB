package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffPrimaryWorkbenchTabletUiValidationTest {

    private static final Path SHELL = Path.of(
        "src", "components", "staff", "StaffPrimaryWorkbench.vue"
    );

    @Test
    void sharedPrimaryWorkbenchOwnsOnlyAdaptiveShellAndNavigation() throws Exception {
        assertThat(SHELL).exists();

        String shell = FrontendSourceSupport.readString(SHELL);
        String nav = FrontendSourceSupport.readString(
            Path.of("src", "components", "staff", "StaffBottomNav.vue")
        );
        String baseShell = FrontendSourceSupport.readString(
            Path.of("src", "styles", "staffWorkbench.css")
        );

        assertThat(shell)
            .contains("storeId: string")
            .contains("activeTab: StaffBottomNavTab")
            .contains("<slot />")
            .contains("display-mode=\"adaptive-primary\"")
            .contains("@media (min-width: 768px)")
            .contains("grid-template-columns: 88px minmax(0, 1fr);")
            .contains("max-width: 1200px;")
            .contains("overflow-x: clip;")
            .contains("padding-right: 116px;")
            .doesNotContain("fetch(")
            .doesNotContain("watch(")
            .doesNotContain("onMounted(");

        assertThat(nav)
            .contains("displayMode?: 'bottom' | 'adaptive-primary'")
            .contains("staff-bottom-nav--adaptive-primary")
            .contains("position: sticky;")
            .contains("height: 100dvh;")
            .contains("grid-template-columns: minmax(0, 1fr);");

        assertThat(baseShell)
            .contains("max-width: 520px;")
            .doesNotContain("max-width: 1200px;");
    }

    @Test
    void exactlyFourPrimaryPagesAdoptTheSharedWorkbench() throws Exception {
        List<Path> primaryPages = List.of(
            Path.of("src", "pages", "StoreStaffHomePage.vue"),
            Path.of("src", "pages", "ReservationTodayViewPage.vue"),
            Path.of("src", "pages", "QueueTicketListPage.vue"),
            Path.of("src", "pages", "TableResourceListPage.vue")
        );
        List<Path> excludedPages = List.of(
            Path.of("src", "pages", "LoginPage.vue"),
            Path.of("src", "pages", "WalkInQueuePage.vue"),
            Path.of("src", "pages", "WalkInDirectSeatingPage.vue"),
            Path.of("src", "pages", "ReservationCheckInPage.vue"),
            Path.of("src", "pages", "SeatingFromCalledQueuePage.vue")
        );

        for (Path page : primaryPages) {
            assertThat(FrontendSourceSupport.readString(page))
                .as("%s should use the primary workbench", page)
                .contains("StaffPrimaryWorkbench")
                .doesNotContain("<StaffBottomNav");
        }
        for (Path page : excludedPages) {
            assertThat(FrontendSourceSupport.readString(page))
                .as("%s should stay outside the primary workbench", page)
                .doesNotContain("StaffPrimaryWorkbench");
        }

        List<Path> adopters = new ArrayList<>();
        try (var pages = Files.list(Path.of("src", "pages"))) {
            for (Path page : pages.filter(path -> path.toString().endsWith("Page.vue")).toList()) {
                if (FrontendSourceSupport.readString(page).contains("StaffPrimaryWorkbench")) {
                    adopters.add(page);
                }
            }
        }
        assertThat(adopters).containsExactlyInAnyOrderElementsOf(primaryPages);
    }

    @Test
    void homeAndReservationDefinePortraitAndLandscapeLayouts() throws Exception {
        String home = FrontendSourceSupport.readString(
            Path.of("src", "pages", "StoreStaffHomePage.vue")
        );
        String reservation = FrontendSourceSupport.readString(
            Path.of("src", "pages", "ReservationTodayViewPage.vue")
        );

        assertThat(home)
            .contains("@media (min-width: 768px)")
            .contains("grid-template-columns: repeat(4, minmax(0, 1fr));")
            .contains("@media (min-width: 1024px)")
            .contains(".overview-section")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr));");

        assertThat(reservation)
            .contains("class=\"reservation-workbench__date-panel\"")
            .contains("class=\"reservation-workbench__quick-panel\"")
            .contains("class=\"reservation-workbench__list-panel\"")
            .contains("@media (min-width: 768px)")
            .doesNotContain("grid-template-columns: minmax(280px, 0.38fr) minmax(0, 0.62fr);")
            .doesNotContain("grid-column: 2;");
    }

    @Test
    void queueAndTableDefineDeterministicTabletGrids() throws Exception {
        String queue = FrontendSourceSupport.readString(
            Path.of("src", "pages", "QueueTicketListPage.vue")
        );
        String table = FrontendSourceSupport.readString(
            Path.of("src", "pages", "TableResourceListPage.vue")
        );

        assertThat(queue)
            .contains("@media (min-width: 768px)")
            .doesNotContain("grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);")
            .contains("@media (min-width: 1200px)")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr));");

        assertThat(table)
            .contains("@media (min-width: 768px)")
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr));")
            .contains("@media (min-width: 1024px)")
            .contains("grid-template-columns: repeat(4, minmax(0, 1fr));")
            .contains("grid-template-columns: minmax(160px, 0.8fr) minmax(220px, 1fr) minmax(260px, 1.4fr);");
    }
}
