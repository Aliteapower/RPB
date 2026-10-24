package com.rpb.reservation.appgate.application;

public record StoreProductSetting(String appKey, boolean enabled, boolean canEnable, String unavailableReason) {}
