package com.rpb.reservation.tenantadmin.application;

import java.util.List;
import java.util.UUID;

/** Read-only distribution metadata; never contains login credentials or payment authorizations. */
public record TenantEntryLinksContext(boolean success,Identity identity,boolean publicBookingEnabled,List<Product> products) {
    public record Identity(UUID tenantId,String tenantCode,String tenantName,String tenantStatus,
                           UUID storeId,String storeCode,String storeName,String storeStatus) {}
    public record Product(String appKey,boolean available,String denialReason) {}
}
