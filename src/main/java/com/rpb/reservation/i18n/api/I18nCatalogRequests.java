package com.rpb.reservation.i18n.api;

import com.rpb.reservation.i18n.application.I18nCatalogMessageCommand;
import java.util.List;

public final class I18nCatalogRequests {
    private I18nCatalogRequests() {
    }

    public record PlatformUpdateRequest(List<MessageRequest> messages) {
    }

    public record TenantUpdateRequest(String scopeLevel, List<MessageRequest> messages) {
    }

    public record MessageRequest(
        String i18nKey,
        String locale,
        String message,
        String status,
        Integer version,
        Boolean clear
    ) {
        I18nCatalogMessageCommand toCommand() {
            return new I18nCatalogMessageCommand(
                i18nKey,
                locale,
                message,
                status,
                version,
                Boolean.TRUE.equals(clear)
            );
        }
    }
}
