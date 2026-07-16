package com.rpb.reservation.table.persistence.repository;

import java.util.UUID;

public interface DiningTableResourceProjection {
    UUID getResourceId();

    String getCode();

    String getDisplayName();

    String getAreaName();

    Integer getCapacityMin();

    Integer getCapacityMax();

    String getStatus();
}
