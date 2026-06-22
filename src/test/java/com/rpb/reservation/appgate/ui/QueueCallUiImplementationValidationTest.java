package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueCallUiImplementationValidationTest {
    @Test
    void queueCallUiImplementsMinimumContract() throws Exception {
        Path pagePath = Path.of("src", "pages", "QueueCallPage.vue");
        Path apiPath = Path.of("src", "api", "queueCallApi.ts");
        Path typesPath = Path.of("src", "types", "queueCall.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path staffHomePath = Path.of("src", "pages", "StoreStaffHomePage.vue");
        Path reportPath = Path.of("docs", "frontend", "QUEUE_CALL_UI_IMPLEMENTATION_REPORT.md");

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
            .contains("QueueCallPage")
            .contains("path: '/stores/:storeId/queue-tickets/call'")
            .contains("name: 'queue-call'")
            .doesNotContain("path: '/stores/:storeId/queue'")
            .doesNotContain("path: '/stores/:storeId/queue/display'")
            .doesNotContain("path: '/stores/:storeId/queue/call-next'")
            .doesNotContain("path: '/stores/:storeId/seating/from-queue'");

        assertThat(staffHome)
            .contains("queue.call")
            .contains("canCallQueueTicket")
            .contains("name: 'queue-call'")
            .contains("排队叫号")
            .contains("输入排队记录 ID 并执行叫号")
            .contains("hasVisibleOperation");
        assertForbiddenQueueOperationsAbsent(staffHome);

        assertThat(apiClient)
            .contains("callQueueTicket")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/queue-tickets/${encodeURIComponent(queueTicketId)}/call")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify(toApiBody(request))")
            .contains("calledAt")
            .contains("reasonCode")
            .contains("note")
            .contains("UNKNOWN_ERROR")
            .contains("queue.call.unknown_error");
        assertForbiddenBodyFieldsAbsent(apiClient);

        assertThat(types)
            .contains("interface CallQueueTicketRequest")
            .contains("interface CallQueueTicketResponse")
            .contains("interface QueueCallApiErrorResponse")
            .contains("interface QueueCallIdempotency")
            .contains("queueTicketId")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("reservationId")
            .contains("reservationCode")
            .contains("reservationStatus")
            .contains("calledAt")
            .contains("holdUntilAt")
            .contains("alreadyCalled")
            .contains("events")
            .contains("idempotency");

        assertThat(page)
            .contains("<h1>排队叫号</h1>")
            .contains("queueTicketId")
            .contains("calledAt")
            .contains("reasonCode")
            .contains("note")
            .contains("createIdempotencyKey")
            .contains("queue:call")
            .contains("lastIdempotencyKey")
            .contains("callQueueTicket")
            .contains("toISOString")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("reservationStatus")
            .contains("holdUntilAt")
            .contains("alreadyCalled")
            .contains("eventsDisplay")
            .contains("idempotency")
            .contains("error.code")
            .contains("error.messageKey");
        assertForbiddenFormFieldsAbsent(page);
    }

    @Test
    void queueCallUiDoesNotIntroduceForbiddenOperationPagesOrApiClients() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .contains("src/pages/QueueCallPage.vue")
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
            .contains("src/api/queueCallApi.ts")
            .doesNotContain(
                "src/api/queueSkipApi.ts",
                "src/api/queueRejoinApi.ts",
                "src/api/queueDisplayApi.ts",
                "src/api/seatingFromQueueApi.ts",
                "src/api/reservationNoShowApi.ts",
                "src/api/reservationCancellationApi.ts"
            );
    }

    private static void assertForbiddenBodyFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("tenantId")
            .doesNotContain("actorId")
            .doesNotContain("actorType")
            .doesNotContain("reservationStatus: request.reservationStatus")
            .doesNotContain("queueTicketId: request.queueTicketId")
            .doesNotContain("queueTicketStatus: request.queueTicketStatus")
            .doesNotContain("tableId")
            .doesNotContain("tableGroupId")
            .doesNotContain("seatingId")
            .doesNotContain("cleaningId")
            .doesNotContain("turnoverId")
            .doesNotContain("skipReason")
            .doesNotContain("rejoinReason")
            .doesNotContain("status: request.status");
    }

    private static void assertForbiddenFormFieldsAbsent(String source) {
        assertThat(source)
            .doesNotContain("name=\"tenantId\"")
            .doesNotContain("name=\"storeId\"")
            .doesNotContain("name=\"actorId\"")
            .doesNotContain("name=\"actorType\"")
            .doesNotContain("name=\"reservationStatus\"")
            .doesNotContain("name=\"tableId\"")
            .doesNotContain("name=\"tableGroupId\"")
            .doesNotContain("name=\"seatingId\"")
            .doesNotContain("name=\"cleaningId\"")
            .doesNotContain("name=\"turnoverId\"")
            .doesNotContain("name=\"skipReason\"")
            .doesNotContain("name=\"rejoinReason\"")
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
