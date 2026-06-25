package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatingFromCalledQueueUiImplementationValidationTest {
    @Test
    void seatingFromCalledQueueUiImplementsMinimumContract() throws Exception {
        Path pagePath = Path.of("src", "pages", "SeatingFromCalledQueuePage.vue");
        Path apiPath = Path.of("src", "api", "seatingFromCalledQueueApi.ts");
        Path typesPath = Path.of("src", "types", "seatingFromCalledQueue.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path staffHomePath = Path.of("src", "pages", "StoreStaffHomePage.vue");
        Path reportPath = Path.of("docs", "frontend", "SEATING_FROM_CALLED_QUEUE_UI_IMPLEMENTATION_REPORT.md");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(reportPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);
        String staffHome = Files.readString(staffHomePath);

        assertThat(router)
            .contains("SeatingFromCalledQueuePage")
            .contains("path: '/stores/:storeId/queue-tickets/seating/direct'")
            .contains("name: 'seating-from-called-queue'")
            .doesNotContain("path: '/stores/:storeId/queue'")
            .doesNotContain("path: '/stores/:storeId/queue/display'")
            .doesNotContain("path: '/stores/:storeId/queue/workbench'")
            .doesNotContain("path: '/stores/:storeId/seating/from-queue'")
            .doesNotContain("path: '/stores/:storeId/table-map'");

        assertThat(staffHome)
            .contains("queue.seat")
            .contains("canSeatCalledQueueTicket")
            .contains("to: queueTicketListRoute.value")
            .contains("排队入座")
            .contains("从已叫号票直接安排桌台")
            .contains("hasVisibleOperation");
        assertForbiddenQueueSeatOperationsAbsent(staffHome);

        assertThat(apiClient)
            .contains("seatCalledQueueTicket")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/seating/direct")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify(toApiBody(request))")
            .contains("tableId")
            .contains("tableGroupId")
            .contains("overrideReasonCode")
            .contains("overrideNote")
            .contains("note")
            .contains("UNKNOWN_ERROR")
            .contains("queue.seat.unknown_error");
        assertForbiddenBodyFieldsAbsent(apiClient);

        assertThat(types)
            .contains("interface SeatCalledQueueTicketRequest")
            .contains("interface SeatCalledQueueTicketResponse")
            .contains("interface SeatingFromCalledQueueApiErrorResponse")
            .contains("interface SeatingFromCalledQueueIdempotency")
            .contains("queueTicketId")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("reservationId")
            .contains("reservationCode")
            .contains("reservationStatus")
            .contains("seatingId")
            .contains("seatingStatus")
            .contains("resourceType")
            .contains("resourceId")
            .contains("alreadySeated")
            .contains("events")
            .contains("idempotency");

        assertThat(page)
            .contains("StaffHomeTopBar")
            .contains("StaffHomeWorkflowStrip")
            .contains("StaffBottomNav")
            .contains("queue-seating-workbench")
            .contains("queue-seating-workbench-body")
            .contains("<h1>排队入座</h1>")
            .contains("route.query.queueTicketId")
            .contains("queueTicketId")
            .contains("tableId")
            .contains("tableGroupId")
            .contains("hasExactlyOneResource")
            .contains("RESOURCE_SELECTION_REQUIRED")
            .contains("RESOURCE_SELECTION_CONFLICT")
            .contains("createIdempotencyKey")
            .contains("queue:seat")
            .contains("seatCalledQueueTicket")
            .contains("router.push(tableResourceListRoute.value)")
            .contains("error.code")
            .contains("error.messageKey")
            .doesNotContain("page-shell")
            .doesNotContain("返回员工首页")
            .doesNotContain("store-context")
            .doesNotContain("name=\"queueTicketId\"")
            .doesNotContain("排队票 ID")
            .doesNotContain("手动填写资源 ID")
            .doesNotContain("调整信息")
            .doesNotContain("备注")
            .doesNotContain("overrideReasonCode")
            .doesNotContain("overrideNote")
            .doesNotContain("lastIdempotencyKey")
            .doesNotContain("eventsDisplay")
            .doesNotContain("幂等键")
            .doesNotContain("资源 ID");
        assertForbiddenFormFieldsAbsent(page);
    }

    @Test
    void seatingFromCalledQueueUiDoesNotShowRequiredResourceAsInitialError() throws Exception {
        String page = Files.readString(Path.of("src", "pages", "SeatingFromCalledQueuePage.vue"));

        int errorStart = page.indexOf("const resourceSelectionError = computed");
        int hintStart = page.indexOf("const resourceSelectionHint = computed");
        assertThat(errorStart).isGreaterThanOrEqualTo(0);
        assertThat(hintStart).isGreaterThan(errorStart);

        String inlineSelectionError = page.substring(errorStart, hintStart);

        assertThat(inlineSelectionError)
            .doesNotContain("selectedResourceCount.value === 0")
            .contains("selectedResourceCount.value > 1")
            .contains("form.temporaryTableIds.length === 1");
        assertThat(page)
            .contains("resourceSelectionHint")
            .contains("请选择桌台、桌组，或在临时组合中选择至少 2 张桌台")
            .contains("v-if=\"resourceSelectionHint\"")
            .contains("v-if=\"resourceSelectionError\"");
    }

    @Test
    void queueSeatingApiAcceptsWalkInSuccessResponseWithoutReservationFields() throws Exception {
        String apiClient = Files.readString(Path.of("src", "api", "seatingFromCalledQueueApi.ts"));
        String types = Files.readString(Path.of("src", "types", "seatingFromCalledQueue.ts"));

        assertThat(types)
            .contains("reservationId?: string | null")
            .contains("reservationCode?: string | null")
            .contains("reservationStatus?: string | null");

        assertThat(apiClient)
            .contains("isOptionalString(candidate.reservationId)")
            .contains("isOptionalString(candidate.reservationCode)")
            .contains("isOptionalString(candidate.reservationStatus)")
            .doesNotContain("typeof candidate.reservationId === 'string' &&")
            .doesNotContain("typeof candidate.reservationCode === 'string' &&")
            .doesNotContain("typeof candidate.reservationStatus === 'string' &&");
    }

    @Test
    void seatingFromCalledQueueUiDoesNotIntroduceForbiddenOperationPagesOrApiClients() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .contains("src/pages/SeatingFromCalledQueuePage.vue")
            .doesNotContain(
                "src/pages/QueuePage.vue",
                "src/pages/QueueSkipPage.vue",
                "src/pages/QueueRejoinPage.vue",
                "src/pages/QueueDisplayPage.vue",
                "src/pages/QueueWorkbenchPage.vue",
                "src/pages/SeatingFromQueuePage.vue",
                "src/pages/TableMapPage.vue",
                "src/pages/ReservationNoShowPage.vue",
                "src/pages/ReservationCancellationPage.vue"
            );

        List<String> apiFiles = Files.walk(Path.of("src", "api"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".ts"))
            .toList();

        assertThat(apiFiles)
            .contains("src/api/seatingFromCalledQueueApi.ts")
            .doesNotContain(
                "src/api/queueDisplayApi.ts",
                "src/api/queueWorkbenchApi.ts",
                "src/api/queueCallFromListApi.ts",
                "src/api/queueSeatFromListApi.ts",
                "src/api/seatingApi.ts",
                "src/api/seatingFromQueueApi.ts",
                "src/api/tableMapApi.ts",
                "src/api/autoAssignmentApi.ts",
                "src/api/noShowApi.ts",
                "src/api/reservationNoShowApi.ts",
                "src/api/cancellationApi.ts",
                "src/api/reservationCancellationApi.ts",
                "src/api/turnoverApi.ts"
            );
    }

    private static void assertForbiddenBodyFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("tenantId")
            .doesNotContain("actorId")
            .doesNotContain("actorType")
            .doesNotContain("storeId: request.storeId")
            .doesNotContain("queueTicketId: request.queueTicketId")
            .doesNotContain("reservationId: request.reservationId")
            .doesNotContain("walkInId: request.walkInId")
            .doesNotContain("checkInAt: request.checkInAt")
            .doesNotContain("noShowAt: request.noShowAt")
            .doesNotContain("cancelledAt: request.cancelledAt")
            .doesNotContain("cleaningId: request.cleaningId")
            .doesNotContain("turnoverId: request.turnoverId")
            .doesNotContain("queueSkipReason: request.queueSkipReason")
            .doesNotContain("queueRejoinReason: request.queueRejoinReason")
            .doesNotContain("status: request.status");
    }

    private static void assertForbiddenFormFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("name=\"tenantId\"")
            .doesNotContain("name=\"storeId\"")
            .doesNotContain("name=\"actorId\"")
            .doesNotContain("name=\"actorType\"")
            .doesNotContain("name=\"reservationId\"")
            .doesNotContain("name=\"walkInId\"")
            .doesNotContain("name=\"checkInAt\"")
            .doesNotContain("name=\"noShowAt\"")
            .doesNotContain("name=\"cancelledAt\"")
            .doesNotContain("name=\"cleaningId\"")
            .doesNotContain("name=\"turnoverId\"")
            .doesNotContain("name=\"queueSkipReason\"")
            .doesNotContain("name=\"queueRejoinReason\"")
            .doesNotContain("name=\"status\"");
    }

    private static void assertForbiddenQueueSeatOperationsAbsent(String source) {
        assertThat(source)
            .doesNotContain("队列列表")
            .doesNotContain("叫号屏")
            .doesNotContain("过号处理")
            .doesNotContain("重新入队")
            .doesNotContain("自动分桌")
            .doesNotContain("桌位图")
            .doesNotContain("爽约处理")
            .doesNotContain("取消预约");
    }
}
