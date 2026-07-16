package com.rpb.reservation.table.api;

import java.util.List;

public record TableResourceListResponse(
    boolean success,
    List<TableResourceItemResponse> resources
) {

    public TableResourceListResponse {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
