package com.rpb.reservation.platform.application;

import com.rpb.reservation.platform.persistence.PublicHostBindingRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublicHostBindingService {
    private static final String HOST_PREFIX_PATTERN = "(?!platform$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";

    private final PublicHostBindingRepository repository;
    private final String baseHost;

    public PublicHostBindingService(
        PublicHostBindingRepository repository,
        @Value("${rpb.host-prefix.base-host:}") String baseHost
    ) {
        this.repository = repository;
        this.baseHost = normalizeHost(baseHost);
    }

    public void syncBinding(
        UUID hostAliasId,
        UUID tenantId,
        String hostPrefix,
        String hostType,
        String status
    ) {
        if (hostAliasId == null || tenantId == null || baseHost == null) {
            return;
        }
        if (!"active".equals(status)) {
            repository.archiveBinding(hostAliasId);
            return;
        }
        String normalizedPrefix = normalizePrefix(hostPrefix);
        String normalizedHostType = normalizeHostType(hostType);
        if (normalizedPrefix == null || normalizedHostType == null) {
            return;
        }
        repository.upsertPendingBinding(
            hostAliasId,
            tenantId,
            normalizedPrefix,
            normalizedHostType,
            normalizedPrefix + "." + baseHost
        );
    }

    public void archiveBinding(UUID hostAliasId) {
        if (hostAliasId != null) {
            repository.archiveBinding(hostAliasId);
        }
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches(HOST_PREFIX_PATTERN) ? normalized : null;
    }

    private static String normalizeHostType(String value) {
        if ("tenant".equals(value) || "store".equals(value)) {
            return value;
        }
        return null;
    }

    private static String normalizeHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return null;
        }
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        int schemeIndex = host.indexOf("://");
        if (schemeIndex >= 0) {
            host = host.substring(schemeIndex + 3);
        }
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > -1 && host.indexOf(':') == colonIndex) {
            host = host.substring(0, colonIndex);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.isBlank() ? null : host;
    }
}
