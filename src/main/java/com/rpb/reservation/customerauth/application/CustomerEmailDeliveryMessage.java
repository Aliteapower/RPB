package com.rpb.reservation.customerauth.application;

import java.time.Instant;
import java.util.UUID;

public record CustomerEmailDeliveryMessage(
    UUID tenantId,
    UUID storeId,
    String toEmail,
    String code,
    Instant expiresAt,
    String purpose
) {
    public CustomerEmailDeliveryMessage(UUID tenantId,UUID storeId,String toEmail,String code,Instant expiresAt){
        this(tenantId,storeId,toEmail,code,expiresAt,"login");
    }
}
