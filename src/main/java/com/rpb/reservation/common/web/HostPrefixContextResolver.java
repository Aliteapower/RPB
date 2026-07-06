package com.rpb.reservation.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HostPrefixContextResolver {
    private static final String NUMERIC_TENANT_PREFIX_PATTERN = "\\d{4,20}";
    private static final String TENANT_PREFIX_PATTERN = "(?!platform$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";

    private final String baseHost;

    public HostPrefixContextResolver(@Value("${rpb.host-prefix.base-host:}") String baseHost) {
        this.baseHost = normalizeHost(baseHost);
    }

    public HostPrefixContext resolve(HttpServletRequest request) {
        if (request == null) {
            return HostPrefixContext.none();
        }
        String host = firstText(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"), request.getServerName());
        return resolveHost(host);
    }

    public HostPrefixContext resolveHost(String rawHost) {
        String host = normalizeHost(rawHost);
        if (host == null || isLocalHost(host) || isIpAddress(host)) {
            return HostPrefixContext.none();
        }
        if (baseHost != null) {
            return resolveConfiguredBaseHost(host);
        }
        String[] labels = host.split("\\.");
        if (labels.length < 3) {
            return HostPrefixContext.none();
        }
        String prefix = labels[0];
        if ("platform".equals(prefix)) {
            return HostPrefixContext.platform();
        }
        if (isTenantPrefix(prefix, labels)) {
            return HostPrefixContext.tenant(prefix);
        }
        return HostPrefixContext.none();
    }

    private HostPrefixContext resolveConfiguredBaseHost(String host) {
        if (host.equals(baseHost)) {
            return HostPrefixContext.none();
        }
        String suffix = "." + baseHost;
        if (!host.endsWith(suffix)) {
            return HostPrefixContext.none();
        }
        String prefix = host.substring(0, host.length() - suffix.length());
        if (prefix.contains(".")) {
            return HostPrefixContext.none();
        }
        if ("platform".equals(prefix)) {
            return HostPrefixContext.platform();
        }
        return isTenantPrefix(prefix) ? HostPrefixContext.tenant(prefix) : HostPrefixContext.none();
    }

    private static String normalizeHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return null;
        }
        String host = rawHost.split(",", 2)[0].trim().toLowerCase(Locale.ROOT);
        int schemeIndex = host.indexOf("://");
        if (schemeIndex >= 0) {
            host = host.substring(schemeIndex + 3);
        }
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        if (host.startsWith("[") && host.contains("]")) {
            return host.substring(1, host.indexOf(']'));
        }
        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > -1 && host.indexOf(':') == colonIndex) {
            host = host.substring(0, colonIndex);
        }
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equals(host) || host.endsWith(".localhost");
    }

    private static boolean isIpAddress(String host) {
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.contains(":");
    }

    private static boolean isTenantPrefix(String prefix, String[] labels) {
        if (!isTenantPrefix(prefix)) {
            return false;
        }
        return prefix.matches(NUMERIC_TENANT_PREFIX_PATTERN) || labels.length > 3;
    }

    private static boolean isTenantPrefix(String prefix) {
        return prefix != null && prefix.matches(TENANT_PREFIX_PATTERN);
    }
}
