package com.rpb.reservation.appgate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppGateRequiredPermissionTest {
    @Test
    void reservationCheckInPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.RESERVATION_CHECK_IN).isEqualTo("reservation.check_in");
    }

    @Test
    void reservationSeatPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.RESERVATION_SEAT).isEqualTo("reservation.seat");
    }

    @Test
    void reservationTodayViewPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.RESERVATION_TODAY_VIEW).isEqualTo("reservation.today_view");
    }

    @Test
    void reservationQueuePermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.RESERVATION_QUEUE).isEqualTo("reservation.queue");
    }

    @Test
    void reservationCancelPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.RESERVATION_CANCEL).isEqualTo("reservation.cancel");
    }

    @Test
    void queueCallPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.QUEUE_CALL).isEqualTo("queue.call");
    }

    @Test
    void queueSeatPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.QUEUE_SEAT).isEqualTo("queue.seat");
    }

    @Test
    void queueSkipPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.QUEUE_SKIP).isEqualTo("queue.skip");
    }

    @Test
    void queueRejoinPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.QUEUE_REJOIN).isEqualTo("queue.rejoin");
    }

    @Test
    void queueViewPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.QUEUE_VIEW).isEqualTo("queue.view");
    }

    @Test
    void tableViewPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.TABLE_VIEW).isEqualTo("table.view");
    }

    @Test
    void customerLookupPermissionKeyIsStable() {
        assertThat(AppGateRequiredPermission.CUSTOMER_LOOKUP).isEqualTo("customer.lookup");
    }

    @Test
    void reservationQueueEntryPermissionsContainConfirmedRuntimeCapabilities() {
        assertThat(AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS)
            .containsExactlyInAnyOrder(
                AppGateRequiredPermission.WALKIN_DIRECT_SEATING_CREATE,
                AppGateRequiredPermission.CLEANING_START,
                AppGateRequiredPermission.CLEANING_COMPLETE,
                AppGateRequiredPermission.RESERVATION_CREATE,
                AppGateRequiredPermission.RESERVATION_CHECK_IN,
                AppGateRequiredPermission.RESERVATION_SEAT,
                AppGateRequiredPermission.RESERVATION_TODAY_VIEW,
                AppGateRequiredPermission.RESERVATION_QUEUE,
                AppGateRequiredPermission.RESERVATION_CANCEL,
                AppGateRequiredPermission.QUEUE_CALL,
                AppGateRequiredPermission.QUEUE_SEAT,
                AppGateRequiredPermission.QUEUE_SKIP,
                AppGateRequiredPermission.QUEUE_REJOIN,
                AppGateRequiredPermission.QUEUE_VIEW,
                AppGateRequiredPermission.TABLE_VIEW,
                AppGateRequiredPermission.CUSTOMER_LOOKUP
            );
    }
}
