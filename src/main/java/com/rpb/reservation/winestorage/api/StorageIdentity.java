package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.customerauth.api.CustomerAuthCookieService;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StorageIdentity {
 private final CurrentActorProvider actors;private final CustomerAuthApplicationService customers;private final CustomerAuthCookieService cookies;
 public StorageIdentity(CurrentActorProvider actors,CustomerAuthApplicationService customers,CustomerAuthCookieService cookies){this.actors=actors;this.customers=customers;this.cookies=cookies;}
 public CurrentActor staff(){var a=actors.currentActor().orElseThrow(()->new StorageException("STORAGE_UNAUTHENTICATED",401,"请先登录"));if(a.tenantId()==null)throw StorageException.forbidden();return a;}
 public CustomerAuthPrincipal customer(HttpServletRequest request){return cookies.readSessionToken(request).flatMap(customers::authenticateSession).orElseThrow(()->new StorageException("STORAGE_UNAUTHENTICATED",401,"请先登录顾客账号"));}
 public StoreScope scope(UUID tenant,UUID store){return new StoreScope(new TenantId(tenant),store);}
}
