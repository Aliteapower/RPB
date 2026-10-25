package com.rpb.reservation.winestorage.domain;

import java.time.*;

public final class StorageRules {
 private StorageRules(){}
 public static Instant expires(Instant received,String zone,int months){
  if(months<1||months>120)throw StorageException.invalid();
  return received.atZone(ZoneId.of(zone)).plusMonths(months).toInstant();
 }
 public static boolean available(int remaining,boolean opened,Instant expires,Instant now,int requested){
  return requested>0&&requested<=remaining&&(!opened||requested==1)&&now.isBefore(expires);
 }
}
