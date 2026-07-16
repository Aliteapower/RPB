package com.rpb.reservation.tenantadmin.application;

import java.util.Locale;

final class TenantAdminSearchNormalizer {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private TenantAdminSearchNormalizer() {
    }

    static TenantAdminSearchCriteria normalize(TenantAdminSearchCommand command) {
        TenantAdminSearchCommand safeCommand = command == null
            ? new TenantAdminSearchCommand(null, null, null)
            : command;
        String keyword = optionalText(safeCommand.keyword());
        return new TenantAdminSearchCriteria(
            keyword == null ? null : keyword.toLowerCase(Locale.ROOT),
            resolveLimit(safeCommand.limit()),
            resolveOffset(safeCommand.offset())
        );
    }

    private static int resolveLimit(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(normalized);
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw new NumberFormatException("invalid_limit");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static int resolveOffset(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return DEFAULT_OFFSET;
        }
        try {
            int offset = Integer.parseInt(normalized);
            if (offset < 0) {
                throw new NumberFormatException("invalid_offset");
            }
            return offset;
        } catch (NumberFormatException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
