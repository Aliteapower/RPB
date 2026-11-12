package com.rpb.reservation.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

/** Selects the same store used by scoped controllers; query parameters cannot override a path store. */
final class RequestStoreScope {
    private static final Pattern STORE_PATH = Pattern.compile("^/api/v1/stores/([^/]+)(?:/.*)?$");
    private RequestStoreScope() {}

    static UUID resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        var match = STORE_PATH.matcher(path);
        if (match.matches()) return uuid(match.group(1));
        return "/api/me/apps".equals(path) ? uuid(request.getParameter("storeId")) : null;
    }

    private static UUID uuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
