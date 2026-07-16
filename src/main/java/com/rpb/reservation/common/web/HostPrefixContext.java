package com.rpb.reservation.common.web;

public record HostPrefixContext(HostPrefixKind kind, String tenantCode) {

    public static HostPrefixContext none() {
        return new HostPrefixContext(HostPrefixKind.NONE, null);
    }

    public static HostPrefixContext platform() {
        return new HostPrefixContext(HostPrefixKind.PLATFORM, null);
    }

    public static HostPrefixContext tenant(String tenantCode) {
        return new HostPrefixContext(HostPrefixKind.TENANT, tenantCode);
    }

    public boolean isPlatform() {
        return kind == HostPrefixKind.PLATFORM;
    }

    public boolean isTenant() {
        return kind == HostPrefixKind.TENANT;
    }

    public enum HostPrefixKind {
        NONE,
        PLATFORM,
        TENANT
    }
}
