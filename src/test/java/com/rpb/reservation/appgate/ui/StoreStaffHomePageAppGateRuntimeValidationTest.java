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
    void staffHomeRendersReservationQueueOperationsOnlyBehindMeAppsEntry() throws Exception {
        String source = readStaffHomeSources();

        assertThat(source)
            .contains("fetchMeApps")
            .contains("reservation_queue")
            .contains("const hasReservationQueue")
            .contains("const hasVisibleOperation")
            .contains("const hasReceptionOperations")
            .contains("const hasReservationOperations")
            .contains("const hasQueueOperations")
            .contains("const hasTableTurnoverOperations")
            .contains("StaffHomeActionGroup")
            .contains("StaffHomeTopBar")
            .contains("StaffHomeWorkflowStrip")
            .contains("<nav v-if=\"hasVisibleOperation\" class=\"operation-groups\"")
            .contains("v-if=\"actions.length\"")
            .doesNotContain("class=\"operation-section\"")
            .doesNotContain("section-eyebrow")
            .contains("reservation.check_in")
            .contains("reservation.seat");
        assertThat(source)
            .doesNotContain("<nav class=\"operation-groups\"")
            .doesNotContain("queue.skip")
            .doesNotContain("queue.rejoin");
    }

    @Test
    void staffHomeLightweightWorkbenchPreservesGroupedTenEntryBaseline() throws Exception {
        String source = readStaffHomeSources();

        assertAppearsInOrder(
            source,
            "const receptionActions",
            "label: '散客直接入座'",
            "label: '预约到店'",
            "const reservationActions",
            "label: '创建预约'",
            "label: '今日预约'",
            "label: '预约排队'",
            "label: '预约入座'",
            "const queueActions",
            "label: '排队列表'",
            "label: '排队叫号'",
            "label: '排队入座'",
            "const tableTurnoverActions",
            "label: '清台处理'"
        );

        assertThat(source)
            .contains("walkInRoute")
            .contains("cleaningRoute")
            .contains("reservationRoute")
            .contains("reservationTodayViewRoute")
            .contains("reservationCheckInRoute")
            .contains("reservationArrivedToQueueRoute")
            .contains("queueTicketListRoute")
            .contains("queueCallRoute")
            .contains("seatingFromCalledQueueRoute")
            .contains("reservationArrivedDirectSeatingRoute")
            .contains("group-id=\"staff-section-reception\"")
            .contains("heading=\"接待\"")
            .contains(":actions=\"receptionActions\"")
            .contains("group-id=\"staff-section-reservation\"")
            .contains("heading=\"预约管理\"")
            .contains(":actions=\"reservationActions\"")
            .contains("group-id=\"staff-section-queue\"")
            .contains("heading=\"排队管理\"")
            .contains(":actions=\"queueActions\"")
            .contains("group-id=\"staff-section-table-turnover\"")
            .contains("heading=\"桌台流转\"")
            .contains(":actions=\"tableTurnoverActions\"");
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
            Path.of("src", "components", "staff-home", "StaffHomeActionGroup.vue"),
            Path.of("src", "components", "staff-home", "StaffHomeTopBar.vue"),
            Path.of("src", "components", "staff-home", "StaffHomeWorkflowStrip.vue"),
            Path.of("src", "components", "staff-home", "staffHomeActions.ts"),
            Path.of("src", "components", "staff-home", "useCurrentClock.ts")
        )) {
            if (Files.exists(path)) {
                source.append(Files.readString(path)).append('\n');
            }
        }

        return source.toString();
    }
}
