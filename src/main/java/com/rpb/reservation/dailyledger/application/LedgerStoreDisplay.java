package com.rpb.reservation.dailyledger.application;

/** Store display context; never changes frozen accounting values. */
public record LedgerStoreDisplay(String timezone,String locale,String dateFormat,String timeFormat,String currency) {}
