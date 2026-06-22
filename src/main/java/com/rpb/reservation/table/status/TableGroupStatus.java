package com.rpb.reservation.table.status;

/**
 * Combined fixed and temporary TableGroup status code set from migration.
 */
public enum TableGroupStatus {
    CREATED("created"),
    ACTIVE("active"),
    INACTIVE("inactive"),
    DELETED("deleted"),
    LOCKED("locked"),
    OCCUPIED("occupied"),
    RELEASED("released"),
    ENDED("ended");

    private final String code;

    TableGroupStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
