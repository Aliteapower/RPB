package com.rpb.reservation.table.application;

import java.util.List;

public record TableResourceListResult(
    boolean success,
    List<TableResourceItem> resources,
    TableResourceListError error
) {

    public TableResourceListResult {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    public static TableResourceListResult success(List<TableResourceItem> resources) {
        return new TableResourceListResult(true, resources, null);
    }

    public static TableResourceListResult failure(TableResourceListError error) {
        return new TableResourceListResult(false, List.of(), error);
    }
}
