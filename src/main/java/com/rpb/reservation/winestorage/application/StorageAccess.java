package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.platform.application.SharedStoreIdentityQuery;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StorageAccess {
 private final AppGateService gate; private final SharedStoreIdentityQuery stores; private final CustomerDirectoryQuery customers;
 public StorageAccess(AppGateService gate,SharedStoreIdentityQuery stores,CustomerDirectoryQuery customers){this.gate=gate;this.stores=stores;this.customers=customers;}
 public String timezone(StoreScope s){return stores.active(s.tenantId().value(),s.storeId().value()).orElseThrow(StorageException::missing).timezone();}
 public void customerId(StoreScope s,UUID id){if(id==null||customers.active(s.tenantScope(),id).isEmpty())throw StorageException.missing();}
 public boolean enabled(StoreScope s){return gate.evaluateCommercial("wine_storage",s.tenantId().value(),s.storeId().value()).allowed();}
 public boolean walletEnabled(StoreScope s){return gate.evaluateCommercial("wallet",s.tenantId().value(),s.storeId().value()).allowed();}
 /** Workspace entry only; existing record/controlled-delivery access remains independent. */
 public void customerEntry(StoreScope s,boolean ownHistory){if(enabled(s)||ownHistory||gate.isStoreProductProvisioned("wine_storage",s.tenantId().value(),s.storeId().value()))return;throw StorageException.forbidden();}
 public void staffEntry(StoreScope s,CurrentActor a){
  var request=new AppGateAccessRequest("wine_storage",a.tenantId(),s.storeId().value(),"wine_storage.view",a);
  if(gate.evaluate(request).allowed())return;var history=gate.evaluateHistory(request);if(history==null||!history.allowed())throw StorageException.forbidden();
 }
 public void customer(StoreScope s,CustomerAuthPrincipal p,boolean fresh){if(p==null||!s.tenantId().value().equals(p.tenantId()))throw StorageException.forbidden();timezone(s);customerId(s,p.customerId());if(fresh&&!enabled(s))throw StorageException.forbidden();}
 public void staff(StoreScope s,CurrentActor a,String permission,boolean fresh){
  if(a==null||!s.tenantId().value().equals(a.tenantId())||!a.canAccessStore(s.storeId().value())||!a.hasPermission(permission)||"platform_admin".equals(a.actorType()))throw StorageException.forbidden();
  timezone(s);if(fresh&&!gate.evaluate(new AppGateAccessRequest("wine_storage",a.tenantId(),s.storeId().value(),permission,a)).allowed())throw StorageException.forbidden();
 }
 public void own(UUID owner,UUID customer){if(!owner.equals(customer))throw StorageException.missing();}
}
