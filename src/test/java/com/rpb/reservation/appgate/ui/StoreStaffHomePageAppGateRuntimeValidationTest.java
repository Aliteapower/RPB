package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoreStaffHomePageAppGateRuntimeValidationTest {
    private static final String LOCAL_VALIDATION_TENANT_ID = "10000000-0000-0000-0000-000000000983";
    private static final String LOCAL_VALIDATION_STORE_ID = "20000000-0000-0000-0000-000000000983";

    @Test
    void staffHomeUsesPersistentOverviewApiInsteadOfDuplicatedEntryGrid() throws Exception {
        String source = readStaffHomeSources();

        assertThat(source)
            .contains("getStaffHomeOverview")
            .contains("StaffHomeOverviewApiError")
            .contains("StaffHomeTopBar")
            .contains("StaffBottomNav")
            .contains("overview")
            .contains("primaryKpis")
            .contains("partySizeGroups")
            .contains("activeQueueTickets")
            .contains("arrivedReservationGroups")
            .contains(":business-date=\"displayedBusinessDate\"")
            .contains("active-tab=\"home\"");

        assertThat(source)
            .doesNotContain("fetchMeApps")
            .doesNotContain("StaffHomeActionGroup")
            .doesNotContain("StaffHomeWorkflowStrip")
            .doesNotContain("StaffHomeActionItem")
            .doesNotContain("<nav v-if=\"hasVisibleOperation\"")
            .doesNotContain("const receptionActions")
            .doesNotContain("const reservationActions")
            .doesNotContain("const queueActions")
            .doesNotContain("const tableTurnoverActions");
    }

    @Test
    void staffHomeFocusesOnTodayOperationalJudgementNotBackofficeBi() throws Exception {
        String source = readStaffHomeSources();

        assertAppearsInOrder(
            source,
            "label: '今日预约'",
            "label: '已到店'",
            "label: '当前排队'",
            "label: '可用桌台'"
        );

        assertThat(source)
            .contains("今日概览")
            .contains("当前排队人数组")
            .contains("桌台状态")
            .contains("waitingTickets")
            .contains("calledTickets")
            .contains("availableTables")
            .contains("temporaryGroups")
            .doesNotContain("周趋势")
            .doesNotContain("月趋势")
            .doesNotContain("报表")
            .doesNotContain("BI");
    }

    @Test
    void staffHomeNewUiDoesNotIntroducePrototypeOrWorkbenchArtifacts() throws Exception {
        String source = readStaffHomeSources();

        assertThat(source)
            .doesNotContain("Queue Display")
            .doesNotContain("排队工作台")
            .doesNotContain("叫号屏")
            .doesNotContain("重新入队")
            .doesNotContain("过号处理")
            .doesNotContain("Table Map")
            .doesNotContain("Reservation Calendar")
            .doesNotContain("ActionSheet")
            .doesNotContain("table-grid")
            .doesNotContain("screen-overlay")
            .doesNotContain("addEventListener")
            .doesNotContain("fake")
            .doesNotContain("Font Awesome")
            .doesNotContain("大屏");
    }

    @Test
    void localValidationDefaultsUseSingleStoreBaseline() throws Exception {
        String routerSource = Files.readString(Path.of("src", "router", "index.ts"));
        String storeContextSource = Files.readString(Path.of("src", "stores", "storeContext.ts"));
        String handoffSource = Files.readString(Path.of("docs", "frontend", "STORE_STAFF_OPERATIONAL_HANDOFF.md"));

        assertThat(routerSource)
            .contains(LOCAL_VALIDATION_STORE_ID)
            .doesNotContain("00000000-0000-0000-0000-000000000000");
        assertThat(storeContextSource)
            .contains(LOCAL_VALIDATION_STORE_ID);
        assertThat(handoffSource)
            .contains(LOCAL_VALIDATION_TENANT_ID)
            .contains(LOCAL_VALIDATION_STORE_ID)
            .contains("queue.view");
    }

    private static void assertAppearsInOrder(String source, String... expectedFragments) {
        int cursor = -1;

        for (String expectedFragment : expectedFragments) {
            int nextIndex = source.indexOf(expectedFragment, cursor + 1);

            assertThat(nextIndex)
                .as("Expected `%s` to appear after index %s", expectedFragment, cursor)
                .isGreaterThan(cursor);
            cursor = nextIndex;
        }
    }

    private static String readStaffHomeSources() throws Exception {
        StringBuilder source = new StringBuilder();

        for (Path path : List.of(
            Path.of("src", "pages", "StoreStaffHomePage.vue"),
            Path.of("src", "components", "staff-home", "StaffHomeTopBar.vue"),
            Path.of("src", "components", "staff-home", "useCurrentClock.ts")
        )) {
            if (Files.exists(path)) {
                source.append(Files.readString(path)).append('\n');
            }
        }

        return source.toString();
    }
}
