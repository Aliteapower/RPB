package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.common.product.ProductLineEntryRegistry;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.tenantadmin.api.*;
import org.springframework.stereotype.Component;

/** Shared customer infrastructure is independent of any single product subscription. */
@Component
public class CustomerEmailManagementAccess {
 private final AppGateService gate;
 private final PublicBookingStoreRepositoryPort stores;
 public CustomerEmailManagementAccess(AppGateService gate,PublicBookingStoreRepositoryPort stores){this.gate=gate;this.stores=stores;}
 public void require(StoreScope scope){
  if(stores.findActiveStoreProfile(scope).isEmpty())throw new TenantAdminApiException(TenantAdminApiErrorCode.STORE_SCOPE_MISMATCH);
  boolean available=ProductLineEntryRegistry.entries().stream()
   .filter(e->e.distributionEntries().stream().anyMatch(d->"customer".equals(d.audience())))
   .anyMatch(e->gate.evaluateCommercial(e.appKey(),scope.tenantId().value(),scope.storeId().value()).allowed());
  if(!available)throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN);
 }
}
