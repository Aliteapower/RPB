package com.rpb.reservation.i18n.application.port.out;

import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface I18nRuntimeMessageRepository {
    List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId, Collection<String> i18nKeys);
}
