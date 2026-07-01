package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffReceptionClosedLoopUiValidationTest {

    @Test
    void staffHomeShowsTodayOverviewInsteadOfReceptionActionGrid() throws Exception {
        String staffHome = Files.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"));

        assertThat(staffHome)
            .contains("getStaffHomeOverview")
            .contains("StaffHomeTopBar")
            .contains("StaffBottomNav")
            .contains("displayedBusinessDate")
            .contains("label: '今日预约'")
            .contains("label: '已到店'")
            .contains("label: '当前排队'")
            .contains("label: '可用桌台'")
            .contains("aria-label=\"今日概览\"")
            .contains("aria-label=\"当前排队人数组\"")
            .contains("aria-label=\"桌台状态\"")
            .contains("active-tab=\"home\"");

        assertThat(staffHome)
            .doesNotContain("hasPermission('walkin.queue.create')")
            .doesNotContain("walkInQueueRoute")
            .doesNotContain("StaffHomeActionGroup")
            .doesNotContain("StaffHomeWorkflowStrip")
            .doesNotContain(":layout=\"'three'\"");
    }

    @Test
    void queueListProvidesOneTapCallSkipCancelAndSeatWithoutCopyIdWorkflow() throws Exception {
        String queueList = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));

        assertThat(queueList)
            .contains("callQueueTicket")
            .contains("cancelQueueTicket")
            .contains("queue.cancel")
            .contains("label: '入桌'")
            .contains("name: 'seating-from-called-queue'")
            .contains("queueTicketId: item.queueTicketId")
            .contains("叫号")
            .contains("过号")
            .contains("取消");

        assertThat(queueList)
            .doesNotContain("navigator.clipboard")
            .doesNotContain("复制 ID")
            .doesNotContain("复制id");
    }

    @Test
    void calledQueueTicketShowsRepeatCallBeforeSkip() throws Exception {
        String queueList = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));

        assertThat(queueList)
            .contains("['waiting', 'called', 'rejoined'].includes(item.queueTicketStatus)")
            .contains("item.queueTicketStatus === 'called' ? '重复叫号' : '叫号'");
        assertThat(queueList.indexOf("class=\"call-button\"")).isLessThan(queueList.indexOf("class=\"skip-button\""));
    }

    @Test
    void queueListUsesCurrentReceptionWorkbenchUiInsteadOfLegacyNarrowPage() throws Exception {
        String queueList = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));

        assertThat(queueList)
            .contains("StaffHomeTopBar")
            .contains("queue-workbench-body")
            .contains("queue-management-panel")
            .contains("queue-status-tabs")
            .contains("营业日期 {{ currentBusinessDate }}")
            .contains("当日排队管理")
            .contains("compact-ticket-card")
            .contains("compact-ticket-main")
            .contains("compact-ticket-actions");

        assertThat(queueList)
            .doesNotContain("返回员工首页")
            .doesNotContain("class=\"raw-status\"")
            .doesNotContain("class=\"debug-row\"")
            .doesNotContain("page-shell")
            .doesNotContain("queue-stats-panel")
            .doesNotContain("pagination-controls")
            .doesNotContain("pageRangeText")
            .doesNotContain("goPrevious")
            .doesNotContain("goNext")
            .doesNotContain("StaffHomeWorkflowStrip")
            .doesNotContain("预约编号")
            .doesNotContain("reservationCode")
            .doesNotContain("<p class=\"section-kicker\">排队管理</p>");
    }

    @Test
    void queueListProvidesReceptionFiltersForTableAreaPartySizeAndPhone() throws Exception {
        String queueList = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));
        String apiClient = Files.readString(Path.of("src", "api", "queueTicketListApi.ts"));
        String types = Files.readString(Path.of("src", "types", "queueTicketList.ts"));

        assertThat(queueList)
            .contains("fetchTableResources")
            .contains("selectedTableArea")
            .contains("tableAreaOptions")
            .contains("compact-queue-filters")
            .contains("compact-filter-controls")
            .contains("桌台分区")
            .contains("全部分区")
            .contains("未指定分区")
            .contains("__unassigned__")
            .contains("selectedPartySizeGroup")
            .contains("queueGroupOptions")
            .contains("人数组")
            .contains("全部人数组")
            .contains("formatQueueGroupCount")
            .contains("组 /")
            .contains("phoneFilter")
            .contains("placeholder=\"手机号\"")
            .contains("query.tableArea = selectedTableArea.value")
            .contains("query.phone = normalizedPhoneFilter.value")
            .doesNotContain("<select v-model=\"selectedPartySizeValue\"")
            .doesNotContain("query.partySize = selectedPartySize.value")
            .doesNotContain("option.count");

        assertThat(apiClient)
            .contains("params.set('tableArea', tableArea)")
            .contains("params.set('phone', phone)")
            .contains("setNumberParam(params, 'partySize', query.partySize)");

        assertThat(types)
            .contains("tableArea?: string")
            .contains("partySize?: number")
            .contains("phone?: string")
            .contains("assignedResourceAreaName?: string | null");
    }

    @Test
    void queueListUsesStableDisplayNumberGuestFallbackAndResourceLabel() throws Exception {
        String queueList = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));
        String apiClient = Files.readString(Path.of("src", "api", "queueTicketListApi.ts"));
        String types = Files.readString(Path.of("src", "types", "queueTicketList.ts"));
        String reservationItem = Files.readString(
            Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue")
        );

        assertThat(types)
            .contains("queueTicketDisplayNumber: string")
            .contains("assignedResourceGroupType?: string | null")
            .contains("assignedResourceLabel?: string | null");

        assertThat(apiClient)
            .contains("typeof candidate.queueTicketDisplayNumber === 'string'")
            .contains("isOptionalString(candidate.assignedResourceGroupType)")
            .contains("isOptionalString(candidate.assignedResourceLabel)");

        assertThat(queueList)
            .contains("queueTicketDisplayText(item)")
            .contains("customerDisplayName(item.customerName)")
            .contains("return '顾客'")
            .contains("item.assignedResourceLabel?.trim()")
            .doesNotContain("#{{ item.queueTicketNumber }}")
            .doesNotContain("确认叫号 #${item.queueTicketNumber}");

        assertThat(reservationItem)
            .contains("item.queueTicketDisplayNumber")
            .doesNotContain("`#${props.item.queueTicketNumber}`");
    }

    @Test
    void routesUseNewWalkInQueuePageAndOldReservationCreatePageIsRemoved() throws Exception {
        String router = Files.readString(Path.of("src", "router", "index.ts"));

        assertThat(router)
            .contains("WalkInQueuePage")
            .contains("path: '/stores/:storeId/walk-ins/queue'")
            .contains("name: 'walk-in-queue'")
            .doesNotContain("ReservationCreatePage");

        assertThat(Files.exists(Path.of("src", "pages", "ReservationCreatePage.vue"))).isFalse();
    }

    @Test
    void walkInQueuePageUsesNewQuickTicketWorkbenchAndRedirectsToQueueList() throws Exception {
        String walkInQueue = Files.readString(Path.of("src", "pages", "WalkInQueuePage.vue"));

        assertThat(walkInQueue)
            .contains("StaffHomeTopBar")
            .contains("StaffHomeWorkflowStrip")
            .contains("walk-in-queue-workbench")
            .contains("quick-ticket-panel")
            .contains("无预约到店取号")
            .contains("快速取号")
            .contains("默认 2 人，可直接取号")
            .contains("router.push(queueTicketListRoute.value)")
            .contains("queueWalkIn(storeId.value, toRequest(), idempotencyKey)")
            .contains("StaffGuestContactLookup")
            .contains("客户信息")
            .contains("可选");

        assertThat(walkInQueue)
            .doesNotContain("返回员工首页")
            .doesNotContain("page-shell")
            .doesNotContain("store-context")
            .doesNotContain("primary-link");
    }

    @Test
    void reservationQueuePageKeepsSingleWalkInQueueEntryInEmptyState() throws Exception {
        String reservationQueue = Files.readString(Path.of("src", "pages", "ReservationArrivedToQueuePage.vue"));

        assertThat(reservationQueue)
            .contains("walkInQueueRoute")
            .contains("现场取号")
            .contains("RouterLink")
            .contains(":to=\"walkInQueueRoute\"")
            .doesNotContain("no-reservation-quick-ticket")
            .doesNotContain("无预约到店取号")
            .doesNotContain("快速取号");
    }

    @Test
    void localRuntimeRestartGuideIncludesClosedLoopQueuePermissions() throws Exception {
        String guide = Files.readString(Path.of("docs", "development", "LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md"));

        assertThat(guide)
            .contains("'walkin.queue.create'")
            .contains("'queue.cancel'")
            .contains("reservation_queue.permissions` contains `walkin.queue.create`")
            .contains("reservation_queue.permissions` contains `queue.cancel`");
    }

    @Test
    void localRuntimeRestartGuidePinsSharedMediaStorageRootAcrossRuntimeWorktrees() throws Exception {
        String guide = Files.readString(Path.of("docs", "development", "LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md"));

        assertThat(guide)
            .contains("mediaStorageRoot=D:\\RPB\\target\\call-screen-media")
            .contains("$mediaStorageRoot = if ($settings.ContainsKey('mediaStorageRoot'))")
            .contains("\"--rpb.call-screen-media.storage-root=$mediaStorageRoot\"")
            .contains("Tenant logo image returns `404`")
            .contains("Use `mediaStorageRoot` from the pointer");
    }
}
