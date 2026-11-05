package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.api.*;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.StorageInventoryRepository;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageInventoryExportTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"store_staff",Set.of(),Set.of("wine_storage.view"),Set.of(store));Clock clock=Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),ZoneOffset.UTC);
 @Test void serviceUsesScopedHistoryPermissionSameClockAndServerStoreMetadata(){
  var access=mock(StorageAccess.class);var repo=mock(StorageInventoryRepository.class);var stores=mock(SharedStoreIdentityQuery.class);var workbook=mock(StorageInventoryWorkbook.class);var platform=mock(PlatformStore.class);when(platform.storeName()).thenReturn("门店");when(platform.timezone()).thenReturn("Asia/Singapore");when(stores.active(tenant,store)).thenReturn(Optional.of(platform));var page=new StorageInventoryDtos.Page(List.of(),new StorageInventoryDtos.Summary(0,0,0,0,0),0,50000,false);when(repo.export(scope,"小兰","expired",clock.instant())).thenReturn(page);
  var service=new StorageInventoryExportService(access,repo,clock,stores,workbook);service.export(scope,actor," 小兰 ","expired","en");verify(access).staff(scope,actor,"wine_storage.view",false);verify(workbook).write(page,"门店","Asia/Singapore",clock.instant(),"小兰","expired","en");
  clearInvocations(repo);assertThatThrownBy(()->service.export(scope,actor,"","invalid","en")).isInstanceOf(StorageException.class);assertThatThrownBy(()->service.export(scope,actor,"","all","invalid")).isInstanceOf(StorageException.class);verifyNoInteractions(repo);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.view",false);assertThatThrownBy(()->service.export(scope,actor,"","all","en")).isInstanceOf(StorageException.class);verifyNoInteractions(repo);
 }
 @Test void controllerReturnsXlsxAttachmentAndPreservesAuthenticationAndLimitErrors()throws Exception{
  var identity=mock(StorageIdentity.class);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);var service=mock(StorageInventoryExportService.class);byte[] bytes={80,75,3,4};when(service.export(scope,actor,"","all","zh-CN")).thenReturn(bytes);
  var mvc=standaloneSetup(new StorageInventoryExportController(identity,service)).setControllerAdvice(new StorageErrorAdvice()).build();String path="/api/v1/stores/"+store+"/wine-storage/inventory/export";
  mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andExpect(header().string("Content-Disposition","attachment; filename=\"storage-inventory-"+store+".xlsx\"")).andExpect(header().string("Cache-Control","no-store, private")).andExpect(content().bytes(bytes));
  when(service.export(scope,actor,"","all","zh-CN")).thenThrow(new StorageException("STORAGE_EXPORT_TOO_LARGE",422,"limit"));mvc.perform(get(path)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("STORAGE_EXPORT_TOO_LARGE"));
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));mvc.perform(get(path)).andExpect(status().isUnauthorized());
 }
 @Test void oversizedExportFailsBeforeLoadingDetails(){
  var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);when(jdbc.queryForObject(anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<StorageInventoryDtos.Summary>>any(),any(Object[].class))).thenReturn(new StorageInventoryDtos.Summary(2,50001,0,0,50001));
  assertThatThrownBy(()->new StorageInventoryRepository(jdbc).export(scope,"","all",clock.instant())).isInstanceOfSatisfying(StorageException.class,e->{assertThat(e.code()).isEqualTo("STORAGE_EXPORT_TOO_LARGE");assertThat(e.status()).isEqualTo(422);});
  verify(jdbc,never()).query(anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<StorageInventoryDtos.Row>>any(),any(Object[].class));
 }
}
