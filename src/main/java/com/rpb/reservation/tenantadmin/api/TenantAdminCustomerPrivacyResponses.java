package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.customer.application.CustomerPrivacyConsentRecord;
import com.rpb.reservation.customer.application.CustomerPrivacyNotice;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TenantAdminCustomerPrivacyResponses {
 private TenantAdminCustomerPrivacyResponses() {}
 public record Notice(String version,String textZh,String textEn,String labelZh,String labelEn){
  public static Notice from(CustomerPrivacyNotice n){return new Notice(n.version(),n.textZh(),n.textEn(),n.labelZh(),n.labelEn());}
 }
 public record Registration(UUID id,UUID customerId,UUID storeId,boolean accepted,UUID recordedBy,UUID recordedByCustomerId,String recordedByName,OffsetDateTime recordedAt,String entrySource,String confirmationMethod,String noticeVersion,String noticeTextZh,String noticeTextEn){
  public static Registration from(CustomerPrivacyConsentRecord r){return new Registration(r.id(),r.customerId(),r.storeId(),r.accepted(),r.recordedBy(),r.recordedByCustomerId(),r.recordedByName(),r.recordedAt(),r.entrySource(),r.confirmationMethod(),r.noticeVersion(),r.noticeTextZh(),r.noticeTextEn());}
 }
 public record History(List<Registration> records){public static History from(List<CustomerPrivacyConsentRecord> rows){return new History(rows.stream().map(Registration::from).toList());}}
}
