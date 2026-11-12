package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.workmode.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import static com.rpb.reservation.workforce.workmode.StoreClockSettings.*;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.any;
import java.util.*;import org.junit.jupiter.api.Test;
class StoreClockSettingsPostgresTest extends WorkforceWorkModePostgresTest {
 StoreClockSettingsService settings(){return proxy(new StoreClockSettingsService(access,new StoreClockSettingsRepository(jdbc),modes,policies,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),time));}
 @Test void addressAndSiteUseTheExistingStoreAndRetryWithoutDuplicatingAnything(){
  var service=settings();var before=service.get(admin,store);int count=jdbc.queryForObject("select count(*) from stores where tenant_id=?",Integer.class,tenant);
  var write=new Write(before.storeVersion(),before.version(),"10 Main Street",office);var saved=service.save(admin,store,write);
  assertThat(saved.storeId()).isEqualTo(store);assertThat(saved.operatingEntityId()).isEqualTo(company);assertThat(saved.storeVersion()).isEqualTo(before.storeVersion()+1);assertThat(saved.version()).isEqualTo(1);
  assertThat(service.save(admin,store,write)).isEqualTo(saved);
  var profile=new com.rpb.reservation.tenantadmin.persistence.TenantAdminProfileRepository(jdbc).find(new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(tenant),store)).orElseThrow();
  assertThat(profile.address()).isEqualTo("10 Main Street");assertThat(jdbc.queryForObject("select count(*) from stores where tenant_id=?",Integer.class,tenant)).isEqualTo(count);
  assertThat(jdbc.queryForObject("select count(*) from tenant_product_subscription_items where tenant_id=?",Integer.class,tenant)).isZero();assertThat(jdbc.queryForObject("select count(*) from workforce_clock_sites where tenant_id=? and store_id=?",Integer.class,tenant,store)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select principal_name from operating_entities where id=?",String.class,company)).isNull();assertThat(jdbc.queryForObject("select logo_media_asset_id from tenants where id=?",UUID.class,tenant)).isNull();
 }
 @Test void employeeManagementCannotChangePublicAddressWithoutTenantPermission(){
  jdbc.update("insert into workforce_administrators(tenant_id,store_id,account_id,employee_manage,version) values(?,?,?,true,1)",tenant,store,adminId);
  var limited=new CurrentActor(tenant,adminId,"staff",Set.of("store_manager"),Set.of("workforce.view","workforce.admin.manage"),Set.of(store));var service=settings();var before=service.get(limited,store);
  assertThat(before.canEditAddress()).isFalse();var saved=service.save(limited,store,new Write(before.storeVersion(),0L,"",office));
  assertThat(saved.point()).isEqualTo(office);assertThat(saved.storeVersion()).isEqualTo(before.storeVersion());
  assertThatThrownBy(()->service.save(limited,store,new Write(saved.storeVersion(),saved.version(),"Forbidden address",office))).hasMessage("WORKFORCE_FORBIDDEN");assertThat(service.get(admin,store).address()).isEmpty();
  assertThatThrownBy(()->service.get(worker,store)).hasMessage("WORKFORCE_FORBIDDEN");assertThatThrownBy(()->service.get(admin,UUID.randomUUID())).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);
 }
 @Test void staleMasterVersionAndAuditFailureCannotPartiallyMoveTheSite(){
  var service=settings();var before=service.get(admin,store);var saved=service.save(admin,store,new Write(before.storeVersion(),0L,"Original",office));
  jdbc.update("update stores set share_address='From other editor',version=version+1 where tenant_id=? and id=?",tenant,store);
  assertThatThrownBy(()->service.save(admin,store,new Write(saved.storeVersion(),saved.version(),"Lost update",new Point("Moved",1d,1d,100d)))).hasMessage("WORKFORCE_CONFLICT");
  var current=service.get(admin,store);assertThat(current.point()).isEqualTo(office);assertThat(current.address()).isEqualTo("From other editor");
  doThrow(new IllegalStateException("audit failure")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  assertThatThrownBy(()->service.save(admin,store,new Write(current.storeVersion(),current.version(),"Failed",new Point("Moved",1d,1d,100d)))).hasMessage("audit failure");assertThat(service.get(admin,store)).isEqualTo(current);
 }
 @Test void draftCalibrationReturnsMeasurementsAndNeverMovesOrWritesAClock(){
  var service=settings();var before=service.get(admin,store);clearInvocations(logs);
  var valid=new Location(0.0005,0d,5d,time.instant(),"");var checked=service.check(admin,store,new CheckWrite(0L,office,valid));
  assertThat(checked.allowed()).isTrue();assertThat(checked.distanceMeters()).isBetween(55d,56d);assertThat(checked.siteVersion()).isZero();
  var inaccurate=service.check(admin,store,new CheckWrite(0L,office,new Location(0.0005,0d,150d,time.instant(),"")));assertThat(inaccurate.code()).isEqualTo("WORKFORCE_LOCATION_ACCURACY");assertThat(inaccurate.distanceMeters()).isEqualTo(checked.distanceMeters());
  assertThat(service.check(admin,store,new CheckWrite(0L,office,new Location(0.002,0d,5d,time.instant(),""))).code()).isEqualTo("WORKFORCE_LOCATION_OUTSIDE");
  assertThat(service.check(admin,store,new CheckWrite(0L,office,new Location(0d,0d,5d,time.instant().minusSeconds(121),""))).code()).isEqualTo("WORKFORCE_LOCATION_STALE");
  assertThat(service.check(admin,store,new CheckWrite(0L,office,null)).code()).isEqualTo("WORKFORCE_LOCATION_REQUIRED");
  assertThatThrownBy(()->service.check(admin,store,new CheckWrite(0L,office,new Location(Double.NaN,0d,5d,time.instant(),"")))).hasMessage("WORKFORCE_INVALID");
  assertThat(service.get(admin,store)).isEqualTo(before);for(var table:List.of("workforce_clock_sites","workforce_clock_events","workforce_clock_locations"))assertThat(jdbc.queryForObject("select count(*) from "+table+" where tenant_id=?",Integer.class,tenant)).isZero();verifyNoInteractions(logs);
  assertThatThrownBy(()->service.check(worker,store,new CheckWrite(0L,office,valid))).hasMessage("WORKFORCE_FORBIDDEN");assertThatThrownBy(()->service.check(admin,store,new CheckWrite(1L,office,valid))).hasMessage("WORKFORCE_CONFLICT");
 }
}

