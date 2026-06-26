package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueRejoinUiImplementationValidationTest {
    @Test
    void queueRejoinUiImplementsListPageContract() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueTicketListPage.vue");
        Path apiPath = Path.of("src", "api", "queueRejoinApi.ts");
        Path typesPath = Path.of("src", "types", "queueRejoin.ts");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);

        assertThat(apiClient)
            .contains("rejoinQueueTicket")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/rejoin")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify({})")
            .contains("UNKNOWN_ERROR")
            .contains("queue.rejoin.unknown_error");
        assertForbiddenRejoinRequestFieldsAbsent(apiClient);

        assertThat(types)
            .contains("interface RejoinQueueTicketResponse")
            .contains("interface QueueRejoinApiErrorResponse")
            .contains("interface QueueRejoinIdempotency")
            .contains("queueTicketId")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("queuePosition")
            .contains("reservationId")
            .contains("reservationCode")
            .contains("reservationStatus")
            .contains("rejoinedAt")
            .contains("alreadyRejoined")
            .contains("events")
            .contains("idempotency");

        assertThat(page)
            .contains("fetchMeApps")
            .contains("reservation_queue")
            .contains("queue.rejoin")
            .contains("canRejoinQueueTicket")
            .contains("canShowRejoinAction")
            .contains("item.queueTicketStatus === 'skipped'")
            .contains("rejoinQueueTicket")
            .contains("createRejoinIdempotencyKey")
            .contains("queue:rejoin")
            .contains("confirmRejoinTicket")
            .contains("executeConfirmedRejoin")
            .contains("rejoinInFlightTicketId")
            .contains("refreshAfterRejoinSuccess")
            .contains("shouldRefreshAfterRejoinError")
            .contains("QUEUE_TICKET_STATUS_NOT_SKIPPED")
            .contains("PERMISSION_DENIED")
            .contains("error.code")
            .contains("error.messageKey")
            .contains("重新入队")
            .contains("确认重新入队");
        assertForbiddenRejoinPageFieldsAbsent(page);
    }

    @Test
    void queueRejoinUiStaysInsideApprovedBoundary() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .contains("src/pages/QueueTicketListPage.vue")
            .doesNotContain(
                "src/pages/QueueRejoinPage.vue",
                "src/pages/QueueWorkbenchPage.vue",
                "src/pages/TableMapPage.vue",
                "src/pages/ReservationCalendarPage.vue",
                "src/pages/ReservationNoShowPage.vue",
                "src/pages/ReservationCancellationPage.vue"
            );

        List<String> apiFiles = Files.walk(Path.of("src", "api"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".ts"))
            .toList();

        assertThat(apiFiles)
            .contains("src/api/queueRejoinApi.ts")
            .doesNotContain(
                "src/api/queueWorkbenchApi.ts",
                "src/api/queueCallFromListApi.ts",
                "src/api/queueSeatFromListApi.ts",
                "src/api/tableMapApi.ts",
                "src/api/reservationCalendarApi.ts",
                "src/api/noShowApi.ts",
                "src/api/reservationNoShowApi.ts",
                "src/api/cancellationApi.ts",
                "src/api/reservationCancellationApi.ts",
                "src/api/turnoverApi.ts"
            );
    }

    private static void assertForbiddenRejoinRequestFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("note:")
            .doesNotContain("skippedAt:")
            .doesNotContain("rejoinedAt: request.rejoinedAt")
            .doesNotContain("reasonCode")
            .doesNotContain("targetStatus")
            .doesNotContain("queuePosition: request.queuePosition")
            .doesNotContain("ticketNumber")
            .doesNotContain("tenantId")
            .doesNotContain("storeId: request.storeId")
            .doesNotContain("actorId")
            .doesNotContain("actorType")
            .doesNotContain("queueTicketId: request.queueTicketId")
            .doesNotContain("reservationId: request.reservationId")
            .doesNotContain("tableId")
            .doesNotContain("seatingId")
            .doesNotContain("status: request.status")
            .doesNotContain("skip:")
            .doesNotContain("noShow")
            .doesNotContain("cancel")
            .doesNotContain("cleaning")
            .doesNotContain("turnover");
    }

    private static void assertForbiddenRejoinPageFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("name=\"tenantId\"")
            .doesNotContain("name=\"storeId\"")
            .doesNotContain("name=\"actorId\"")
            .doesNotContain("name=\"actorType\"")
            .doesNotContain("name=\"note\"")
            .doesNotContain("name=\"skippedAt\"")
            .doesNotContain("name=\"rejoinedAt\"")
            .doesNotContain("name=\"reasonCode\"")
            .doesNotContain("name=\"targetStatus\"")
            .doesNotContain("name=\"queuePosition\"")
            .doesNotContain("name=\"ticketNumber\"")
            .doesNotContain("name=\"reservationId\"")
            .doesNotContain("name=\"tableId\"")
            .doesNotContain("name=\"seatingId\"")
            .doesNotContain("name=\"status\"")
            .doesNotContain("叫号屏")
            .doesNotContain("桌位图")
            .doesNotContain("预约日历")
            .doesNotContain("爽约处理")
            .doesNotContain("取消预约");
    }
}
