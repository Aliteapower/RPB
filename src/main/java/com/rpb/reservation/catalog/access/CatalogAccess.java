package com.rpb.reservation.catalog.access;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.platform.application.SharedStoreIdentityQuery;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.catalog.domain.CatalogException;
import org.springframework.stereotype.Component;
@Component
public class CatalogAccess {
 private final CurrentActorProvider actors;private final SharedStoreIdentityQuery stores;
 public CatalogAccess(CurrentActorProvider actors,SharedStoreIdentityQuery stores){this.actors=actors;this.stores=stores;}
 public CurrentActor actor(){var a=actors.currentActor().orElseThrow(()->new CatalogException("CATALOG_UNAUTHENTICATED",401,"请先登录"));if(a.tenantId()==null||"platform_admin".equals(a.actorType()))throw new CatalogException("CATALOG_FORBIDDEN",403,"请使用商户账号");return a;}
 public void require(StoreScope s,CurrentActor a,String permission){
  if(a==null||a.tenantId()==null||!a.tenantId().equals(s.tenantId().value())||!a.canAccessStore(s.storeId().value())||!a.hasPermission(permission))throw new CatalogException("CATALOG_FORBIDDEN",403,"无产品管理权限");
  if(stores.active(s.tenantId().value(),s.storeId().value()).isEmpty())throw CatalogException.missing();
 }
 public void requireGlobal(StoreScope s,CurrentActor a,String permission){require(s,a,permission);if(!a.roles().contains("tenant_admin"))throw new CatalogException("CATALOG_FORBIDDEN",403,"共享资料与全局上下架需要租户管理员权限");}
}
