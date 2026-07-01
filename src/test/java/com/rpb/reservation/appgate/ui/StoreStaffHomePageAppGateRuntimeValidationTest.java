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
    void staffHomeTopBarProvidesLogoutActionUsingSharedAuthSession() throws Exception {
        String source = Files.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"))
            + Files.readString(Path.of("src", "components", "staff-home", "StaffHomeTopBar.vue"));

        assertThat(source)
            .contains("useRouter")
            .contains("const router = useRouter()")
            .contains("const loggingOut = ref(false)")
            .contains("async function logoutFromStaffHome(): Promise<void>")
            .contains("await authSession.logoutCurrentUser()")
            .contains("await router.push({ name: 'login' })")
            .contains("<slot name=\"utility\" />")
            .contains("<template #utility>")
            .contains("class=\"topbar-actions\"")
            .contains("class=\"topbar-logout\"")
            .contains("{{ loggingOut ? '退出中' : '退出登录' }}")
            .contains("@click=\"logoutFromStaffHome\"")
            .contains(":disabled=\"loggingOut\"");
    }

    @Test
    void staffPagesShowFriendlyAppGateSubscriptionMessages() throws Exception {
        String staffHomeSource = Files.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"));
        String todayListSource = Files.readString(Path.of(
            "src",
            "components",
            "reservation-workbench",
            "ReservationTodayListPanel.vue"
        ));
        String queueTicketListSource = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));
        String tableResourceListSource = Files.readString(Path.of("src", "pages", "TableResourceListPage.vue"));
        Path appGateMessagesPath = Path.of("src", "utils", "appGateErrorMessages.ts");

        assertThat(appGateMessagesPath).exists();
        String appGateMessagesSource = Files.readString(appGateMessagesPath);

        assertThat(staffHomeSource)
            .contains("formatAppGateErrorMessage")
            .contains("formatAppGateErrorTitle")
            .doesNotContain("apiError.value?.error.messageKey");

        assertThat(todayListSource)
            .contains("formatAppGateErrorMessage")
            .contains("formatAppGateErrorTitle")
            .doesNotContain("错误代码：{{ apiError.error.code }}")
            .doesNotContain("消息键：{{ apiError.error.messageKey }}");

        assertThat(queueTicketListSource)
            .contains("formatAppGateErrorMessage")
            .contains("formatAppGateErrorTitle")
            .doesNotContain("错误代码：{{ apiError.error.code }}")
            .doesNotContain("消息键：{{ apiError.error.messageKey }}")
            .doesNotContain("错误代码：{{ callApiError.error.code }}")
            .doesNotContain("消息键：{{ callApiError.error.messageKey }}")
            .doesNotContain("错误代码：{{ skipApiError.error.code }}")
            .doesNotContain("消息键：{{ skipApiError.error.messageKey }}")
            .doesNotContain("错误代码：{{ rejoinApiError.error.code }}")
            .doesNotContain("消息键：{{ rejoinApiError.error.messageKey }}")
            .doesNotContain("错误代码：{{ cancelApiError.error.code }}")
            .doesNotContain("消息键：{{ cancelApiError.error.messageKey }}");

        assertThat(tableResourceListSource)
            .contains("formatAppGateErrorMessage")
            .contains("formatAppGateErrorTitle")
            .doesNotContain("<strong>{{ apiError.error.code }}</strong>")
            .doesNotContain("<span>{{ apiError.error.messageKey }}</span>")
            .doesNotContain("<strong>{{ actionError.error.code }}</strong>")
            .doesNotContain("<span>{{ actionError.error.messageKey }}</span>");

        assertThat(appGateMessagesSource)
            .contains("TENANT_APP_NOT_ENABLED")
            .contains("预约排队叫号系统未开通")
            .contains("请联系平台管理员在租户计费中勾选产品线后再使用")
            .contains("TENANT_APP_EXPIRED")
            .contains("产品线订阅已到期")
            .contains("PERMISSION_DENIED")
            .contains("当前账号没有此功能权限")
            .doesNotContain("错误代码")
            .doesNotContain("消息键");
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
