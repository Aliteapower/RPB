package com.rpb.reservation.winestorage;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.api.*;
import com.rpb.reservation.winestorage.persistence.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageReportQueryTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);
 CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wine_storage.view"),Set.of(store));
 AppGateService gate=mock(AppGateService.class);SharedStoreIdentityQuery stores=mock(SharedStoreIdentityQuery.class);StorageReportRepository repo=mock(StorageReportRepository.class);StorageRepository records=mock(StorageRepository.class);
 Clock clock=Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"),ZoneOffset.UTC);
 StorageAccess access=new StorageAccess(gate,stores,mock(CustomerDirectoryQuery.class));StorageReportQuery query=new StorageReportQuery(access,repo,records,clock);
 @BeforeEach void setup(){var identity=mock(PlatformStore.class);when(identity.timezone()).thenReturn("Asia/Singapore");when(stores.active(tenant,store)).thenReturn(Optional.of(identity));when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,"wine_storage.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED));when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("wine_storage",tenant,store,"wine_storage.view"));}
 @Test void inclusiveDatesUseStoreTimezoneAndHistoryAccessDoesNotEnableCommands(){query.list(scope,actor,"deposit","  啤酒  ","all","2026-11-01","2026-11-02",0,20);verify(repo).list(scope,"deposit","啤酒","all",Instant.parse("2026-10-31T16:00:00Z"),Instant.parse("2026-11-02T16:00:00Z"),0,20,clock.instant());verify(gate).evaluateHistory(any());}
 @Test void rejectsForeignTenantStoreMissingPermissionAndDeniedHistory(){
  for(var denied:List.of(new CurrentActor(UUID.randomUUID(),actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of(store)),new CurrentActor(tenant,actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of()),new CurrentActor(tenant,actor.actorId(),"staff",actor.roles(),Set.of(),Set.of(store))))assertThatThrownBy(()->query.list(scope,denied,"deposit","","all","","",0,20)).isInstanceOf(StorageException.class);
  when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,"wine_storage.view",AppGateDenyReason.TENANT_APP_NOT_ENABLED));assertThatThrownBy(()->query.detail(scope,actor,"deposit",UUID.randomUUID())).extracting("status").isEqualTo(403);verifyNoInteractions(repo,records);
 }
 @Test void badDatesKindsStatusesAndBoundsAreRejectedBeforeQuery(){
  for(String[] dates:List.of(new String[]{"bad",""},new String[]{"2026-02-30",""},new String[]{"2026-11-03","2026-11-02"}))assertThatThrownBy(()->query.list(scope,actor,"deposit","","all",dates[0],dates[1],0,20)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->query.list(scope,actor,"other","","all","","",0,20)).isInstanceOf(StorageException.class);assertThatThrownBy(()->query.list(scope,actor,"deposit","","completed","","",0,20)).isInstanceOf(StorageException.class);assertThatThrownBy(()->query.list(scope,actor,"pickup","","stored","","",0,20)).isInstanceOf(StorageException.class);
  for(int page:List.of(-1,100001))assertThatThrownBy(()->query.list(scope,actor,"deposit","","all","","",page,20)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->query.list(scope,actor,"deposit","a".repeat(121),"all","","",0,20)).isInstanceOf(StorageException.class);assertThatThrownBy(()->query.list(scope,actor,"deposit","","all","","",0,101)).isInstanceOf(StorageException.class);verifyNoInteractions(repo);
 }
 @Test void detailKeepsOnlySpecifiedBillAndItsLinkedEvidence(){UUID id=UUID.randomUUID(),other=UUID.randomUUID(),customer=UUID.randomUUID();var app=new StorageDtos.Application(id,customer,"stored",null,List.of(),null,"note",clock.instant());when(records.application(scope,id,false)).thenReturn(app);when(records.itemsForApplication(scope,id,clock.instant())).thenReturn(List.of());when(records.events(scope,customer)).thenReturn(List.of(new StorageDtos.Event(UUID.randomUUID(),id,"received",actor.actorId(),Map.of(),clock.instant()),new StorageDtos.Event(UUID.randomUUID(),other,"received",actor.actorId(),Map.of(),clock.instant())));var detail=query.detail(scope,actor,"deposit",id);assertThat(detail.application()).isSameAs(app);assertThat(detail.events()).hasSize(1);verify(records,never()).items(any(),any(),any());}
 @Test void apiBindsFiltersAndReturnsForbiddenInvalidAndMissing()throws Exception{
  var identity=mock(StorageIdentity.class);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);when(repo.list(scope,"deposit","","all",null,null,0,20,clock.instant())).thenReturn(new StorageReportDtos.Page(List.of(),new StorageReportDtos.Summary(0,0,0),0,20,false));
  var mvc=standaloneSetup(new StorageReportController(identity,query)).setControllerAdvice(new StorageErrorAdvice()).build();String path="/api/v1/stores/"+store+"/wine-storage/reports";
  mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.recordCount").value(0));mvc.perform(get(path).param("from","bad")).andExpect(status().isBadRequest());
  UUID missing=UUID.randomUUID();when(records.application(scope,missing,false)).thenThrow(StorageException.missing());mvc.perform(get(path+"/deposit/"+missing)).andExpect(status().isNotFound());
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));mvc.perform(get(path)).andExpect(status().isUnauthorized());
 }
}
