package com.rpb.reservation.customer.application;
import java.time.OffsetDateTime;
import java.util.UUID;
public record CustomerPrivacyConsentRecord(UUID id,UUID customerId,UUID storeId,boolean accepted,UUID recordedBy,UUID recordedByCustomerId,String recordedByName,OffsetDateTime recordedAt,String entrySource,String confirmationMethod,String noticeVersion,String noticeTextZh,String noticeTextEn) {}
