package com.rpb.reservation.i18n.api;

import com.rpb.reservation.i18n.application.I18nCatalogEntry;
import com.rpb.reservation.i18n.application.I18nCatalogKey;
import com.rpb.reservation.i18n.application.I18nCatalogLocaleView;
import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import com.rpb.reservation.i18n.application.I18nCatalogView;
import java.util.List;

public record I18nCatalogResponse(
    boolean success,
    List<String> supportedLocales,
    List<EntryResponse> entries
) {
    public static I18nCatalogResponse from(I18nCatalogView view) {
        return new I18nCatalogResponse(
            true,
            view.supportedLocales(),
            view.entries().stream().map(EntryResponse::from).toList()
        );
    }

    public record EntryResponse(
        KeyResponse key,
        List<LocaleResponse> locales
    ) {
        static EntryResponse from(I18nCatalogEntry entry) {
            return new EntryResponse(
                KeyResponse.from(entry.key()),
                entry.locales().stream().map(LocaleResponse::from).toList()
            );
        }
    }

    public record KeyResponse(
        String i18nKey,
        String namespace,
        String category,
        String displayName,
        String description,
        String textKind,
        boolean tenantEditable,
        List<String> placeholderNames,
        String status,
        int sortOrder
    ) {
        static KeyResponse from(I18nCatalogKey key) {
            return new KeyResponse(
                key.i18nKey(),
                key.namespace(),
                key.category(),
                key.displayName(),
                key.description(),
                key.textKind(),
                key.tenantEditable(),
                key.placeholderNames(),
                key.status(),
                key.sortOrder()
            );
        }
    }

    public record LocaleResponse(
        String locale,
        MessageResponse platformMessage,
        MessageResponse tenantOverride,
        MessageResponse storeOverride,
        String effectiveMessage,
        String effectiveSource
    ) {
        static LocaleResponse from(I18nCatalogLocaleView view) {
            return new LocaleResponse(
                view.locale(),
                MessageResponse.from(view.platformMessage()),
                MessageResponse.from(view.tenantOverride()),
                MessageResponse.from(view.storeOverride()),
                view.effectiveMessage(),
                view.effectiveSource()
            );
        }
    }

    public record MessageResponse(
        String message,
        String status,
        int version
    ) {
        static MessageResponse from(I18nCatalogStoredMessage message) {
            if (message == null) {
                return null;
            }
            return new MessageResponse(message.message(), message.status(), message.version());
        }
    }
}
