package com.rpb.reservation.staffhome.application;

public record StaffHomeOverviewResult(
    boolean success,
    StaffHomeOverviewError error,
    StaffHomeOverview overview
) {

    public static StaffHomeOverviewResult success(StaffHomeOverview overview) {
        return new StaffHomeOverviewResult(true, null, overview);
    }

    public static StaffHomeOverviewResult failure(StaffHomeOverviewError error) {
        return new StaffHomeOverviewResult(false, error, null);
    }
}
