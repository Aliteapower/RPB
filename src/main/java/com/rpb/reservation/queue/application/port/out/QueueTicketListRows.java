package com.rpb.reservation.queue.application.port.out;

import java.util.List;

public record QueueTicketListRows(
    List<QueueTicketListRow> rows,
    int total
) {

    public QueueTicketListRows {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
