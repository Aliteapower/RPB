package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueSkipUiImplementationValidationTest {
    @Test
    void queueSkipUiImplementsListPageContract() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueTicketListPage.vue");
        Path apiPath = Path.of("src", "api", "queueSkipApi.ts");
        Path typesPath = Path.of("src", "types", "queueSkip.ts");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);

        assertThat(apiClient)
            .contains("skipQueueTicket")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/skip")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify({})")
            .contains("UNKNOWN_ERROR")
            .contains("queue.skip.unknown_error");
        assertForbiddenSkipRequestFieldsAbsent(apiClient);

        assertThat(types)
            .contains("interface SkipQueueTicketResponse")
            .contains("interface QueueSkipApiErrorResponse")
            .contains("interface QueueSkipIdempotency")
            .contains("queueTicketId")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("reservationId")
            .contains("reservationCode")
            .contains("reservationStatus")
            .contains("skippedAt")
            .contains("alreadySkipped")
            .contains("events")
            .contains("idempotency");

        assertThat(page)
            .contains("fetchMeApps")
            .contains("reservation_queue")
            .contains("queue.skip")
            .contains("canSkipQueueTicket")
            .contains("canShowSkipAction")
            .contains("item.queueTicketStatus === 'called'")
            .contains("skipQueueTicket")
            .contains("createIdempotencyKey")
            .contains("queue:skip")
            .contains("confirmSkipTicket")
            .contains("executeConfirmedSkip")
            .contains("skipInFlightTicketId")
            .contains("refreshAfterSkipSuccess")
            .contains("shouldRefreshAfterSkipError")
            .contains("QUEUE_TICKET_STATUS_NOT_CALLED")
            .contains("PERMISSION_DENIED")
            .contains("error.code")
            .contains("error.messageKey")
            .contains("过号")
            .contains("确认过号");
        assertForbiddenSkipPageFieldsAbsent(page);
    }

    @Test
    void queueSkipUiStaysInsideApprovedBoundary() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .contains("src/pages/QueueTicketListPage.vue")
            .doesNotContain(
                "src/pages/QueueSkipPage.vue",
                "src/pages/QueueRejoinPage.vue",
                "src/pages/QueueDisplayPage.vue",
                "src/pages/QueueWorkbenchPage.vue",
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
            .contains("src/api/queueSkipApi.ts")
            .doesNotContain(
                "src/api/queueRejoinApi.ts",
                "src/api/queueDisplayApi.ts",
                "src/api/queueWorkbenchApi.ts",
                "src/api/tableMapApi.ts",
                "src/api/autoAssignmentApi.ts",
                "src/api/reservationNoShowApi.ts",
                "src/api/reservationCancellationApi.ts"
            );
    }

    private static void assertForbiddenSkipRequestFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("skippedAt: request.skippedAt")
            .doesNotContain("reasonCode: request.reasonCode")
            .doesNotContain("note: request.note")
            .doesNotContain("tenantId")
            .doesNotContain("storeId: request.storeId")
            .doesNotContain("actorId")
            .doesNotContain("actorType")
            .doesNotContain("queueTicketId: request.queueTicketId")
            .doesNotContain("reservationId: request.reservationId")
            .doesNotContain("tableId: request.tableId")
            .doesNotContain("tableGroupId: request.tableGroupId")
            .doesNotContain("seatingId: request.seatingId")
            .doesNotContain("cleaningId: request.cleaningId")
            .doesNotContain("turnoverId: request.turnoverId")
            .doesNotContain("rejoin")
            .doesNotContain("noShow")
            .doesNotContain("cancel")
            .doesNotContain("status: request.status");
    }

    private static void assertForbiddenSkipPageFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("name=\"tenantId\"")
            .doesNotContain("name=\"storeId\"")
            .doesNotContain("name=\"actorId\"")
            .doesNotContain("name=\"actorType\"")
            .doesNotContain("name=\"skippedAt\"")
            .doesNotContain("name=\"reasonCode\"")
            .doesNotContain("name=\"reservationId\"")
            .doesNotContain("name=\"tableId\"")
            .doesNotContain("name=\"tableGroupId\"")
            .doesNotContain("name=\"seatingId\"")
            .doesNotContain("name=\"cleaningId\"")
            .doesNotContain("name=\"turnoverId\"")
            .doesNotContain("name=\"status\"")
            .doesNotContain("重新入队")
            .doesNotContain("叫号屏")
            .doesNotContain("桌位图")
            .doesNotContain("自动分桌")
            .doesNotContain("爽约处理")
            .doesNotContain("取消预约");
    }
}
