package com.rpb.reservation.wallet.personalpayment;

import java.time.Instant;
import static com.rpb.reservation.wallet.application.WalletViews.*;

/** Public contracts; money limits are SGD cents, charge amount is a decimal SGD string. */
public final class PersonalPaymentDtos {
 private PersonalPaymentDtos() {}
 // policyVersion is the per-customer authorization revision (legacy wire name).
 public record ConsentUpdate(Integer policyVersion,Long limitCents,Boolean accepted,String idempotencyKey) {}
 public record Consent(boolean configured,int policyVersion,Long limitCents,Long consentedLimitCents,boolean paymentEnabled,boolean requiresConsent,int ruleVersion) {}
 public record PersonalCode(String type,String code,Instant expiresAt,boolean paymentEnabled,Long limitCents,Long consentedLimitCents,int policyVersion) {}
 public record CodeStatus(String state,Bill bill) {}
 public record ScanCharge(String customerCode,String amount,String description,String idempotencyKey) {}
 public record ChargeResult(String status,Bill bill,Account account,java.util.UUID storeId) {
  public ChargeResult(String status,Bill bill,Account account){this(status,bill,account,bill==null?null:bill.storeId());}
 }
}
