package com.rpb.reservation.i18n.application;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.Collection;
import java.util.Map;

public interface I18nMessageResolver {
    Map<String, String> resolve(StoreScope scope, Collection<String> i18nKeys, String locale);
}
