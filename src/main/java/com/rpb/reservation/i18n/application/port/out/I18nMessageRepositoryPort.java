package com.rpb.reservation.i18n.application.port.out;

import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.I18nKey;
import com.rpb.reservation.i18n.domain.I18nMessage;
import java.util.List;
import java.util.Optional;

public interface I18nMessageRepositoryPort {

    Optional<I18nMessage> findMessage(StoreScope scope, I18nKey i18nKey, String locale);

    Optional<I18nMessage> findMessage(TenantScope scope, I18nKey i18nKey, String locale);

    Optional<I18nMessage> findMessage(PlatformScope scope, I18nKey i18nKey, String locale);

    List<I18nMessage> findFallbackChain(StoreScope scope, I18nKey i18nKey, String locale);

    I18nMessage save(StoreScope scope, I18nMessage message);

    I18nMessage save(TenantScope scope, I18nMessage message);
}
