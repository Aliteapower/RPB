package com.rpb.reservation.appgate.domain;

import java.util.Set;

public final class AppGateRequiredPermission {
    public static final String RESERVATION_CREATE = "reservation.create";
    public static final String RESERVATION_CHECK_IN = "reservation.check_in";
    public static final String RESERVATION_SEAT = "reservation.seat";
    public static final String RESERVATION_TODAY_VIEW = "reservation.today_view";
    public static final String RESERVATION_QUEUE = "reservation.queue";
    public static final String RESERVATION_CANCEL = "reservation.cancel";
    public static final String QUEUE_VIEW = "queue.view";
    public static final String QUEUE_CALL = "queue.call";
    public static final String QUEUE_SEAT = "queue.seat";
    public static final String QUEUE_SKIP = "queue.skip";
    public static final String QUEUE_REJOIN = "queue.rejoin";
    public static final String TABLE_VIEW = "table.view";
    public static final String TABLE_SWITCH = "table.switch";
    public static final String CUSTOMER_LOOKUP = "customer.lookup";
    public static final String WALKIN_DIRECT_SEATING_CREATE = "walkin.direct_seating.create";
    public static final String CLEANING_START = "cleaning.start";
    public static final String CLEANING_COMPLETE = "cleaning.complete";

    public static final Set<String> RESERVATION_QUEUE_ENTRY_PERMISSIONS = Set.of(
        RESERVATION_CREATE,
        RESERVATION_CHECK_IN,
        RESERVATION_SEAT,
        RESERVATION_TODAY_VIEW,
        RESERVATION_QUEUE,
        RESERVATION_CANCEL,
        QUEUE_VIEW,
        QUEUE_CALL,
        QUEUE_SEAT,
        QUEUE_SKIP,
        QUEUE_REJOIN,
        TABLE_VIEW,
        TABLE_SWITCH,
        CUSTOMER_LOOKUP,
        WALKIN_DIRECT_SEATING_CREATE,
        CLEANING_START,
        CLEANING_COMPLETE
    );

    private AppGateRequiredPermission() {
    }
}
