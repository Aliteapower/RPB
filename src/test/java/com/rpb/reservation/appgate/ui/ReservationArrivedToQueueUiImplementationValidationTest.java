package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationArrivedToQueueUiImplementationValidationTest {
    @Test
    void reservationArrivedToQueueUiImplementsMinimumContract() throws Exception {
        Path pagePath = Path.of("src", "pages", "ReservationArrivedToQueuePage.vue");
        Path apiPath = Path.of("src", "api", "reservationArrivedToQueueApi.ts");
        Path typesPath = Path.of("src", "types", "reservationArrivedToQueue.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path staffHomePath = Path.of("src", "pages", "StoreStaffHomePage.vue");
        Path todayViewPath = Path.of("src", "pages", "ReservationTodayViewPage.vue");
        Path todayQuickActionsPath = Path.of("src", "components", "reservation-workbench", "ReservationQuickActionPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(todayQuickActionsPath).exists();
        assertThat(todayListItemPath).exists();

        String page = FrontendSourceSupport.readString(pagePath);
        String apiClient = FrontendSourceSupport.readString(apiPath);
        String types = FrontendSourceSupport.readString(typesPath);
        String router = FrontendSourceSupport.readString(routerPath);
        String staffHome = FrontendSourceSupport.readString(staffHomePath);
        String todayView = FrontendSourceSupport.readString(todayViewPath);
        String todayWorkbenchSource = todayView
            + FrontendSourceSupport.readString(todayQuickActionsPath)
            + FrontendSourceSupport.readString(todayListItemPath);

        assertThat(router)
            .contains("ReservationArrivedToQueuePage")
            .contains("path: '/stores/:storeId/reservations/queue'")
            .contains("name: 'reservation-arrived-to-queue'")
            .doesNotContain("path: '/stores/:storeId/queue'")
            .doesNotContain("path: '/stores/:storeId/queue/call'")
            .doesNotContain("path: '/stores/:storeId/queue/display'")
            .doesNotContain("path: '/stores/:storeId/seating/from-queue'");

        assertThat(staffHome)
            .contains("getStaffHomeOverview")
            .contains("StaffHomeTopBar")
            .contains("StaffBottomNav")
            .contains("staffHome.aria.todayOverview")
            .contains("active-tab=\"home\"");
        assertForbiddenQueueOperationsAbsent(staffHome);

        assertThat(todayWorkbenchSource)
            .contains("ReservationQuickActionPanel")
            .contains("routeName: 'reservation-arrived-to-queue'")
            .contains("labelKey: 'reservationWorkbench.quickActions.reservationToQueue'")
            .contains("routeName: 'walk-in-queue'")
            .contains("labelKey: 'reservationWorkbench.quickActions.walkInQueue'")
            .contains("labelKey: 'reservationWorkbench.quickActions.createReservation'")
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
            .doesNotContain("labelKey: 'reservationWorkbench.quickActions.checkIn'")
            .doesNotContain("labelKey: 'reservationWorkbench.quickActions.seat'")
            .doesNotContain("show-confirmed-reservations")
            .doesNotContain("show-arrived-reservations")
            .doesNotContain("已到店预约进入排队")
            .doesNotContain("取消预约需后端契约")
            .doesNotContain("queueArrivedReservation")
            .doesNotContain("reservationArrivedToQueueApi");

        assertThat(apiClient)
            .contains("queueArrivedReservation")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/queue")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify(toApiBody(request))")
            .contains("partySizeGroup")
            .contains("reasonCode")
            .contains("note")
            .contains("UNKNOWN_ERROR");
        assertForbiddenBodyFieldsAbsent(apiClient);

        assertThat(types)
            .contains("interface QueueArrivedReservationRequest")
            .contains("interface QueueArrivedReservationResponse")
            .contains("interface ReservationArrivedToQueueApiErrorResponse")
            .contains("interface ReservationArrivedToQueueIdempotency")
            .contains("reservationStatus")
            .contains("queueTicketId")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("queueGroupId")
            .contains("partySize")
            .contains("partySizeGroup")
            .contains("alreadyQueued")
            .contains("events")
            .contains("idempotency");

        assertThat(page)
            .contains("StaffHomeTopBar")
            .contains("StaffBottomNav")
            .contains("staff-workbench-shell")
            .contains("<h1>预约排队</h1>")
            .contains("getReservationTodayView")
            .contains("checkInReservation")
            .contains("ReservationTodayViewItem")
            .contains("ReservationCheckInApiError")
            .contains("status: 'operational'")
            .contains("queueReservation(item: ReservationTodayViewItem)")
            .contains("canQueueReservation(item)")
            .contains("shouldCheckInBeforeQueue(item)")
            .contains("createReservationCheckInIdempotencyKey")
            .contains("queue-ticket-list")
            .contains("reservation-today-view")
            .contains("router.push(queueTicketListRoute.value)")
            .contains(":business-date=\"displayedBusinessDate\"")
            .contains("进入排队线")
            .contains("到店取号")
            .contains("可取号预约")
            .contains("现场取号")
            .contains("reservationId")
            .contains("createIdempotencyKey")
            .contains("reservation:queue")
            .contains("queueArrivedReservation")
            .contains("error.code")
            .contains("error.messageKey");
        assertThat(page)
            .doesNotContain("预约 ID")
            .doesNotContain("复制预约")
            .doesNotContain("复制 ID")
            .doesNotContain("name=\"reservationId\"")
            .doesNotContain("partySizeOptions")
            .doesNotContain("lastIdempotencyKey")
            .doesNotContain("idempotency-key")
            .doesNotContain("idempotency.status")
            .doesNotContain("alreadyQueued")
            .doesNotContain("business-date-badge")
            .doesNotContain("StaffHomeWorkflowStrip")
            .doesNotContain("no-reservation-quick-ticket")
            .doesNotContain("快速取号")
            .doesNotContain("无预约到店取号")
            .doesNotContain("page-shell")
            .doesNotContain("返回员工首页");
        assertForbiddenFormFieldsAbsent(page);
    }

    @Test
    void reservationArrivedToQueueUiDoesNotIntroduceForbiddenOperationPages() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .contains("src/pages/ReservationArrivedToQueuePage.vue")
            .doesNotContain(
                "src/pages/QueuePage.vue",
                "src/pages/QueueSkipPage.vue",
                "src/pages/QueueRejoinPage.vue",
                "src/pages/SeatingFromQueuePage.vue",
                "src/pages/TableMapPage.vue",
                "src/pages/ReservationNoShowPage.vue",
                "src/pages/ReservationCancellationPage.vue"
            );
    }

    @Test
    void reservationTodayListSeparatesDirectReservationSeatingFromCalledQueueSeating() throws Exception {
        Path todayViewPath = Path.of("src", "pages", "ReservationTodayViewPage.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");

        assertThat(todayViewPath).exists();
        assertThat(todayListItemPath).exists();

        String todayView = FrontendSourceSupport.readString(todayViewPath);
        String todayListItem = FrontendSourceSupport.readString(todayListItemPath);

        assertThat(todayListItem)
            .contains("const queueTicketStatus")
            .contains("const showDirectSeat")
            .contains("const showQueueSeat")
            .contains("queueTicketStatus.value === 'called'")
            .contains("const seatActionText")
            .contains("reservationWorkbench.item.seatFromQueue")
            .contains("resourceLabel")
            .doesNotContain("const showSeat = computed(() => props.item.status === 'arrived')");

        assertThat(todayView)
            .contains("useRouter")
            .contains("router.push({")
            .contains("name: 'seating-from-called-queue'")
            .contains("queueTicketId")
            .contains("showSeatDialog.value = true");
    }

    private static void assertForbiddenBodyFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("tenantId")
            .doesNotContain("actorId")
            .doesNotContain("actorType")
            .doesNotContain("tableId")
            .doesNotContain("tableGroupId")
            .doesNotContain("seatingId")
            .doesNotContain("walkInId")
            .doesNotContain("cleaningId")
            .doesNotContain("turnoverId")
            .doesNotContain("noShowAt")
            .doesNotContain("cancelledAt")
            .doesNotContain("queueTicketNumber: request.queueTicketNumber")
            .doesNotContain("status: request.status");
    }

    private static void assertForbiddenFormFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("name=\"tenantId\"")
            .doesNotContain("name=\"storeId\"")
            .doesNotContain("name=\"actorId\"")
            .doesNotContain("name=\"actorType\"")
            .doesNotContain("name=\"tableId\"")
            .doesNotContain("name=\"tableGroupId\"")
            .doesNotContain("name=\"seatingId\"")
            .doesNotContain("name=\"walkInId\"")
            .doesNotContain("name=\"cleaningId\"")
            .doesNotContain("name=\"turnoverId\"")
            .doesNotContain("name=\"noShowAt\"")
            .doesNotContain("name=\"cancelledAt\"")
            .doesNotContain("name=\"queueTicketNumber\"")
            .doesNotContain("name=\"status\"");
    }

    private static void assertForbiddenQueueOperationsAbsent(String source) {
        assertThat(source)
            .doesNotContain("过号处理")
            .doesNotContain("重新入队")
            .doesNotContain("队列列表")
            .doesNotContain("叫号屏")
            .doesNotContain("从队列入座");
    }
}
