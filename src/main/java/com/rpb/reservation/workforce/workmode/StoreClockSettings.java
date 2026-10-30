package com.rpb.reservation.workforce.workmode;
import java.time.Instant;import java.util.UUID;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
/** Existing store identity plus attendance configuration; this is never a billable store creation command. */
public final class StoreClockSettings {
 private StoreClockSettings(){}
 public record Settings(UUID storeId,String storeCode,String storeName,UUID operatingEntityId,String operatingEntityName,String address,long storeVersion,Point point,long version,boolean canEditAddress){}
 public record Write(Long expectedStoreVersion,Long expectedVersion,String address,Point point){}
 public record CheckWrite(Long expectedVersion,Point point,Location location){}
 public record CheckResult(Double distanceMeters,Double radiusMeters,Double accuracyMeters,boolean allowed,String code,long siteVersion,Instant checkedAt){}
}
