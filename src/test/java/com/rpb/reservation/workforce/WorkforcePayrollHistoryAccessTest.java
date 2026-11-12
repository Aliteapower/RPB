package com.rpb.reservation.workforce;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.*;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import com.rpb.reservation.workforce.payroll.PayrollAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkforcePayrollHistoryAccessTest {
 UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),store=UUID.randomUUID(),sibling=UUID.randomUUID(),source=UUID.randomUUID(),company=UUID.randomUUID();
 AppGateService gate=mock(AppGateService.class);WorkforceDirectory directory=mock(WorkforceDirectory.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
 WorkforceAccess access=new WorkforceAccess(gate,directory);PayrollAccess payroll=new PayrollAccess(access,directory,jdbc);
 CurrentActor head(Set<UUID> stores){return new CurrentActor(tenant,account,"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage","workforce.admin.manage","workforce.view"),stores);}
 void setup(){when(directory.companies(tenant)).thenReturn(List.of(new Option(company,"Company")));when(directory.stores(tenant)).thenReturn(List.of(new StoreOption(store,"One",company),new StoreOption(sibling,"Two",company)));when(gate.evaluateHistory(any())).thenAnswer(i->{var r=(AppGateAccessRequest)i.getArgument(0);return AppGateDecision.allow("workforce",tenant,r.storeId(),"workforce.view");});}
 @Test void companyWideAndSnapshotSourceOwnershipAreStillRequired(){
  setup();payroll.history(head(Set.of(store,sibling,source)),store,company,List.of(source));
  verify(gate).evaluateHistory(argThat(r->r.storeId().equals(sibling)));verify(gate).evaluateHistory(argThat(r->r.storeId().equals(source)));
  assertThatThrownBy(()->payroll.history(head(Set.of(store)),store,company,List.of())).extracting("status").isEqualTo(403);
  assertThatThrownBy(()->payroll.history(head(Set.of(store,sibling)),store,company,List.of(source))).extracting("status").isEqualTo(403);
 }
 @Test void employeeManagementDoesNotGrantHistoricalSalaryScope(){
  setup();when(directory.managerStoreIds(tenant,account)).thenReturn(Set.of(store,sibling));
  var manager=new CurrentActor(tenant,account,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store,sibling));
  assertThat(access.managedStores(manager)).contains(store);
  assertThatThrownBy(()->payroll.history(manager,store,company,List.of())).extracting("status").isEqualTo(403);
 }
 @Test void unavailableHistoricalSourceCommercialOwnershipIsNotIgnored(){
  setup();doReturn(AppGateDecision.deny("workforce",tenant,source,"workforce.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED)).when(gate).evaluateHistory(argThat(r->r!=null&&r.storeId().equals(source)));
  assertThatThrownBy(()->payroll.history(head(Set.of(store,sibling,source)),store,company,List.of(source))).extracting("status").isEqualTo(403);
 }
}
