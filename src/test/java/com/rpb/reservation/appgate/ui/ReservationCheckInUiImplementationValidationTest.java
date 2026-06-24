package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationCheckInUiImplementationValidationTest {
    @Test
    void reservationCheckInUiImplementsMinimumContract() throws Exception {
        Path pagePath = Path.of("src", "pages", "ReservationCheckInPage.vue");
        Path apiPath = Path.of("src", "api", "reservationCheckInApi.ts");
        Path typesPath = Path.of("src", "types", "reservationCheckIn.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path staffHomePath = Path.of("src", "pages", "StoreStaffHomePage.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);
        String staffHome = Files.readString(staffHomePath);

        assertThat(router)
            .contains("ReservationCheckInPage")
            .contains("path: '/stores/:storeId/reservations/check-in'")
            .contains("name: 'reservation-check-in'")
            .doesNotContain("path: '/stores/:storeId/check-ins'")
            .doesNotContain("path: '/stores/:storeId/queue'")
            .doesNotContain("path: '/stores/:storeId/reservations/calendar'");

        assertThat(staffHome)
            .contains("reservation.check_in")
            .contains("canCheckInReservation")
            .contains("reservationConfirmedTodayRoute")
            .contains("name: 'reservation-today-view'")
            .contains("status: 'confirmed'")
            .contains("hasVisibleOperation")
            .doesNotContain("reservation-check-in");

        assertThat(apiClient)
            .contains("checkInReservation")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/check-in")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify(toApiBody(request))")
            .contains("arrivedAt: request.arrivedAt ?? null")
            .contains("reasonCode: request.reasonCode ?? null")
            .contains("note: request.note ?? null");

        assertThat(apiClient)
            .doesNotContain("tenantId")
            .doesNotContain("queueTicketId")
            .doesNotContain("seatingId")
            .doesNotContain("tableId")
            .doesNotContain("tableGroupId")
            .doesNotContain("noShowAt")
            .doesNotContain("cancelledAt");

        assertThat(types)
            .contains("interface CheckInReservationRequest")
            .contains("interface CheckInReservationResponse")
            .contains("interface ReservationCheckInApiErrorResponse")
            .contains("interface ReservationCheckInIdempotency")
            .contains("alreadyArrived")
            .contains("events")
            .contains("idempotency");

        assertThat(page)
            .contains("StaffBottomNav")
            .contains("staff-workbench-shell")
            .contains("reservation-check-in-workbench")
            .contains("reservation-check-in-card")
            .contains("check-in-result-card")
            .contains("确认预约客人已到店")
            .contains("active-tab=\"reservation\"")
            .contains("reservationId")
            .contains("arrivedAt")
            .contains("reasonCode")
            .contains("note")
            .contains("createIdempotencyKey")
            .contains("lastIdempotencyKey")
            .contains("alreadyArrived")
            .contains("idempotency")
            .contains("error.code")
            .contains("error.messageKey");

        assertThat(page)
            .doesNotContain("name=\"tenantId\"")
            .doesNotContain("name=\"storeId\"")
            .doesNotContain("name=\"queueTicketId\"")
            .doesNotContain("name=\"seatingId\"")
            .doesNotContain("name=\"tableId\"")
            .doesNotContain("name=\"tableGroupId\"")
            .doesNotContain("name=\"noShowAt\"")
            .doesNotContain("name=\"cancelledAt\"")
            .doesNotContain("name=\"status\"");
    }

    @Test
    void reservationCheckInUiDoesNotIntroduceForbiddenOperationPages() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .contains("src/pages/ReservationCheckInPage.vue")
            .doesNotContain(
                "src/pages/ReservationListPage.vue",
                "src/pages/ReservationCalendarPage.vue",
                "src/pages/QueuePage.vue",
                "src/pages/SeatingPage.vue",
                "src/pages/ReservationNoShowPage.vue",
                "src/pages/ReservationCancellationPage.vue"
            );
    }
}
