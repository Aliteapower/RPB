package com.rpb.reservation.queue.application;

import java.util.List;

public record QueueTicketListResult(
    boolean success,
    QueueTicketListError error,
    List<QueueTicketListItem> items,
    QueueTicketListPage page
) {

    public QueueTicketListResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static QueueTicketListResult success(List<QueueTicketListItem> items, QueueTicketListPage page) {
        return new QueueTicketListResult(true, null, items, page);
    }

    public static QueueTicketListResult failure(QueueTicketListError error) {
        return new QueueTicketListResult(false, error, List.of(), null);
    }
}
