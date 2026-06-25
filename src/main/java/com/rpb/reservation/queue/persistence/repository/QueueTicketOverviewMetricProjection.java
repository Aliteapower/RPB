package com.rpb.reservation.queue.persistence.repository;

public interface QueueTicketOverviewMetricProjection {
    String getStatus();

    String getPartySizeGroup();

    long getTicketCount();

    int getPartySizeTotal();
}
