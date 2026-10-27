package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.platform.application.SharedStoreIdentityQuery;
import com.rpb.reservation.winestorage.persistence.StorageInventoryRepository;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StorageInventoryExportService {
 private final StorageAccess access;private final StorageInventoryRepository repository;private final Clock clock;private final SharedStoreIdentityQuery stores;private final StorageInventoryWorkbook workbook;
 public StorageInventoryExportService(StorageAccess access,StorageInventoryRepository repository,Clock clock,SharedStoreIdentityQuery stores,StorageInventoryWorkbook workbook){this.access=access;this.repository=repository;this.clock=clock;this.stores=stores;this.workbook=workbook;}
 public byte[] export(StoreScope scope,CurrentActor actor,String query,String status,String locale){
  access.staff(scope,actor,"wine_storage.view",false);String normalized=StorageInventoryQuery.filter(query,status);
  if(locale==null||!Set.of("zh-CN","en").contains(locale))throw StorageException.invalid();
  var store=stores.active(scope.tenantId().value(),scope.storeId().value()).orElseThrow(StorageException::missing);var now=clock.instant();
  return workbook.write(repository.export(scope,normalized,status,now),store.storeName(),store.timezone(),now,normalized,status,locale);
 }
}
