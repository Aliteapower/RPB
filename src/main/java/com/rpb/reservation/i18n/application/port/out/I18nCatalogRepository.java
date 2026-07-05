package com.rpb.reservation.i18n.application.port.out;

import com.rpb.reservation.i18n.application.I18nCatalogKey;
import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import java.util.List;
import java.util.UUID;

public interface I18nCatalogRepository {
    List<I18nCatalogKey> findActiveKeys();

    List<I18nCatalogKey> findTenantEditableActiveKeys();

    List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId);

    boolean upsertMessage(
        UUID tenantId,
        UUID storeId,
        String i18nKey,
        String locale,
        String message,
        String status,
        Integer expectedVersion
    );

    boolean clearMessage(UUID tenantId, UUID storeId, String i18nKey, String locale, Integer expectedVersion);
}
