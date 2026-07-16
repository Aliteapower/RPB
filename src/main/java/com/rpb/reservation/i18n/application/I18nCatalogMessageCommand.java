package com.rpb.reservation.i18n.application;

public record I18nCatalogMessageCommand(
    String i18nKey,
    String locale,
    String message,
    String status,
    Integer version,
    boolean clear
) {
}
