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

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);
        String staffHome = Files.readString(staffHomePath);
        String todayView = Files.readString(todayViewPath);

        assertThat(router)
            .contains("ReservationArrivedToQueuePage")
            .contains("path: '/stores/:storeId/reservations/queue'")
            .contains("name: 'reservation-arrived-to-queue'")
            .doesNotContain("path: '/stores/:storeId/queue'")
            .doesNotContain("path: '/stores/:storeId/queue/call'")
            .doesNotContain("path: '/stores/:storeId/queue/display'")
            .doesNotContain("path: '/stores/:storeId/seating/from-queue'");

        assertThat(staffHome)
            .contains("reservation.queue")
            .contains("canQueueArrivedReservation")
            .contains("name: 'reservation-arrived-to-queue'")
            .contains("预约排队")
            .contains("hasVisibleOperation");
        assertForbiddenQueueOperationsAbsent(staffHome);

        assertThat(todayView)
            .contains("queueReservationRoute")
            .contains("name: 'reservation-arrived-to-queue'")
            .contains("进入排队")
            .contains("item.status === 'arrived'")
            .contains("reservationId: item.reservationId")
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
            .contains("<h1>预约排队</h1>")
            .contains("reservationId")
            .contains("partySizeGroup")
            .contains("reasonCode")
            .contains("note")
            .contains("partySizeOptions")
            .contains("自动推导")
            .contains("1-2")
            .contains("3-4")
            .contains("5-6")
            .contains("7+")
            .contains("createIdempotencyKey")
            .contains("reservation:queue")
            .contains("lastIdempotencyKey")
            .contains("queueArrivedReservation")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("reservationStatus")
            .contains("alreadyQueued")
            .contains("idempotency")
            .contains("error.code")
            .contains("error.messageKey");
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
                "src/pages/QueueDisplayPage.vue",
                "src/pages/SeatingFromQueuePage.vue",
                "src/pages/TableMapPage.vue",
                "src/pages/ReservationNoShowPage.vue",
                "src/pages/ReservationCancellationPage.vue"
            );
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
